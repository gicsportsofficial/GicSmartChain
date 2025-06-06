package com.gicsports.mining.microblocks

import cats.syntax.applicativeError.*
import cats.syntax.bifunctor.*
import cats.syntax.either.*
import com.gicsports.account.KeyPair
import com.gicsports.block.Block.BlockId
import com.gicsports.block.{Block, MicroBlock}
import com.gicsports.metrics.*
import com.gicsports.mining.*
import com.gicsports.mining.microblocks.MicroBlockMinerImpl.*
import com.gicsports.network.{MicroBlockInv, *}
import com.gicsports.settings.MinerSettings
import com.gicsports.state.Blockchain
import com.gicsports.state.appender.MicroblockAppender
import com.gicsports.transaction.{BlockchainUpdater, Transaction}
import com.gicsports.utils.ScorexLogging
import com.gicsports.utx.UtxPool.PackStrategy
import com.gicsports.utx.UtxPoolImpl
import io.netty.channel.group.ChannelGroup
import kamon.Kamon
import monix.eval.Task
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable

import scala.concurrent.duration.*

class MicroBlockMinerImpl(
    setDebugState: MinerDebugInfo.State => Unit,
    allChannels: ChannelGroup,
    blockchainUpdater: BlockchainUpdater & Blockchain,
    utx: UtxPoolImpl,
    settings: MinerSettings,
    minerScheduler: SchedulerService,
    appenderScheduler: SchedulerService,
    transactionAdded: Observable[Unit],
    nextMicroBlockSize: Int => Int
) extends MicroBlockMiner
    with ScorexLogging {

  private val microBlockBuildTimeStats = Kamon.timer("miner.forge-microblock-time").withoutTags()

  def generateMicroBlockSequence(
      account: KeyPair,
      accumulatedBlock: Block,
      restTotalConstraint: MiningConstraint,
      lastMicroBlock: Long
  ): Task[Unit] =
    generateOneMicroBlockTask(account, accumulatedBlock, restTotalConstraint, lastMicroBlock)
      .flatMap {
        case res @ Success(newBlock, newConstraint) =>
          Task.defer(generateMicroBlockSequence(account, newBlock, newConstraint, res.nanoTime))
        case Retry =>
          Task
            .defer(generateMicroBlockSequence(account, accumulatedBlock, restTotalConstraint, lastMicroBlock))
            .delayExecution(1 second)
        case Stop =>
          setDebugState(MinerDebugInfo.MiningBlocks)
          Task(log.debug("MicroBlock mining completed, block is full"))
      }
      .recover { case e => log.error("Error mining microblock", e) }

  private[mining] def generateOneMicroBlockTask(
      account: KeyPair,
      accumulatedBlock: Block,
      restTotalConstraint: MiningConstraint,
      lastMicroBlock: Long
  ): Task[MicroBlockMiningResult] = {
    val packTask = Task.cancelable[(Option[Seq[Transaction]], MiningConstraint)] { cb =>
      @volatile var cancelled = false
      minerScheduler.execute { () =>
        val mdConstraint = MultiDimensionalMiningConstraint(
          restTotalConstraint,
          OneDimensionalMiningConstraint(
            nextMicroBlockSize(settings.maxTransactionsInMicroBlock),
            TxEstimators.one,
            "MaxTxsInMicroBlock"
          )
        )
        val packStrategy =
          if (accumulatedBlock.transactionData.isEmpty) PackStrategy.Limit(settings.microBlockInterval)
          else PackStrategy.Estimate(settings.microBlockInterval)
        log.trace(s"Starting pack for ${accumulatedBlock.id()} with $packStrategy, initial constraint is $mdConstraint")
        val (unconfirmed, updatedMdConstraint) =
          concurrent.blocking(
            Instrumented.logMeasure(log, "packing unconfirmed transactions for microblock")(
              utx.packUnconfirmed(
                mdConstraint,
                packStrategy,
                () => cancelled
              )
            )
          )
        log.trace(s"Finished pack for ${accumulatedBlock.id()}")
        val updatedTotalConstraint = updatedMdConstraint.constraints.head
        cb.onSuccess(unconfirmed -> updatedTotalConstraint)
      }
      Task.eval {
        cancelled = true
      }
    }

    packTask.flatMap {
      case (Some(unconfirmed), updatedTotalConstraint) if unconfirmed.nonEmpty =>
        val delay = {
          val delay         = System.nanoTime() - lastMicroBlock
          val requiredDelay = settings.microBlockInterval.toNanos
          if (delay >= requiredDelay) Duration.Zero else (requiredDelay - delay).nanos
        }

        for {
          _ <- Task.now(if (delay > Duration.Zero) log.trace(s"Sleeping ${delay.toMillis} ms before applying microBlock"))
          _ <- Task.sleep(delay)
          _ = log.trace(s"Generating microBlock for ${account.toAddress}, constraints: $updatedTotalConstraint")
          blocks <- forgeBlocks(account, accumulatedBlock, unconfirmed)
            .leftWiden[Throwable]
            .liftTo[Task]
          (signedBlock, microBlock) = blocks
          blockId <- appendMicroBlock(microBlock)
          _ = BlockStats.mined(microBlock, blockId)
          _       <- broadcastMicroBlock(account, microBlock, blockId)
        } yield {
          if (updatedTotalConstraint.isFull) Stop
          else Success(signedBlock, updatedTotalConstraint)
        }

      case (_, updatedTotalConstraint) =>
        if (updatedTotalConstraint.isFull) {
          log.trace(s"Stopping forging microBlocks, the block is full: $updatedTotalConstraint")
          Task.now(Stop)
        } else {
          log.trace("UTX is empty, waiting for new transactions")
          transactionAdded.headL.map(_ => Retry)
        }
    }
  }

  private def broadcastMicroBlock(account: KeyPair, microBlock: MicroBlock, blockId: BlockId): Task[Unit] =
    Task(if (allChannels != null) allChannels.broadcast(MicroBlockInv(account, blockId, microBlock.reference)))

  private def appendMicroBlock(microBlock: MicroBlock): Task[BlockId] =
    MicroblockAppender(blockchainUpdater, utx, appenderScheduler)(microBlock)
      .flatMap {
        case Left(err) => Task.raiseError(MicroBlockAppendError(microBlock, err))
        case Right(v)  => Task.now(v)
      }

  private def forgeBlocks(
      account: KeyPair,
      accumulatedBlock: Block,
      unconfirmed: Seq[Transaction]
  ): Either[MicroBlockMiningError, (Block, MicroBlock)] =
    microBlockBuildTimeStats.measureSuccessful {
      for {
        signedBlock <- Block
          .buildAndSign(
            version = blockchainUpdater.currentBlockVersion,
            timestamp = accumulatedBlock.header.timestamp,
            reference = accumulatedBlock.header.reference,
            baseTarget = accumulatedBlock.header.baseTarget,
            generationSignature = accumulatedBlock.header.generationSignature,
            txs = accumulatedBlock.transactionData ++ unconfirmed,
            signer = account,
            featureVotes = accumulatedBlock.header.featureVotes,
            rewardVote = accumulatedBlock.header.rewardVote
          )
          .leftMap(BlockBuildError)
        microBlock <- MicroBlock
          .buildAndSign(signedBlock.header.version, account, unconfirmed, accumulatedBlock.id(), signedBlock.signature)
          .leftMap(MicroBlockBuildError)
      } yield (signedBlock, microBlock)
    }
}

object MicroBlockMinerImpl {
  sealed trait MicroBlockMiningResult

  case object Stop  extends MicroBlockMiningResult
  case object Retry extends MicroBlockMiningResult
  final case class Success(b: Block, totalConstraint: MiningConstraint) extends MicroBlockMiningResult {
    val nanoTime: Long = System.nanoTime()
  }
}
