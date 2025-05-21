package com.gicsports.state.diffs.smart

import com.gicsports.TransactionGenBase
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.features.BlockchainFeatures._
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.settings.TestFunctionalitySettings
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.ci.ciFee
import com.gicsports.test._
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.utils.Signed
import com.gicsports.transaction.{GenesisTransaction, TxVersion}

class EstimationSwitchTest extends PropSpec with WithDomain with TransactionGenBase {
  private val time = new TestTime
  private def ts   = time.getTimestamp()

  private val dAppScript =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default() = {
         |   if (1 != 1) then [] else []
         | }
       """.stripMargin
    )

  private val settings =
    TestFunctionalitySettings.withFeaturesByHeight(BlockV5 -> 0, SynchronousCalls -> 0, RideV6 -> 3)

  property("both evaluator and estimator complexities should be decreased after RideV6 activation") {
    val invoker   = accountGen.sample.get
    val dApp      = accountGen.sample.get
    val fee       = ciFee().sample.get
    val genesis1  = GenesisTransaction.create(invoker.toAddress, ENOUGH_AMT, ts).explicitGet()
    val genesis2  = GenesisTransaction.create(dApp.toAddress, ENOUGH_AMT, ts).explicitGet()
    val setScript = () => SetScriptTransaction.selfSigned(1.toByte, dApp, Some(dAppScript), fee, ts).explicitGet()
    val invoke    = () => Signed.invokeScript(TxVersion.V3, invoker, dApp.toAddress, None, Nil, fee, Waves, ts)

    withDomain(domainSettingsWithFS(settings)) { d =>
      d.appendBlock(genesis1, genesis2)

      d.appendBlock(setScript(), invoke())
      d.liquidDiff.scripts.head._2.get.complexitiesByEstimator(3)("default") shouldBe 5
      d.liquidDiff.scriptsComplexity shouldBe 7
      // bigger than estimator because of ignoring predefined user function complexities

      d.appendBlock(setScript(), invoke())
      d.liquidDiff.scripts.head._2.get.complexitiesByEstimator(3)("default") shouldBe 1
      d.liquidDiff.scriptsComplexity shouldBe 1
      // condition decreased by 1,
      // accessing to ref ([] = nil) decreased by 1,
      // != decreased by 4 (because of using predefined user function complexities)
    }
  }
}
