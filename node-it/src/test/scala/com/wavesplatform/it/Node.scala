package com.gicsports.it

import java.net.{InetSocketAddress, URL}

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config
import com.gicsports.account.{KeyPair, PublicKey}
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.util.GlobalTimer
import com.gicsports.settings.WavesSettings
import com.gicsports.state.diffs.FeeValidation
import com.gicsports.transaction.TransactionType
import com.gicsports.utils.LoggerFacade
import com.gicsports.wallet.Wallet
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import org.asynchttpclient._
import org.asynchttpclient.Dsl.{config => clientConfig, _}
import org.slf4j.LoggerFactory

abstract class Node(val config: Config) extends AutoCloseable {
  lazy val log: LoggerFacade =
    LoggerFacade(LoggerFactory.getLogger(s"${getClass.getCanonicalName}.${this.name}"))

  val settings: WavesSettings = WavesSettings.fromRootConfig(config)
  val client: AsyncHttpClient = asyncHttpClient(
    clientConfig()
      .setKeepAlive(false)
      .setNettyTimer(GlobalTimer.instance)
  )

  lazy val grpcChannel: ManagedChannel = ManagedChannelBuilder
    .forAddress(networkAddress.getHostString, nodeExternalPort(6870))
    .usePlaintext()
    .build()

  private[this] val wallet = Wallet(settings.walletSettings.copy(file = None))
  wallet.generateNewAccounts(1)

  def generateKeyPair(): KeyPair = wallet.synchronized {
    wallet.generateNewAccount().get
  }

  val keyPair: KeyPair     = KeyPair.fromSeed(config.getString("account-seed")).explicitGet()
  val publicKey: PublicKey = PublicKey.fromBase58String(config.getString("public-key")).explicitGet()
  val address: String      = config.getString("address")

  def nodeExternalPort(internalPort: Int): Int
  def nodeApiEndpoint: URL
  def apiKey: String

  /** An address which can be reached from the host running IT (may not match the declared address) */
  def networkAddress: InetSocketAddress

  override def close(): Unit = client.close()
}

object Node {
  implicit class NodeExt(val n: Node) extends AnyVal {
    def name: String               = n.settings.networkSettings.nodeName
    def publicKeyStr: String       = n.publicKey.toString
    def fee(txTypeId: Byte): Long  = FeeValidation.FeeConstants(TransactionType(txTypeId)) * FeeValidation.FeeUnit
    def blockDelay: FiniteDuration = n.settings.blockchainSettings.genesisSettings.averageBlockDelay
  }
}
