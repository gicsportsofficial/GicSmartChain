package com.gicsports.state

import java.util.concurrent.TimeUnit

import com.gicsports.account.KeyPair
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.directives.values._
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils._
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.settings.FunctionalitySettings
import com.gicsports.state.StateSyntheticBenchmark._
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.Transaction
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.transfer._
import org.openjdk.jmh.annotations._
import org.scalacheck.Gen

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class StateSyntheticBenchmark {

  @Benchmark
  def appendBlock_test(db: St): Unit = db.genAndApplyNextBlock()

  @Benchmark
  def appendBlock_smart_test(db: SmartSt): Unit = db.genAndApplyNextBlock()

}

object StateSyntheticBenchmark {

  @State(Scope.Benchmark)
  class St extends BaseState {
    protected override def txGenP(sender: KeyPair, ts: Long): Gen[Transaction] =
      for {
        amount    <- Gen.choose(1, GIC(1))
        recipient <- accountGen
      } yield TransferTransaction.selfSigned(1.toByte, sender, recipient.toAddress, Waves, amount, Waves, 100000, ByteStr.empty, ts).explicitGet()
  }

  @State(Scope.Benchmark)
  class SmartSt extends BaseState {

    override protected def updateFunctionalitySettings(base: FunctionalitySettings): FunctionalitySettings = {
      base.copy(preActivatedFeatures = Map(4.toShort -> 0))
    }

    protected override def txGenP(sender: KeyPair, ts: Long): Gen[Transaction] =
      for {
        recipient: KeyPair <- accountGen
        amount             <- Gen.choose(1, GIC(1))
      } yield TransferTransaction
        .selfSigned(2.toByte, sender, recipient.toAddress, Waves, amount, Waves, 1000000, ByteStr.empty, ts)
        .explicitGet()
    @Setup
    override def init(): Unit = {
      super.init()

      val textScript    = "sigVerify(tx.bodyBytes,tx.proofs[0],tx.senderPublicKey)"
      val untypedScript = Parser.parseExpr(textScript).get.value
      val typedScript   = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untypedScript).explicitGet()._1

      val setScriptBlock = nextBlock(
        Seq(
          SetScriptTransaction
            .selfSigned(1.toByte, richAccount, Some(ExprScript(typedScript).explicitGet()), 1000000, System.currentTimeMillis())
            .explicitGet()
        )
      )

      applyBlock(setScriptBlock)
    }
  }

}
