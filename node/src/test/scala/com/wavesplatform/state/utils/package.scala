package com.gicsports.state

import com.gicsports.account.Address
import com.gicsports.api.common.AddressTransactions
import com.gicsports.common.state.ByteStr
import com.gicsports.database.{LevelDBWriter, TestStorageFactory}
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.settings.TestSettings._
import com.gicsports.settings.{BlockchainSettings, FunctionalitySettings, GenesisSettings, RewardsSettings, TestSettings}
import com.gicsports.transaction.{Asset, Transaction}
import com.gicsports.utils.SystemTime
import monix.reactive.Observer
import org.iq80.leveldb.DB

package object utils {

  def addressTransactions(
      db: DB,
      diff: => Option[(Height, Diff)],
      address: Address,
      types: Set[Transaction.Type],
      fromId: Option[ByteStr]
  ): Seq[(Height, Transaction)] =
    AddressTransactions.allAddressTransactions(db, diff, address, None, types, fromId).map { case (tm, tx) => tm.height -> tx }.toSeq

  object TestLevelDB {
    def withFunctionalitySettings(
        writableDB: DB,
        spendableBalanceChanged: Observer[(Address, Asset)],
        fs: FunctionalitySettings
    ): LevelDBWriter =
      TestStorageFactory(
        TestSettings.Default.withFunctionalitySettings(fs),
        writableDB,
        SystemTime,
        spendableBalanceChanged,
        BlockchainUpdateTriggers.noop
      )._2

    def createTestBlockchainSettings(fs: FunctionalitySettings): BlockchainSettings =
      BlockchainSettings('T', fs, GenesisSettings.TESTNET, RewardsSettings.TESTNET)
  }
}
