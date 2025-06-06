package com.gicsports.network

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, RemovalNotification}
import com.google.common.collect.EvictingQueue
import com.gicsports.settings.NetworkSettings
import com.gicsports.utils.{JsonFileStorage, ScorexLogging}
import io.netty.channel.Channel
import io.netty.channel.socket.nio.NioSocketChannel

import scala.jdk.CollectionConverters._
import scala.collection._
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import scala.util.control.NonFatal

class PeerDatabaseImpl(settings: NetworkSettings) extends PeerDatabase with ScorexLogging {

  private type PeerRemoved[T]         = RemovalNotification[T, java.lang.Long]
  private type PeerRemovalListener[T] = PeerRemoved[T] => Unit

  private def cache[T <: AnyRef](timeout: FiniteDuration, removalListener: Option[PeerRemovalListener[T]] = None) =
    removalListener.fold {
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(timeout.toMillis, TimeUnit.MILLISECONDS)
        .build[T, java.lang.Long]()
    } { listener =>
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(timeout.toMillis, TimeUnit.MILLISECONDS)
        .removalListener(listener(_))
        .build[T, java.lang.Long]()
    }

  private type PeersPersistenceType = Set[String]
  private val peersPersistence = cache[InetSocketAddress](settings.peersDataResidenceTime, Some(nonExpiringKnownPeers))
  private val blacklist        = cache[InetAddress](settings.blackListResidenceTime)
  private val suspension       = cache[InetAddress](settings.suspensionResidenceTime)
  private val reasons          = mutable.Map.empty[InetAddress, String]
  private val unverifiedPeers  = EvictingQueue.create[InetSocketAddress](settings.maxUnverifiedPeers)

  private val knownPeersAddresses = settings.knownPeers.map(inetSocketAddress(_, 6863))

  private def nonExpiringKnownPeers(n: PeerRemoved[InetSocketAddress]): Unit =
    if (n.wasEvicted() && knownPeersAddresses.contains(n.getKey))
      peersPersistence.put(n.getKey, n.getValue)

  for (a <- knownPeersAddresses) {
    // add peers from config with max timestamp so they never get evicted from the list of known peers
    doTouch(a, Long.MaxValue)
  }

  for (f <- settings.file if f.exists()) try {
    JsonFileStorage.load[PeersPersistenceType](f.getCanonicalPath).foreach(a => touch(inetSocketAddress(a, 6863)))
    log.info(s"Loaded ${peersPersistence.size} known peer(s) from ${f.getName}")
  } catch {
    case NonFatal(_) => log.info("Legacy or corrupted peers.dat, ignoring, starting all over from known-peers...")
  }

  override def addCandidate(socketAddress: InetSocketAddress): Boolean = unverifiedPeers.synchronized {
    val r = !socketAddress.getAddress.isAnyLocalAddress &&
      !(socketAddress.getAddress.isLoopbackAddress && socketAddress.getPort == settings.bindAddress.getPort) &&
      Option(peersPersistence.getIfPresent(socketAddress)).isEmpty &&
      !unverifiedPeers.contains(socketAddress)
    if (r) unverifiedPeers.add(socketAddress)
    r
  }

  private def doTouch(socketAddress: InetSocketAddress, timestamp: Long): Unit = unverifiedPeers.synchronized {
    unverifiedPeers.removeIf(_ == socketAddress)
    peersPersistence.put(socketAddress, Option(peersPersistence.getIfPresent(socketAddress)).fold(timestamp)(_.toLong.max(timestamp)))
  }

  override def touch(socketAddress: InetSocketAddress): Unit = doTouch(socketAddress, System.currentTimeMillis())

  override def blacklist(inetAddress: InetAddress, reason: String): Unit =
    if (settings.enableBlacklisting) {
      unverifiedPeers.synchronized {
        unverifiedPeers.removeIf { x =>
          Option(x.getAddress).contains(inetAddress.getAddress)
        }
        blacklist.put(inetAddress, System.currentTimeMillis())
        reasons.put(inetAddress, reason)
      }
    }

  override def suspend(socketAddress: InetSocketAddress): Unit = getAddress(socketAddress).foreach { address =>
    unverifiedPeers.synchronized {
      unverifiedPeers.removeIf { x =>
        Option(x.getAddress).contains(address)
      }
      suspension.put(address, System.currentTimeMillis())
    }
  }

  override def knownPeers: immutable.Map[InetSocketAddress, Long] = {
    peersPersistence.cleanUp() // run all deferred actions (expiration/listeners/etc)
    peersPersistence
      .asMap()
      .asScala
      .collect {
        case (addr, ts) if !(settings.enableBlacklisting && blacklistedHosts.contains(addr.getAddress)) => addr -> ts.toLong
      }
      .toMap
  }

  override def blacklistedHosts: immutable.Set[InetAddress] = blacklist.asMap().asScala.keys.toSet

  override def suspendedHosts: immutable.Set[InetAddress] = suspension.asMap().asScala.keys.toSet

  override def detailedBlacklist: immutable.Map[InetAddress, (Long, String)] =
    blacklist.asMap().asScala.view.mapValues(_.toLong).map { case ((h, t)) => h -> ((t, Option(reasons(h)).getOrElse(""))) }.toMap

  override def detailedSuspended: immutable.Map[InetAddress, Long] = suspension.asMap().asScala.view.mapValues(_.toLong).toMap

  override def randomPeer(excluded: immutable.Set[InetSocketAddress]): Option[InetSocketAddress] = unverifiedPeers.synchronized {
    def excludeAddress(isa: InetSocketAddress): Boolean = {
      excluded(isa) || Option(isa.getAddress).exists(blacklistedHosts) || suspendedHosts(isa.getAddress)
    }
    // excluded only contains local addresses, our declared address, and external declared addresses we already have
    // connection to, so it's safe to filter out all matching candidates
    unverifiedPeers.removeIf(excluded(_))
    val unverified = Option(unverifiedPeers.peek()).filterNot(excludeAddress)
    val verified   = Random.shuffle(knownPeers.keySet.diff(excluded).toSeq).headOption.filterNot(excludeAddress)

    (unverified, verified) match {
      case (Some(_), v @ Some(_)) => if (Random.nextBoolean()) Some(unverifiedPeers.poll()) else v
      case (Some(_), None)        => Some(unverifiedPeers.poll())
      case (None, v @ Some(_))    => v
      case _                      => None
    }
  }

  def clearBlacklist(): Unit = {
    blacklist.invalidateAll()
    reasons.clear()
  }

  override def close(): Unit = settings.file.foreach { f =>
    log.info(s"Saving ${knownPeers.size} known peer(s) to ${f.getName}")
    val rawPeers = for {
      inetAddress <- knownPeers.keySet
      address     <- Option(inetAddress.getAddress)
    } yield s"${address.getHostAddress}:${inetAddress.getPort}"

    JsonFileStorage.save[PeersPersistenceType](rawPeers, f.getCanonicalPath)
  }

  override def blacklistAndClose(channel: Channel, reason: String): Unit = getRemoteAddress(channel).foreach { x =>
    log.debug(s"Blacklisting ${id(channel)}: $reason")
    blacklist(x.getAddress, reason)
    channel.close()
  }

  override def suspendAndClose(channel: Channel): Unit = getRemoteAddress(channel).foreach { x =>
    log.debug(s"Suspending ${id(channel)}")
    suspend(x)
    channel.close()
  }

  private def getAddress(socketAddress: InetSocketAddress): Option[InetAddress] = {
    val r = Option(socketAddress.getAddress)
    if (r.isEmpty) log.debug(s"Can't obtain an address from $socketAddress")
    r
  }

  private def getRemoteAddress(channel: Channel): Option[InetSocketAddress] = channel match {
    case x: NioSocketChannel => Option(x.remoteAddress())
    case x =>
      log.debug(s"Doesn't know how to get a remoteAddress from $x")
      None
  }
}
