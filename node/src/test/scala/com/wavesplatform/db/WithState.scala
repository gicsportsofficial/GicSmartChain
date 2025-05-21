package com.gicsports.db

import java.nio.file.Files

import com.gicsports.account.{Address, KeyPair}
import com.gicsports.block.Block
import com.gicsports.common.utils.EitherExt2
import com.gicsports.database.{LevelDBFactory, LevelDBWriter, TestStorageFactory, loadActiveLeases}
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.features.BlockchainFeatures
import com.gicsports.history.Domain
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.ValidationError
import com.gicsports.lang.directives.DirectiveDictionary
import com.gicsports.lang.directives.values.*
import com.gicsports.mining.MiningConstraint
import com.gicsports.settings.{TestFunctionalitySettings as TFS, *}
import com.gicsports.state.diffs.{BlockDiffer, ENOUGH_AMT}
import com.gicsports.state.reader.CompositeBlockchain
import com.gicsports.state.utils.TestLevelDB
import com.gicsports.state.{Blockchain, BlockchainUpdaterImpl, Diff, Portfolio}
import com.gicsports.test.*
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.transaction.{Asset, Transaction, TxHelpers}
import com.gicsports.{NTPTime, TestHelpers}
import monix.reactive.Observer
import monix.reactive.subjects.{PublishSubject, Subject}
import org.iq80.leveldb.{DB, Options}
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers

trait WithState extends DBCacheSettings with Matchers with NTPTime { _: Suite =>
  protected val ignoreSpendableBalanceChanged: Subject[(Address, Asset), (Address, Asset)] = PublishSubject()
  protected val ignoreBlockchainUpdateTriggers: BlockchainUpdateTriggers                   = BlockchainUpdateTriggers.noop

  private[this] val currentDbInstance = new ThreadLocal[DB]
  protected def db: DB                = currentDbInstance.get()

  protected def tempDb[A](f: DB => A): A = {
    val path = Files.createTempDirectory("lvl-temp").toAbsolutePath
    val db   = LevelDBFactory.factory.open(path.toFile, new Options().createIfMissing(true))
    currentDbInstance.set(db)
    try {
      f(db)
    } finally {
      db.close()
      currentDbInstance.remove()
      TestHelpers.deleteRecursively(path)
    }
  }

  protected def withLevelDBWriter[A](ws: WavesSettings)(test: LevelDBWriter => A): A = tempDb { db =>
    val (_, ldb) = TestStorageFactory(
      ws,
      db,
      ntpTime,
      ignoreSpendableBalanceChanged,
      ignoreBlockchainUpdateTriggers
    )
    test(ldb)
  }

  protected def withLevelDBWriter[A](bs: BlockchainSettings)(test: LevelDBWriter => A): A =
    withLevelDBWriter(TestSettings.Default.copy(blockchainSettings = bs))(test)

  def withLevelDBWriter[A](fs: FunctionalitySettings)(test: LevelDBWriter => A): A =
    withLevelDBWriter(TestLevelDB.createTestBlockchainSettings(fs))(test)

  def assertDiffEi(preconditions: Seq[Block], block: Block, fs: FunctionalitySettings = TFS.Enabled)(
      assertion: Either[ValidationError, Diff] => Unit
  ): Unit = withLevelDBWriter(fs) { state =>
    assertDiffEi(preconditions, block, state)(assertion)
  }

  def assertDiffEi(preconditions: Seq[Block], block: Block, state: LevelDBWriter)(
      assertion: Either[ValidationError, Diff] => Unit
  ): Unit = {
    def differ(blockchain: Blockchain, b: Block) =
      BlockDiffer.fromBlock(blockchain, None, b, MiningConstraint.Unlimited, b.header.generationSignature)

    preconditions.foreach { precondition =>
      val BlockDiffer.Result(preconditionDiff, preconditionFees, totalFee, _, _) = differ(state, precondition).explicitGet()
      state.append(preconditionDiff, preconditionFees, totalFee, None, precondition.header.generationSignature, precondition)
    }

    val totalDiff1 = differ(state, block)
    assertion(totalDiff1.map(_.diff))
  }

  def assertDiffEiTraced(preconditions: Seq[Block], block: Block, fs: FunctionalitySettings = TFS.Enabled)(
      assertion: TracedResult[ValidationError, Diff] => Unit
  ): Unit = withLevelDBWriter(fs) { state =>
    def differ(blockchain: Blockchain, b: Block) =
      BlockDiffer.fromBlockTraced(blockchain, None, b, MiningConstraint.Unlimited, b.header.generationSignature, verify = true)

    preconditions.foreach { precondition =>
      val BlockDiffer.Result(preconditionDiff, preconditionFees, totalFee, _, _) = differ(state, precondition).resultE.explicitGet()
      state.append(preconditionDiff, preconditionFees, totalFee, None, precondition.header.generationSignature, precondition)
    }
    val totalDiff1 = differ(state, block)
    assertion(totalDiff1.map(_.diff))
  }

  private def assertDiffAndState(preconditions: Seq[Block], block: Block, fs: FunctionalitySettings, withNg: Boolean)(
      assertion: (Diff, Blockchain) => Unit
  ): Unit = withLevelDBWriter(fs) { state =>
    def differ(blockchain: Blockchain, prevBlock: Option[Block], b: Block): Either[ValidationError, BlockDiffer.Result] =
      BlockDiffer.fromBlock(blockchain, if (withNg) prevBlock else None, b, MiningConstraint.Unlimited, b.header.generationSignature)

    preconditions.foldLeft[Option[Block]](None) { (prevBlock, curBlock) =>
      val BlockDiffer.Result(diff, fees, totalFee, _, _) = differ(state, prevBlock, curBlock).explicitGet()
      state.append(diff, fees, totalFee, None, curBlock.header.generationSignature, curBlock)
      Some(curBlock)
    }

    val BlockDiffer.Result(diff, fees, totalFee, _, _) = differ(state, preconditions.lastOption, block).explicitGet()
    val cb                                             = CompositeBlockchain(state, diff)
    assertion(diff, cb)

    state.append(diff, fees, totalFee, None, block.header.generationSignature, block)
    assertion(diff, state)
  }

  def assertNgDiffState(preconditions: Seq[Block], block: Block, fs: FunctionalitySettings = TFS.Enabled)(
      assertion: (Diff, Blockchain) => Unit
  ): Unit =
    assertDiffAndState(preconditions, block, fs, withNg = true)(assertion)

  def assertDiffAndState(preconditions: Seq[Block], block: Block, fs: FunctionalitySettings = TFS.Enabled)(
      assertion: (Diff, Blockchain) => Unit
  ): Unit =
    assertDiffAndState(preconditions, block, fs, withNg = false)(assertion)

  def assertDiffAndState(fs: FunctionalitySettings)(test: (Seq[Transaction] => Either[ValidationError, Unit]) => Unit): Unit =
    withLevelDBWriter(fs) { state =>
      def differ(blockchain: Blockchain, b: Block) =
        BlockDiffer.fromBlock(blockchain, None, b, MiningConstraint.Unlimited, b.header.generationSignature)

      test(txs => {
        val nextHeight = state.height + 1
        val isProto    = state.activatedFeatures.get(BlockchainFeatures.BlockV5.id).exists(nextHeight > 1 && nextHeight >= _)
        val block      = TestBlock.create(txs, if (isProto) Block.ProtoBlockVersion else Block.PlainBlockVersion)
        differ(state, block).map(diff =>
          state.append(diff.diff, diff.carry, diff.totalFee, None, block.header.generationSignature.take(Block.HitSourceLength), block)
        )
      })
    }

  def assertBalanceInvariant(diff: Diff): Unit = {
    val portfolioDiff = diff.portfolios.values.fold(Portfolio())(_.combine(_).explicitGet())
    portfolioDiff.balance shouldBe 0
    portfolioDiff.effectiveBalance.explicitGet() shouldBe 0
    all(portfolioDiff.assets.values) shouldBe 0
  }

  def assertLeft(preconditions: Seq[Block], block: Block, fs: FunctionalitySettings = TFS.Enabled)(errorMessage: String): Unit =
    assertDiffEi(preconditions, block, fs)(_ should produce(errorMessage))
}

trait WithDomain extends WithState { _: Suite =>
  val DomainPresets = com.gicsports.test.DomainPresets
  import DomainPresets.*

  def domainSettingsWithFS(fs: FunctionalitySettings): WavesSettings =
    DomainPresets.domainSettingsWithFS(fs)

  def withDomain[A](
      settings: WavesSettings =
        DomainPresets.SettingsFromDefaultConfig.addFeatures(BlockchainFeatures.SmartAccounts), // SmartAccounts to allow V2 transfers by default
      balances: Seq[AddrWithBalance] = Seq.empty,
      wrapDB: DB => DB = identity
  )(test: Domain => A): A =
    withLevelDBWriter(settings) { blockchain =>
      var domain: Domain = null
      val bcu = new BlockchainUpdaterImpl(
        blockchain,
        Observer.stopped,
        settings,
        ntpTime,
        BlockchainUpdateTriggers.combined(domain.triggers),
        loadActiveLeases(db, _, _)
      )
      domain = Domain(wrapDB(db), bcu, blockchain, settings)
      val genesis = balances.map { case AddrWithBalance(address, amount) =>
        TxHelpers.genesis(address, amount)
      }
      if (genesis.nonEmpty) {
        domain.appendBlock(genesis*)
      }
      try test(domain)
      finally bcu.shutdown()
    }

  private val allVersions = DirectiveDictionary[StdLibVersion].all
  private val lastVersion = allVersions.last

  def testDomain(
      balances: Seq[AddrWithBalance] = Nil,
      from: StdLibVersion = V3,
      to: StdLibVersion = lastVersion
  )(assertion: (StdLibVersion, Domain) => Unit): Unit =
    allVersions
      .filter(v => v >= from && v <= to)
      .foreach(v => withDomain(DomainPresets.settingsForRide(v), balances)(assertion(v, _)))
}

object WithState {
  case class AddrWithBalance(address: Address, balance: Long = ENOUGH_AMT)

  object AddrWithBalance {
    def enoughBalances(accs: KeyPair*): Seq[AddrWithBalance] =
      accs.map(acc => AddrWithBalance(acc.toAddress))

    implicit def toAddrWithBalance(v: (KeyPair, Long)): AddrWithBalance = AddrWithBalance(v._1.toAddress, v._2)
  }
}
