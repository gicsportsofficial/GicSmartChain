package com.gicsports.state.diffs.smart.scenarios

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils.*
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.smart.*
import com.gicsports.test.*
import com.gicsports.transaction.{GenesisTransaction, TxHelpers}
import com.gicsports.transaction.lease.LeaseTransaction
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.transfer.*

class TransactionFieldAccessTest extends PropSpec with WithState {

  private def preconditionsTransferAndLease(code: String): (GenesisTransaction, SetScriptTransaction, LeaseTransaction, TransferTransaction) = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis   = TxHelpers.genesis(master.toAddress)
    val untyped   = Parser.parseExpr(code).get.value
    val typed     = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untyped).explicitGet()._1
    val setScript = TxHelpers.setScript(master, ExprScript(typed).explicitGet())
    val transfer  = TxHelpers.transfer(master, recipient.toAddress, ENOUGH_AMT / 2)
    val lease     = TxHelpers.lease(master, recipient.toAddress, ENOUGH_AMT / 2)

    (genesis, setScript, lease, transfer)
  }

  private val script =
    """
      |
      | match tx {
      | case ttx: TransferTransaction =>
      |       isDefined(ttx.assetId)==false
      | case _ =>
      |       false
      | }
      """.stripMargin

  property("accessing field of transaction without checking its type first results on exception") {
    val (genesis, setScript, lease, transfer) = preconditionsTransferAndLease(script)
    assertDiffAndState(Seq(TestBlock.create(Seq(genesis, setScript))), TestBlock.create(Seq(transfer)), smartEnabledFS) { case _ => () }
    assertDiffEi(Seq(TestBlock.create(Seq(genesis, setScript))), TestBlock.create(Seq(lease)), smartEnabledFS)(
      totalDiffEi => totalDiffEi should produce("TransactionNotAllowedByScript")
    )
  }
}
