package com.gicsports.state.diffs

import com.gicsports.account.KeyPair
import com.gicsports.block.Block
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.settings.FunctionalitySettings
import com.gicsports.state.{Blockchain, Diff}
import com.gicsports.test.node.*
import com.gicsports.transaction.{TxHelpers, TxVersion}
import com.gicsports.test.FreeSpec

class BlockDifferTest extends FreeSpec with WithState {
  private val TransactionFee = 10

  private val signerA, signerB = randomKeyPair()

  private val testChain: Seq[Block] = {
    val master, recipient = randomKeyPair()
    getTwoMinersBlockChain(master, recipient, 9)
  }

  "BlockDiffer" - {
    "enableMicroblocksAfterHeight" - {
      /*
      | N | fee | signer | A receive | A balance | B receive | B balance |
      |--:|:---:|:------:|----------:|----------:|----------:|-----------|
      |1  |0    |A       |0          |0          |0          |0          | <- genesis
      |2  |10   |B       |0          |0          |10         |+10        |
      |3  |10   |A       |10         |+10        |0          |0          |
      |4  |10   |B       |0          |10         |+10        |10+10=20   |
      |5  |10   |A       |10         |10+10=20   |0          |20         |
      |6  |10   |B       |0          |20         |+10        |20+10=30   |
      |7  |10   |A       |10         |20+10=30   |0          |30         |
      |8  |10   |B       |0          |30         |+10        |30+10=40   |
      |9  |10   |A       |10         |30+10=40   |0          |40         | <- 1st check
      |10 |10   |B       |0          |40         |+10        |40+10=50   | <- 2nd check
       */
      "height < enableMicroblocksAfterHeight - a miner should receive 100% of the current block's fee" in {
        assertDiff(testChain.init, 1000) {
          case (_, s) =>
            s.balance(signerA.toAddress) shouldBe 40
        }

        assertDiff(testChain, 1000) {
          case (_, s) =>
            s.balance(signerB.toAddress) shouldBe 50
        }
      }

      /*
      | N | fee | signer | A receive | A balance | B receive | B balance |
      |--:|:---:|:------:|----------:|----------:|----------:|-----------|
      |1  |0    |A       |0          |0          |0          |0          | <- genesis
      |2  |10   |B       |0          |0          |10         |+10        |
      |3  |10   |A       |10         |+10        |0          |0          |
      |4  |10   |B       |0          |10         |+10        |10+10=20   |
      |5  |10   |A       |10         |10+10=20   |0          |20         |
      |6  |10   |B       |0          |20         |+10        |20+10=30   |
      |7  |10   |A       |10         |20+10=30   |0          |30         |
      |8  |10   |B       |0          |30         |+10        |30+10=40   |
      |9  |10   |A       |10         |30+10=40   |0          |40         |
      |-------------------------- Enable NG -----------------------------|
      |10 |10   |B       |0          |40         |+4         |40+4=44    | <- check
       */
      "height = enableMicroblocksAfterHeight - a miner should receive 40% of the current block's fee only" in {
        assertDiff(testChain, 9) {
          case (_, s) =>
            s.balance(signerB.toAddress) shouldBe 44
        }
      }

      /*
      | N | fee | signer | A receive | A balance | B receive | B balance |
      |--:|:---:|:------:|----------:|----------:|----------:|-----------|
      |1  |0    |A       |0          |0          |0          |0          | <- genesis
      |2  |10   |B       |0          |0          |10         |+10        |
      |3  |10   |A       |10         |+10        |0          |0          |
      |4  |10   |B       |0          |10         |+10        |10+10=20   |
      |-------------------------- Enable NG -----------------------------|
      |5  |10   |A       |4          |10+4=14    |0          |20         |
      |6  |10   |B       |0          |14         |+4+6=10    |20+10=30   |
      |7  |10   |A       |4+6=10     |14+10=24   |0          |30         |
      |8  |10   |B       |0          |24         |+4+6=10    |30+10=40   |
      |9  |10   |A       |4+6=10     |24+10=34   |0          |40         | <- 1st check
      |10 |10   |B       |0          |34         |+4+6=10    |40+10=50   | <- 2nd check
       */
      "height > enableMicroblocksAfterHeight - a miner should receive 60% of previous block's fee and 40% of the current one" in {
        assertDiff(testChain.init, 4) {
          case (_, s) =>
            s.balance(signerA.toAddress) shouldBe 34
        }

        assertDiff(testChain, 4) {
          case (_, s) =>
            s.balance(signerB.toAddress) shouldBe 50
        }
      }
    }
  }

  private def assertDiff(blocks: Seq[Block], ngAtHeight: Int)(assertion: (Diff, Blockchain) => Unit): Unit = {
    val fs = FunctionalitySettings(featureCheckBlocksPeriod = ngAtHeight / 2, blocksForFeatureActivation = 1, preActivatedFeatures = Map[Short, Int]((2, ngAtHeight)), doubleFeaturesPeriodsAfterHeight = Int.MaxValue)
    assertNgDiffState(blocks.init, blocks.last, fs)(assertion)
  }

  private def getTwoMinersBlockChain(from: KeyPair, to: KeyPair, numPayments: Int): Seq[Block] = {
    val genesisTx            = TxHelpers.genesis(from.toAddress, Long.MaxValue - 1)
    val features: Seq[Short] = Seq[Short](2)

    val paymentTxs = (1 to numPayments).map { i =>
      TxHelpers.transfer(from, to.toAddress, 10000, fee = TransactionFee, version = TxVersion.V1)
    }

    (genesisTx +: paymentTxs).zipWithIndex.map {
      case (x, i) =>
        val signer = if (i % 2 == 0) signerA else signerB
        TestBlock.create(signer, Seq(x), features)
    }
  }
}
