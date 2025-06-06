package com.gicsports.history

import com.gicsports.account.Address
import com.gicsports.database.{DBExt, Keys, LevelDBWriter, loadActiveLeases}
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.mining.Miner
import com.gicsports.settings.WavesSettings
import com.gicsports.state.BlockchainUpdaterImpl
import com.gicsports.transaction.Asset
import com.gicsports.utils.{ScorexLogging, Time, UnsupportedFeature, forceStopApplication}
import monix.reactive.Observer
import org.iq80.leveldb.DB

object StorageFactory extends ScorexLogging {
  private val StorageVersion = 5

  def apply(
      settings: WavesSettings,
      db: DB,
      time: Time,
      spendableBalanceChanged: Observer[(Address, Asset)],
      blockchainUpdateTriggers: BlockchainUpdateTriggers,
      miner: Miner = _ => ()
  ): (BlockchainUpdaterImpl, LevelDBWriter & AutoCloseable) = {
    checkVersion(db)
    val levelDBWriter = LevelDBWriter(db, spendableBalanceChanged, settings)
    val bui = new BlockchainUpdaterImpl(
      levelDBWriter,
      spendableBalanceChanged,
      settings,
      time,
      blockchainUpdateTriggers,
      (minHeight, maxHeight) => loadActiveLeases(db, minHeight, maxHeight),
      miner
    )
    (bui, levelDBWriter)
  }

  private def checkVersion(db: DB): Unit = db.readWrite { rw =>
    val version = rw.get(Keys.version)
    val height  = rw.get(Keys.height)
    if (version != StorageVersion) {
      if (height == 0) {
        // The storage is empty, set current version
        rw.put(Keys.version, StorageVersion)
      } else {
        // Here we've detected that the storage is not empty and doesn't contain version
        log.error(
          s"Storage version $version is not compatible with expected version $StorageVersion! Please, rebuild node's state, use import or sync from scratch."
        )
        log.error("FOR THIS REASON THE NODE STOPPED AUTOMATICALLY")
        forceStopApplication(UnsupportedFeature)
      }
    }
  }
}
