package com.gicsports.state.diffs.smart.scenarios

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils.*
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.compiler.Terms.EXPR
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.smart.*
import com.gicsports.test.*
import com.gicsports.transaction.{GenesisTransaction, TxHelpers}
import com.gicsports.transaction.lease.LeaseTransaction
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.transfer.TransferTransaction

class OnlyTransferIsAllowedTest extends PropSpec with WithState {

  property("transfer is allowed but lease is not due to predicate") {

    val scriptText =
      s"""
         |
         | match tx {
         |  case ttx: TransferTransaction | MassTransferTransaction =>
         |     sigVerify(ttx.bodyBytes,ttx.proofs[0],ttx.senderPublicKey)
         |  case _ =>
         |     false
         | }
      """.stripMargin
    val untyped         = Parser.parseExpr(scriptText).get.value
    val transferAllowed = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untyped).explicitGet()._1

    val (genesis, script, lease, transfer) = preconditions(transferAllowed)
    assertDiffAndState(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(transfer)), smartEnabledFS) { case _ => () }
    assertDiffEi(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(lease)), smartEnabledFS)(
      totalDiffEi => totalDiffEi should produce("TransactionNotAllowedByScript")
    )
  }

  private def preconditions(typed: EXPR): (GenesisTransaction, SetScriptTransaction, LeaseTransaction, TransferTransaction) = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis   = TxHelpers.genesis(master.toAddress)
    val setScript = TxHelpers.setScript(master, ExprScript(typed).explicitGet())
    val transfer  = TxHelpers.transfer(master, recipient.toAddress, ENOUGH_AMT / 2)
    val lease     = TxHelpers.lease(master, recipient.toAddress, ENOUGH_AMT / 2)

    (genesis, setScript, lease, transfer)
  }
}
