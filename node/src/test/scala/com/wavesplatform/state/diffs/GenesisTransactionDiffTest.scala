package com.gicsports.state.diffs

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.state.*
import com.gicsports.test.*
import com.gicsports.transaction.TxHelpers

class GenesisTransactionDiffTest extends PropSpec with WithState {

  property("fails if height != 1") {
    val genesis = TxHelpers.genesis(TxHelpers.address(1))
    val height  = 2
    GenesisTransactionDiff(height)(genesis) should produce("GenesisTransaction cannot appear in non-initial block")
  }

  property("Diff establishes Waves invariant") {
    val genesis = (1 to 10).map(idx => TxHelpers.genesis(TxHelpers.address(idx), 10000))

    assertDiffAndState(Seq.empty, TestBlock.create(genesis)) { (blockDiff, _) =>
      val totalPortfolioDiff: Portfolio = blockDiff.portfolios.values.fold(Portfolio())(_.combine(_).explicitGet())
      totalPortfolioDiff.balance shouldBe genesis.map(_.amount.value).sum
      totalPortfolioDiff.effectiveBalance.explicitGet() shouldBe genesis.map(_.amount.value).sum
      totalPortfolioDiff.assets shouldBe Map.empty

      genesis.foreach { gtx =>
        blockDiff.portfolios(gtx.recipient).balance shouldBe gtx.amount.value
      }
    }
  }
}
