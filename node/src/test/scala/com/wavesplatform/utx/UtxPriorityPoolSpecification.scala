package com.gicsports.utx

import com.gicsports.account.KeyPair
import com.gicsports.db.WithState
import com.gicsports.lang.directives.values.V3
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.mining.MultiDimensionalMiningConstraint
import com.gicsports.settings.WavesSettings
import com.gicsports.test.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TxHelpers
import com.gicsports.utx.UtxPool.PackStrategy

class UtxPriorityPoolSpecification extends FreeSpec with SharedDomain {
  private val alice = TxHelpers.signer(100)

  private var lastKeyPair = 0
  private def nextKeyPair = {
    lastKeyPair += 1
    TxHelpers.signer(lastKeyPair)
  }

  override val genesisBalances: Seq[WithState.AddrWithBalance] = Seq(alice -> 10000.waves)

  override def settings: WavesSettings = DomainPresets.RideV3

  private def pack() = domain.utxPool.packUnconfirmed(MultiDimensionalMiningConstraint.unlimited, PackStrategy.Unlimited)._1

  private def mkHeightSensitiveScript(sender: KeyPair) =
    TxHelpers.setScript(
      sender,
      TestCompiler(V3).compileExpression(s"""
                                            |match tx {
                                            |    case _: TransferTransaction => height % 2 == ${domain.blockchain.height % 2}
                                            |    case _ => true
                                            |}
                                            |""".stripMargin),
      fee = 1.waves
    )

  "priority pool" - {
    "preserves correct order of transactions" in {
      val id = domain.appendKeyBlock().id()
      val t1 = TxHelpers.transfer(alice, nextKeyPair.toAddress, fee = 0.02.waves)
      val t2 = TxHelpers.transfer(alice, nextKeyPair.toAddress, fee = 0.02.waves, timestamp = t1.timestamp - 10000)

      domain.appendMicroBlock(t1)
      domain.appendMicroBlock(t2)
      domain.appendKeyBlock(Some(id))

      val expectedTransactions = Seq(t1, t2)
      domain.utxPool.priorityPool.priorityTransactions shouldBe expectedTransactions
      pack() shouldBe Some(expectedTransactions)
    }

    "takes into account priority txs when packing" in {
      val id        = domain.appendKeyBlock().id()
      val bob       = nextKeyPair
      val transfer1 = TxHelpers.transfer(alice, bob.toAddress, 10.02.waves, fee = 0.02.waves)

      domain.appendMicroBlock(transfer1)
      domain.appendKeyBlock(Some(id))

      domain.utxPool.priorityPool.priorityTransactions shouldBe Seq(transfer1)

      val transfer2 = TxHelpers.transfer(bob, nextKeyPair.toAddress, 10.waves, fee = 0.02.waves)

      domain.utxPool.putIfNew(transfer2).resultE should beRight
      domain.utxPool.nonPriorityTransactions shouldBe Seq(transfer2)
      pack() shouldBe Some(Seq(transfer1, transfer2))
    }

    "counts microblock size from priority diffs" in {
      val ref = domain.appendKeyBlock().id()
      domain.appendMicroBlock(Seq.tabulate(5) { i =>
        TxHelpers.transfer(alice, TxHelpers.signer(200 + i).toAddress)
      }*)
      domain.appendMicroBlock(Seq.tabulate(5) { i =>
        TxHelpers.transfer(alice, TxHelpers.signer(300 + i).toAddress)
      }*)

      domain.appendKeyBlock(Some(ref))
      // priority pool contains two microblocks, 5 txs each
      domain.utxPool.priorityPool.nextMicroBlockSize(3) shouldBe 5
      domain.utxPool.priorityPool.nextMicroBlockSize(5) shouldBe 5
      domain.utxPool.priorityPool.nextMicroBlockSize(8) shouldBe 5
      domain.utxPool.priorityPool.nextMicroBlockSize(10) shouldBe 10
      domain.utxPool.priorityPool.nextMicroBlockSize(12) shouldBe 12
    }

    "cleans up priority pool only when packing, not during cleanup" in {

      val bob, carol = nextKeyPair

      domain.appendKeyBlock()
      val rollbackTarget = domain.appendMicroBlock(
        TxHelpers.transfer(alice, bob.toAddress, 11.06.waves, fee = 0.02.waves),
        mkHeightSensitiveScript(bob)
      )
      val transferToCarol = TxHelpers.transfer(bob, carol.toAddress, 10.waves, fee = 0.06.waves)
      domain.appendMicroBlock(transferToCarol)

      domain.appendKeyBlock(Some(rollbackTarget))
      domain.utxPool.cleanUnconfirmed()
      domain.utxPool.priorityPool.priorityTransactions shouldEqual Seq(transferToCarol)
      pack() shouldBe None
      domain.utxPool.priorityPool.priorityTransactions shouldBe empty
    }

    "continues packing when priority diff contains no valid transactions" in {
      val bob = nextKeyPair
      domain.appendBlock(
        TxHelpers.transfer(alice, bob.toAddress, 21.1.waves, fee = 0.02.waves),
        mkHeightSensitiveScript(bob)
      )
      val ref       = domain.appendKeyBlock().id()
      val transfer1 = TxHelpers.transfer(bob, nextKeyPair.toAddress, 10.waves, fee = 0.06.waves)
      domain.appendMicroBlock(transfer1)
      domain.appendKeyBlock(Some(ref))
      domain.utxPool.priorityPool.priorityTransactions shouldEqual Seq(transfer1)

      val createAlias = TxHelpers.createAlias("0xbob", bob, 10.04.waves)
      domain.utxPool.putIfNew(createAlias).resultE should beRight
      domain.utxPool.all shouldEqual Seq(transfer1, createAlias)

      pack() shouldEqual Some(Seq(createAlias))
    }

    "tx from last microblock is placed on next height ahead of new txs after appending key block" in {
      domain.utxPool.removeAll(domain.utxPool.nonPriorityTransactions)
      val blockId = domain.appendKeyBlock().id()

      val issue    = TxHelpers.issue(alice)
      val transfer = TxHelpers.transfer(alice, asset = IssuedAsset(issue.id()))

      domain.appendMicroBlock(issue)
      domain.blockchain.transactionInfo(issue.id()) shouldBe defined
      domain.utxPool.priorityPool.priorityTransactions shouldBe Nil

      domain.appendKeyBlock(Some(blockId))
      domain.blockchain.transactionInfo(issue.id()) shouldBe None
      domain.utxPool.priorityPool.priorityTransactions shouldBe Seq(issue)

      domain.utxPool.putIfNew(transfer)
      pack() shouldBe Some(List(issue, transfer))
    }
  }
}
