package com.gicsports.state.diffs.smart.performance

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils.*
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.compiler.Terms.*
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.metrics.Instrumented
import com.gicsports.state.diffs.smart.*
import com.gicsports.test.*
import com.gicsports.transaction.{TxHelpers, TxVersion}

class SigVerifyPerformanceTest extends PropSpec with WithState {

  private val AmtOfTxs = 10000

  private def differentTransfers(typed: EXPR) = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis = TxHelpers.genesis(master.toAddress)

    val setScript       = TxHelpers.setScript(master, ExprScript(typed).explicitGet())
    val transfers       = (1 to AmtOfTxs).map(_ => TxHelpers.transfer(master, recipient.toAddress, version = TxVersion.V1))
    val scriptTransfers = (1 to AmtOfTxs).map(_ => TxHelpers.transfer(master, recipient.toAddress))

    (genesis, setScript, transfers, scriptTransfers)
  }

  ignore("parallel native signature verification vs sequential scripted signature verification") {
    val textScript    = "sigVerify(tx.bodyBytes,tx.proofs[0],tx.senderPk)"
    val untypedScript = Parser.parseExpr(textScript).get.value
    val typedScript   = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untypedScript).explicitGet()._1

    val (gen, setScript, transfers, scriptTransfers) = differentTransfers(typedScript)

    def simpleCheck(): Unit = assertDiffAndState(Seq(TestBlock.create(Seq(gen))), TestBlock.create(transfers), smartEnabledFS) { case _ => }
    def scriptedCheck(): Unit =
      assertDiffAndState(Seq(TestBlock.create(Seq(gen, setScript))), TestBlock.create(scriptTransfers), smartEnabledFS) {
        case _ =>
      }

    val simeplCheckTime   = Instrumented.withTimeMillis(simpleCheck())._2
    val scriptedCheckTime = Instrumented.withTimeMillis(scriptedCheck())._2
    println(s"[parallel] simple check time: $simeplCheckTime ms,\t [seqential] scripted check time: $scriptedCheckTime ms")
  }
}
