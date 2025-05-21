package com.gicsports.state.diffs.smart.scenarios

import com.gicsports.common.utils._
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.directives.values.{Expression, V3}
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils.compilerContext
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.compiler.Terms.EXPR
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.state.BinaryDataEntry
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.smart.smartEnabledFS
import com.gicsports.test.PropSpec
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.{DataTransaction, GenesisTransaction, TxHelpers, TxVersion}

class TransferByIdTest extends PropSpec with WithState {

  val scriptSrc: String =
    s"""
       |match tx {
       |  case dtx: DataTransaction =>
       |    let txId    = extract(getBinary(dtx.data, "transfer_id"))
       |    let maybeTx = transferTransactionById(txId)
       |
       |    isDefined(maybeTx)
       |
       |  case _ => false
       |}
     """.stripMargin

  val expr: EXPR = {
    val parsed = Parser.parseExpr(scriptSrc).get.value
    ExpressionCompiler(compilerContext(V3, Expression, isAssetScript = false), parsed).explicitGet()._1
  }

  property("Transfer by id works fine") {
    preconditions.foreach {
      case (genesis, transfer, setScript, data) =>
        assertDiffEi(
          Seq(TestBlock.create(Seq(genesis, transfer))),
          TestBlock.create(Seq(setScript, data)),
          smartEnabledFS
        )(_ shouldBe an[Right[_, _]])
    }
  }

  private def preconditions: Seq[(GenesisTransaction, TransferTransaction, SetScriptTransaction, DataTransaction)] = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis   = TxHelpers.genesis(master.toAddress)
    val setScript = TxHelpers.setScript(master, ExprScript(V3, expr).explicitGet())

    Seq(
      TxHelpers.transfer(master, recipient.toAddress, ENOUGH_AMT / 2),
      TxHelpers.transfer(master, recipient.toAddress, ENOUGH_AMT / 2, version = TxVersion.V1)
    ).map { transfer =>
      val data = TxHelpers.data(master, Seq(BinaryDataEntry("transfer_id", transfer.id())))

      (genesis, transfer, setScript, data)
    }
  }
}
