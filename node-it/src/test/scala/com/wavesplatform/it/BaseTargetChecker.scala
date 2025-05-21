package com.gicsports.it

import com.typesafe.config.ConfigFactory.{defaultApplication, defaultReference}
import com.gicsports.account.KeyPair
import com.gicsports.block.Block
import com.gicsports.common.utils.EitherExt2
import com.gicsports.consensus.PoSSelector
import com.gicsports.database.openDB
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.features.BlockchainFeatures
import com.gicsports.history.StorageFactory
import com.gicsports.settings._
import com.gicsports.transaction.Asset.Waves
import com.gicsports.utils.NTP
import monix.execution.UncaughtExceptionReporter
import monix.reactive.Observer
import net.ceedubs.ficus.Ficus._

object BaseTargetChecker {
  def main(args: Array[String]): Unit = {
    implicit val reporter: UncaughtExceptionReporter = UncaughtExceptionReporter.default
    val sharedConfig = Docker.genesisOverride()
      .withFallback(Docker.configTemplate)
      .withFallback(defaultApplication())
      .withFallback(defaultReference())
      .resolve()

    val settings               = WavesSettings.fromRootConfig(sharedConfig)
    val db                     = openDB("/tmp/tmp-db")
    val ntpTime                = new NTP("ntp.pool.org")
    val (blockchainUpdater, _) = StorageFactory(settings, db, ntpTime, Observer.empty, BlockchainUpdateTriggers.noop)
    val poSSelector            = PoSSelector(blockchainUpdater, settings.synchronizationSettings.maxBaseTarget)

    try {
      val genesisBlock = Block.genesis(settings.blockchainSettings.genesisSettings, blockchainUpdater.isFeatureActivated(BlockchainFeatures.RideV6)).explicitGet()
      blockchainUpdater.processBlock(genesisBlock, genesisBlock.header.generationSignature)

      NodeConfigs.Default.map(_.withFallback(sharedConfig)).collect {
        case cfg if cfg.as[Boolean]("GIC.miner.enable") =>
          val account = KeyPair.fromSeed(cfg.getString("account-seed")).explicitGet()
          val address = account.toAddress
          val balance = blockchainUpdater.balance(address, Waves)
          val timeDelay = poSSelector
            .getValidBlockDelay(blockchainUpdater.height, account, genesisBlock.header.baseTarget, balance)
            .explicitGet()

          f"$address: ${timeDelay * 1e-3}%10.3f s"
      }
    } finally ntpTime.close()
  }
}
