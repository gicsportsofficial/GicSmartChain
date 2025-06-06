package com.gicsports

import java.nio.file.Files

import com.gicsports.account.Address
import com.gicsports.database.LevelDBFactory
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.transaction.Asset
import monix.reactive.subjects.{PublishSubject, Subject}
import org.iq80.leveldb.{DB, Options}
import org.scalatest.{BeforeAndAfterEach, Suite}

trait WithDB extends BeforeAndAfterEach {
  this: Suite =>

  private val path                  = Files.createTempDirectory("lvl").toAbsolutePath
  private var currentDBInstance: DB = _

  protected val ignoreSpendableBalanceChanged: Subject[(Address, Asset), (Address, Asset)] = PublishSubject()

  protected val ignoreBlockchainUpdateTriggers: BlockchainUpdateTriggers = BlockchainUpdateTriggers.noop

  def db: DB = currentDBInstance

  override def beforeEach(): Unit = {
    currentDBInstance = LevelDBFactory.factory.open(path.toFile, new Options().createIfMissing(true))
    super.beforeEach()
  }

  override def afterEach(): Unit =
    try {
      super.afterEach()
      db.close()
    } finally {
      TestHelpers.deleteRecursively(path)
    }
}
