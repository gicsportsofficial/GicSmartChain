package com.gicsports.state.diffs.ci

import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.features.BlockchainFeatures.*
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.settings.TestFunctionalitySettings
import com.gicsports.test.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.{Asset, TxHelpers}

class NegativeBurnTest extends PropSpec with WithDomain {

  private def sigVerify(c: Boolean) =
    s""" strict c = ${if (c) (1 to 5).map(_ => "sigVerify(base58'', base58'', base58'')").mkString(" || ") else "true"} """

  private def dAppScript(asset: Asset, bigComplexity: Boolean): Script =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default() = {
         |    ${sigVerify(bigComplexity)}
         |    [
         |      Burn(base58'$asset', -1)
         |    ]
         | }
       """.stripMargin
    )

  private val settings =
    TestFunctionalitySettings
      .withFeatures(BlockV5, SynchronousCalls, RideV6)

  property("negative burn quantity") {
    for (bigComplexity <- Seq(false, true)) {
      val invoker = TxHelpers.signer(0)
      val dApp    = TxHelpers.signer(1)

      val balances = AddrWithBalance.enoughBalances(invoker, dApp)

      val issue     = TxHelpers.issue(dApp, 100)
      val asset     = IssuedAsset(issue.id.value())
      val setScript = TxHelpers.setScript(dApp, dAppScript(asset, bigComplexity))

      val preparingTxs = Seq(issue, setScript)

      val invoke = TxHelpers.invoke(dApp.toAddress, func = None, invoker = invoker)

      withDomain(domainSettingsWithFS(settings), balances) { d =>
        d.appendBlock(preparingTxs*)

        if (!bigComplexity) {
          d.appendAndCatchError(invoke).toString should include("Negative burn quantity")
        } else {
          d.appendAndAssertFailed(invoke)
        }
      }
    }
  }
}
