package com.gicsports.state.appender

import cats.data.EitherT
import com.gicsports.block.Block.BlockId
import com.gicsports.block.MicroBlock
import com.gicsports.lang.ValidationError
import com.gicsports.metrics.*
import com.gicsports.network.*
import com.gicsports.network.MicroBlockSynchronizer.MicroblockData
import com.gicsports.state.Blockchain
import com.gicsports.transaction.BlockchainUpdater
import com.gicsports.transaction.TxValidationError.InvalidSignature
import com.gicsports.utils.ScorexLogging
import com.gicsports.utx.UtxPoolImpl
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import kamon.Kamon
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.{Left, Right}

object MicroblockAppender extends ScorexLogging {
  private val microblockProcessingTimeStats = Kamon.timer("microblock-appender.processing-time").withoutTags()

  def apply(blockchainUpdater: BlockchainUpdater & Blockchain, utxStorage: UtxPoolImpl, scheduler: Scheduler, verify: Boolean = true)(
      microBlock: MicroBlock
  ): Task[Either[ValidationError, BlockId]] =
    Task(microblockProcessingTimeStats.measureSuccessful {
      blockchainUpdater
        .processMicroBlock(microBlock, verify)
        .map { totalBlockId =>
          if (microBlock.transactionData.nonEmpty) {
            utxStorage.removeAll(microBlock.transactionData)
            log.trace(
              s"Removing txs of ${microBlock.stringRepr(totalBlockId)} ${microBlock.transactionData.map(_.id()).mkString("(", ", ", ")")} from UTX pool"
            )
          }

          utxStorage.scheduleCleanup()
          totalBlockId
        }
    }).executeOn(scheduler)

  def apply(
      blockchainUpdater: BlockchainUpdater & Blockchain,
      utxStorage: UtxPoolImpl,
      allChannels: ChannelGroup,
      peerDatabase: PeerDatabase,
      scheduler: Scheduler
  )(ch: Channel, md: MicroblockData): Task[Unit] = {
    import md.microBlock
    val microblockTotalResBlockSig = microBlock.totalResBlockSig
    (for {
      _       <- EitherT(Task.now(microBlock.signaturesValid()))
      blockId <- EitherT(apply(blockchainUpdater, utxStorage, scheduler)(microBlock))
    } yield blockId).value.map {
      case Right(blockId) =>
        md.invOpt match {
          case Some(mi) => allChannels.broadcast(mi, except = md.microblockOwners())
          case None     => log.warn(s"${id(ch)} Not broadcasting MicroBlockInv")
        }
        BlockStats.applied(microBlock, blockId)
      case Left(is: InvalidSignature) =>
        val idOpt = md.invOpt.map(_.totalBlockId)
        peerDatabase.blacklistAndClose(ch, s"Could not append microblock ${idOpt.getOrElse(s"(sig=$microblockTotalResBlockSig)")}: $is")
      case Left(ve) =>
        md.invOpt.foreach(mi => BlockStats.declined(mi.totalBlockId))
        val idOpt = md.invOpt.map(_.totalBlockId)
        log.debug(s"${id(ch)} Could not append microblock ${idOpt.getOrElse(s"(sig=$microblockTotalResBlockSig)")}: $ve")
    }
  }
}
