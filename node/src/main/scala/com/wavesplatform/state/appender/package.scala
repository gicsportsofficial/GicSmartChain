package com.gicsports.state
import cats.syntax.either.*
import com.gicsports.account.AddressScheme
import com.gicsports.block.Block
import com.gicsports.block.Block.BlockId
import com.gicsports.common.state.ByteStr
import com.gicsports.consensus.PoSSelector
import com.gicsports.lang.ValidationError
import com.gicsports.metrics.*
import com.gicsports.mining.Miner
import com.gicsports.transaction.*
import com.gicsports.transaction.TxValidationError.{BlockAppendError, BlockFromFuture, GenericError}
import com.gicsports.utils.Time
import com.gicsports.utx.UtxPoolImpl
import kamon.Kamon

package object appender {
  val MaxTimeDrift: Long = 100 // millis
  private val scheme = AddressScheme.current
  val wrongBLocksUntil = 950000
  val wrongNetworkChainId = 76

  // Invalid blocks, that are already in blockchain
  private val exceptions = List(
  )

  private[appender] def appendKeyBlock(
      blockchainUpdater: BlockchainUpdater & Blockchain,
      utx: UtxPoolImpl,
      pos: PoSSelector,
      time: Time,
      verify: Boolean
  )(block: Block): Either[ValidationError, Option[Int]] =
    for {
      hitSource <- if (verify) validateBlock(blockchainUpdater, pos, time)(block) else pos.validateGenerationSignature(block)
      newHeight <- utx.priorityPool.lockedWrite {
        metrics.appendBlock
          .measureSuccessful(blockchainUpdater.processBlock(block, hitSource, verify))
          .map { discardedDiffs =>
            utx.removeAll(block.transactionData)
            utx.setPriorityDiffs(discardedDiffs)
            utx.scheduleCleanup()
            Some(blockchainUpdater.height)
          }
      }
    } yield newHeight

  private[appender] def appendExtensionBlock(
      blockchainUpdater: BlockchainUpdater & Blockchain,
      pos: PoSSelector,
      time: Time,
      verify: Boolean
  )(block: Block): Either[ValidationError, Option[Int]] =
    for {
      hitSource <- if (verify) validateBlock(blockchainUpdater, pos, time)(block) else pos.validateGenerationSignature(block)
      _         <- metrics.appendBlock.measureSuccessful(blockchainUpdater.processBlock(block, hitSource, verify))
    } yield Some(blockchainUpdater.height)

  private def validateBlock(blockchainUpdater: Blockchain, pos: PoSSelector, time: Time)(block: Block) =
    for {
      _ <- Miner.isAllowedForMining(block.sender.toAddress, blockchainUpdater).leftMap(BlockAppendError(_, block))
      hitSource <- blockConsensusValidation(blockchainUpdater, pos, time.correctedTime(), block) { (height, parent) =>
        val balance = blockchainUpdater.generatingBalance(block.sender.toAddress, Some(parent))
        Either.cond(
          blockchainUpdater.isEffectiveBalanceValid(height, block, balance),
          balance,
          s"generator's effective balance $balance is less that required for generation"
        )
      }
    } yield hitSource

  private def blockConsensusValidation(blockchain: Blockchain, pos: PoSSelector, currentTs: Long, block: Block)(
      genBalance: (Int, BlockId) => Either[String, Long]
  ): Either[ValidationError, ByteStr] =
    metrics.blockConsensusValidation
      .measureSuccessful {

        val blockTime = block.header.timestamp

        for {
          height <- blockchain
            .heightOf(block.header.reference)
            .toRight(GenericError(s"height: history does not contain parent ${block.header.reference}"))
          parent <- blockchain.parentHeader(block.header).toRight(GenericError(s"parent: history does not contain parent ${block.header.reference}"))
          grandParent = blockchain.parentHeader(parent, 2)
          effectiveBalance <- genBalance(height, block.header.reference).left.map(GenericError(_))
          _                <- validateBlockVersion(height, block, blockchain)
          _                <- Either.cond(blockTime - currentTs < MaxTimeDrift, (), BlockFromFuture(blockTime))
          _                <- pos.validateBaseTarget(height, block, parent, grandParent)
          hitSource        <- pos.validateGenerationSignature(block)
          _                <- pos.validateBlockDelay(height, block.header, parent, effectiveBalance).orElse(checkExceptions(height, block))
        } yield hitSource
      }
      .left
      .map {
        case GenericError(x) => GenericError(s"Block $block is invalid: $x")
        case x => x
      }

  private def checkExceptions(height: Int, block: Block): Either[ValidationError, Unit] = {
    Either
      .cond(
        exceptions.contains((height, block.id)) || (height < wrongBLocksUntil && scheme.chainId == wrongNetworkChainId),
        (),
        GenericError(s"Block time ${block.header.timestamp} less than expected")
      )
  }

  private def validateBlockVersion(parentHeight: Int, block: Block, blockchain: Blockchain): Either[ValidationError, Unit] = {
    Either.cond(
      blockchain.blockVersionAt(parentHeight + 1) == block.header.version,
      (),
      GenericError(s"Block version should be equal to ${blockchain.blockVersionAt(parentHeight + 1)}")
    )
  }

  private[this] object metrics {
    val blockConsensusValidation = Kamon.timer("block-appender.block-consensus-validation").withoutTags()
    val appendBlock              = Kamon.timer("block-appender.blockchain-append-block").withoutTags()
  }

}
