package com.gicsports.state.diffs.smart.scenarios

import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.v1.compiler.Terms.*
import com.gicsports.state.diffs.smart.smartEnabledFS
import com.gicsports.test.*
import com.gicsports.transaction.{Proofs, TxHelpers}

class OneProofForNonScriptedAccountTest extends PropSpec with WithState {

  property("exactly 1 proof required for non-scripted accounts") {
    val s = {
      val master    = TxHelpers.signer(1)
      val recipient = TxHelpers.signer(2)

      val genesis   = TxHelpers.genesis(master.toAddress)
      val setScript = TxHelpers.setScript(master, ExprScript(TRUE).explicitGet())
      val transfer  = TxHelpers.transfer(master, recipient.toAddress)

      (genesis, setScript, transfer)
    }

    val (genesis, _, transfer) = s
    val transferWithExtraProof = transfer.copy(proofs = Proofs(Seq(ByteStr.empty, ByteStr(Array(1: Byte)))))
    assertDiffEi(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(transferWithExtraProof)), smartEnabledFS)(
      totalDiffEi => totalDiffEi should produce("must have exactly 1 proof")
    )
  }

}
