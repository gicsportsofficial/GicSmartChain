package com.gicsports.database

import com.google.common.hash.{Funnels, BloomFilter => GBloomFilter}
import com.gicsports.account.Address
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.settings.WavesSettings
import com.gicsports.state.BlockchainUpdaterImpl
import com.gicsports.transaction.Asset
import com.gicsports.utils.Time
import monix.reactive.Observer
import org.iq80.leveldb.DB

object TestStorageFactory {
  private def wrappedFilter(use: Boolean): BloomFilter =
    if (use) new Wrapper(GBloomFilter.create(Funnels.byteArrayFunnel(), 1000L)) else BloomFilter.AlwaysEmpty

  def apply(
      settings: WavesSettings,
      db: DB,
      time: Time,
      spendableBalanceChanged: Observer[(Address, Asset)],
      blockchainUpdateTriggers: BlockchainUpdateTriggers
  ): (BlockchainUpdaterImpl, LevelDBWriter) = {
    val useBloomFilter = settings.dbSettings.useBloomFilter
    val levelDBWriter: LevelDBWriter = new LevelDBWriter(db, spendableBalanceChanged, settings.blockchainSettings, settings.dbSettings) {
      override val orderFilter: BloomFilter        = wrappedFilter(useBloomFilter)
      override val dataKeyFilter: BloomFilter      = wrappedFilter(useBloomFilter)
      override val wavesBalanceFilter: BloomFilter = wrappedFilter(useBloomFilter)
      override val assetBalanceFilter: BloomFilter = wrappedFilter(useBloomFilter)
    }
    (
      new BlockchainUpdaterImpl(levelDBWriter, spendableBalanceChanged, settings, time, blockchainUpdateTriggers, loadActiveLeases(db, _, _)),
      levelDBWriter
    )
  }
}
