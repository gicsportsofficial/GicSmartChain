package com

import java.time.Instant

import com.typesafe.scalalogging.Logger
import com.gicsports.block.Block
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.ValidationError
import com.gicsports.mining.Miner
import com.gicsports.settings.WavesSettings
import com.gicsports.state.Blockchain
import com.gicsports.transaction.BlockchainUpdater
import com.gicsports.transaction.TxValidationError.GenericError
import org.slf4j.LoggerFactory

package object gicsports {
  private lazy val logger: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName))
  private def checkOrAppend(block: Block, blockchainUpdater: Blockchain & BlockchainUpdater, miner: Miner): Either[ValidationError, Unit] =
    if (blockchainUpdater.isEmpty) {
      blockchainUpdater.processBlock(block, block.header.generationSignature).map { _ =>
        val genesisHeader = blockchainUpdater.blockHeader(1).get
        logger.info(
          s"Genesis block ${genesisHeader.id()} (generated at ${Instant.ofEpochMilli(genesisHeader.header.timestamp)}) has been added to the state"
        )
      }
    } else
      blockchainUpdater.blockHeader(1).map(_.id()) match {
        case Some(id) if id == block.id() =>
          miner.scheduleMining()
          Right(())
        case _ =>
          Left(GenericError("Mismatched genesis blocks in configuration and blockchain"))
      }

  def checkGenesis(settings: WavesSettings, blockchainUpdater: Blockchain & BlockchainUpdater, miner: Miner): Unit = {
    Block
      .genesis(settings.blockchainSettings.genesisSettings, blockchainUpdater.isFeatureActivated(BlockchainFeatures.RideV6))
      .flatMap { genesis =>
        logger.trace(s"Genesis block json: ${genesis.json()}")
        checkOrAppend(genesis, blockchainUpdater, miner)
      }
      .left
      .foreach { e =>
        logger.error("INCORRECT NODE CONFIGURATION!!! NODE STOPPED BECAUSE OF THE FOLLOWING ERROR:")
        logger.error(e.toString)
        com.gicsports.utils.forceStopApplication()
      }
  }
}
