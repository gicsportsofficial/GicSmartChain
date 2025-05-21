package com.gicsports.test

import java.nio.file.Files

import com.gicsports.{NTPTime, TestHelpers}
import com.gicsports.database.{LevelDBFactory, TestStorageFactory}
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.history.Domain
import com.gicsports.settings.WavesSettings
import com.gicsports.transaction.TxHelpers
import monix.reactive.Observer
import org.iq80.leveldb.{DB, Options}
import org.scalatest.{BeforeAndAfterAll, Suite}

trait SharedDomain extends BeforeAndAfterAll with NTPTime { _: Suite =>
  private val path = Files.createTempDirectory("lvl-temp").toAbsolutePath
  private val db: DB   = LevelDBFactory.factory.open(path.toFile, new Options().createIfMissing(true))
  private val (bui, ldb) = TestStorageFactory(settings, db, ntpTime, Observer.stopped, BlockchainUpdateTriggers.noop)

  def settings: WavesSettings = DomainPresets.ScriptsAndSponsorship
  def genesisBalances: Seq[AddrWithBalance] = Seq.empty

  lazy val domain: Domain = Domain(db, bui, ldb, settings)


  override protected def beforeAll(): Unit = {
    val genesisTransactions = genesisBalances.map(ab => TxHelpers.genesis(ab.address, ab.balance))
    if (genesisTransactions.nonEmpty) {
      domain.appendBlock(genesisTransactions: _*)
    }
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    db.close()
    bui.shutdown()
    TestHelpers.deleteRecursively(path)
  }
}
