package com.gicsports.state

import com.google.common.primitives.Longs
import com.typesafe.config.ConfigFactory
import com.gicsports.TestHelpers.enableNG
import com.gicsports.account.{Address, KeyPair}
import com.gicsports.block.Block
import com.gicsports.block.Block.PlainBlockVersion
import com.gicsports.common.utils.EitherExt2
import com.gicsports.database.loadActiveLeases
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.db.{DBCacheSettings, WithDomain}
import com.gicsports.events.BlockchainUpdateTriggers
import com.gicsports.history.Domain.BlockchainUpdaterExt
import com.gicsports.history.{Domain, chainBaseAndMicro, randomSig}
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.settings.{WavesSettings, loadConfig}
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.test.*
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.TxHelpers.*
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.smart.script.ScriptCompiler
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.utils.Signed
import com.gicsports.transaction.{Asset, Transaction, TxHelpers, TxVersion}
import com.gicsports.utils.Time
import com.gicsports.{EitherMatchers, NTPTime}
import monix.execution.Scheduler.Implicits.global
import monix.reactive.subjects.PublishToOneSubject
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

class BlockchainUpdaterImplSpec extends FreeSpec with EitherMatchers with WithDomain with NTPTime with DBCacheSettings with MockFactory {
  import DomainPresets.*

  private val FEE_AMT = 1000000L

  // default settings, no NG
  private lazy val wavesSettings = WavesSettings.fromRootConfig(loadConfig(ConfigFactory.load()))

  def baseTest(setup: Time => (KeyPair, Seq[Block]), enableNg: Boolean = false, triggers: BlockchainUpdateTriggers = BlockchainUpdateTriggers.noop)(
      f: (BlockchainUpdaterImpl, KeyPair) => Unit
  ): Unit = withDomain(if (enableNg) enableNG(wavesSettings) else wavesSettings) { d =>
    d.triggers = d.triggers :+ triggers

    val (account, blocks) = setup(ntpTime)

    blocks.foreach { block =>
      d.appendBlock(block)
    }

    f(d.blockchainUpdater, account)
  }

  def createTransfer(master: KeyPair, recipient: Address, ts: Long): TransferTransaction =
    TxHelpers.transfer(master, recipient, ENOUGH_AMT / 5, fee = 1000000, timestamp = ts, version = TxVersion.V1)

  def commonPreconditions(ts: Long): (KeyPair, List[Block]) = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis      = TxHelpers.genesis(master.toAddress, timestamp = ts)
    val genesisBlock = TestBlock.create(ts, Seq(genesis))
    val b1 = TestBlock
      .create(
        ts + 10,
        genesisBlock.id(),
        Seq(
          createTransfer(master, recipient.toAddress, ts + 1),
          createTransfer(master, recipient.toAddress, ts + 2),
          createTransfer(recipient, master.toAddress, ts + 3),
          createTransfer(master, recipient.toAddress, ts + 4),
          createTransfer(master, recipient.toAddress, ts + 5)
        )
      )
    val b2 = TestBlock.create(
      ts + 20,
      b1.id(),
      Seq(
        createTransfer(master, recipient.toAddress, ts + 11),
        createTransfer(recipient, master.toAddress, ts + 12),
        createTransfer(recipient, master.toAddress, ts + 13),
        createTransfer(recipient, master.toAddress, ts + 14)
      )
    )

    (master, List(genesisBlock, b1, b2))
  }

  "blochain update events sending" - {
    "without NG" - {
      "genesis block and two transfers blocks" in {
        val triggersMock = mock[BlockchainUpdateTriggers]

        inSequence {
          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 0 &&
              block.transactionData.length == 1 &&
              diff.parentDiff.portfolios.head._2.balance == 0 &&
              diff.transactionDiffs.head.portfolios.head._2.balance == ENOUGH_AMT
            })
            .once()

          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              val txDiff = diff.transactionDiffs.head
              val tx     = txDiff.transactions.head.transaction.asInstanceOf[TransferTransaction]

              bc.height == 1 &&
              block.transactionData.length == 5 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 5 &&
              // first Tx updated balances
              txDiff.portfolios(tx.recipient.asInstanceOf[Address]).balance == (ENOUGH_AMT / 5) &&
              txDiff.portfolios(tx.sender.toAddress).balance == (-ENOUGH_AMT / 5 - FEE_AMT)
            })
            .once()

          (triggersMock.onProcessBlock _).expects(*, *, *, *).once()
        }

        baseTest(time => commonPreconditions(time.correctedTime()), enableNg = false, triggersMock)((_, _) => ())
      }
    }

    "with NG" - {
      "genesis block and two transfers blocks" in {
        val triggersMock = mock[BlockchainUpdateTriggers]

        inSequence {
          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 0 &&
              block.transactionData.length == 1 &&
              diff.parentDiff.portfolios.head._2.balance == 0 &&
              diff.transactionDiffs.head.portfolios.head._2.balance == ENOUGH_AMT
            })
            .once()

          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 1 &&
              block.transactionData.length == 5 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 5 * 0.4
            })
            .once()

          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 2 &&
              block.transactionData.length == 4 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == (
                FEE_AMT * 5 * 0.6     // carry from prev block
                  + FEE_AMT * 4 * 0.4 // current block reward
              )
            })
            .once()
        }

        baseTest(time => commonPreconditions(time.correctedTime()), enableNg = true, triggersMock)((_, _) => ())
      }

      "block, then 2 microblocks, then block referencing previous microblock" in withDomain(enableNG(wavesSettings)) { d =>
        def preconditions(ts: Long): (Transaction, Seq[Transaction]) = {
          val master    = TxHelpers.signer(1)
          val recipient = TxHelpers.signer(2)

          val genesis = TxHelpers.genesis(master.toAddress, timestamp = ts)
          val transfers = Seq(
            createTransfer(master, recipient.toAddress, ts + 1),
            createTransfer(master, recipient.toAddress, ts + 2),
            createTransfer(master, recipient.toAddress, ts + 3),
            createTransfer(recipient, master.toAddress, ts + 4),
            createTransfer(master, recipient.toAddress, ts + 5)
          )

          (genesis, transfers)
        }

        val triggersMock = mock[BlockchainUpdateTriggers]

        d.triggers = d.triggers :+ triggersMock

        val (genesis, transfers)       = preconditions(0)
        val (block1, microBlocks1And2) = chainBaseAndMicro(randomSig, genesis, Seq(transfers.take(2), Seq(transfers(2))))
        val (block2, microBlock3)      = chainBaseAndMicro(microBlocks1And2.head.totalResBlockSig, transfers(3), Seq(Seq(transfers(4))))

        inSequence {
          // genesis
          (triggersMock.onProcessBlock _)
            .expects(where { (block, diff, _, bc) =>
              bc.height == 0 &&
              block.transactionData.length == 1 &&
              diff.parentDiff.portfolios.head._2.balance == 0 &&
              diff.transactionDiffs.head.portfolios.head._2.balance == ENOUGH_AMT
            })
            .once()

          // microblock 1
          (triggersMock.onProcessMicroBlock _)
            .expects(where { (microBlock, diff, bc, _, _) =>
              bc.height == 1 &&
              microBlock.transactionData.length == 2 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 2 * 0.4
            })
            .once()

          // microblock 2
          (triggersMock.onProcessMicroBlock _)
            .expects(where { (microBlock, diff, bc, _, _) =>
              bc.height == 1 &&
              microBlock.transactionData.length == 1 &&
              // miner reward, no NG — all txs fees
              diff.parentDiff.portfolios.size == 1 &&
              diff.parentDiff.portfolios.head._2.balance == FEE_AMT * 0.4
            })
            .once()

          // rollback microblock
          (triggersMock.onMicroBlockRollback _)
            .expects(where { (_, toSig) =>
              toSig == microBlocks1And2.head.totalResBlockSig
            })
            .once()

          // next keyblock
          (triggersMock.onProcessBlock _)
            .expects(where { (block, _, _, bc) =>
              bc.height == 1 &&
              block.header.reference == microBlocks1And2.head.totalResBlockSig
            })
            .once()

          // microblock 3
          (triggersMock.onProcessMicroBlock _)
            .expects(where { (microBlock, _, bc, _, _) =>
              bc.height == 2 && microBlock.reference == block2.signature
            })
            .once()
        }

        d.blockchainUpdater.processBlock(block1) should beRight
        d.blockchainUpdater.processMicroBlock(microBlocks1And2.head) should beRight
        d.blockchainUpdater.processMicroBlock(microBlocks1And2.last) should beRight
        d.blockchainUpdater.processBlock(block2) should beRight // this should remove previous microblock
        d.blockchainUpdater.processMicroBlock(microBlock3.head) should beRight
        d.blockchainUpdater.shutdown()
      }
    }

    "VRF" in {
      val dapp   = KeyPair(Longs.toByteArray(Random.nextLong()))
      val sender = KeyPair(Longs.toByteArray(Random.nextLong()))

      withDomain(
        RideV4,
        balances = Seq(AddrWithBalance(dapp.toAddress, 10_00000000), AddrWithBalance(sender.toAddress, 10_00000000))
      ) { d =>
        val script = ScriptCompiler
          .compile(
            """
              |
              |{-# STDLIB_VERSION 4 #-}
              |{-# SCRIPT_TYPE ACCOUNT #-}
              |{-# CONTENT_TYPE DAPP #-}
              |
              |@Callable(i)
              |func default() = {
              |  [
              |    BinaryEntry("vrf", value(value(blockInfoByHeight(height)).vrf))
              |  ]
              |}
              |""".stripMargin,
            ScriptEstimatorV2
          )
          .explicitGet()
          ._1

        d.appendBlock(
          SetScriptTransaction.selfSigned(2.toByte, dapp, Some(script), 1_0000_0000L, ntpTime.getTimestamp()).explicitGet()
        )

        val invoke =
          Signed.invokeScript(3.toByte, sender, dapp.toAddress, None, Seq.empty, 600_0000L, Waves, ntpTime.getTimestamp())

        d.appendBlock(d.createBlock(5.toByte, Seq(invoke)))
      }
    }

    "spendableBalanceChanged" in {
      withLevelDBWriter(RideV6) { levelDb =>
        val ps    = PublishToOneSubject[(Address, Asset)]()
        val items = ps.toListL.runToFuture

        val blockchain = new BlockchainUpdaterImpl(
          levelDb,
          ps,
          RideV6,
          ntpTime,
          BlockchainUpdateTriggers.noop,
          loadActiveLeases(db, _, _)
        )

        val d = Domain(db, blockchain, levelDb, RideV6)
        blockchain.processBlock(d.createBlock(PlainBlockVersion, Seq(genesis(defaultAddress)), generator = TestBlock.defaultSigner))
        blockchain.processBlock(d.createBlock(PlainBlockVersion, Seq(transfer()), generator = TestBlock.defaultSigner))

        ps.onComplete()
        Await.result(items, 2.seconds) shouldBe Seq(
          (TestBlock.defaultSigner.toAddress, Waves),
          (defaultAddress, Waves),
          (TestBlock.defaultSigner.toAddress, Waves),
          (defaultAddress, Waves),
          (secondAddress, Waves)
        )
      }
    }
  }
}
