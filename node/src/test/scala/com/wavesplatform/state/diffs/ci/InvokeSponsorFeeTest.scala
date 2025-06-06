package com.gicsports.state.diffs.ci
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.v1.ExprScript.ExprScriptImpl
import com.gicsports.lang.v1.compiler.{Terms, TestCompiler}
import com.gicsports.test.PropSpec
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TxHelpers.{defaultSigner, invoke, issue, secondSigner, setScript}

class InvokeSponsorFeeTest extends PropSpec with WithDomain {
  import DomainPresets.*

  property("invoke fails on sponsorship of foreign asset") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(defaultSigner,secondSigner)) { d =>
      val issueTx = issue()
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() = [
           |   SponsorFee(base58'$asset', 1)
           | ]
        """.stripMargin
      )
      d.appendBlock(issueTx)
      d.appendBlock(setScript(secondSigner, dApp))
      d.appendAndAssertFailed(invoke(), s"SponsorFee assetId=$asset was not issued from address of current dApp")
    }
  }

  property("invoke fails on sponsorship of smart asset") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      val issueTx = issue(secondSigner, script = Some(ExprScriptImpl(V3, false, Terms.TRUE)))
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() = [
           |   SponsorFee(base58'$asset', 1)
           | ]
        """.stripMargin
      )
      d.appendBlock(issueTx)
      d.appendBlock(setScript(secondSigner, dApp))
      d.appendAndAssertFailed(invoke(), s"Sponsorship smart assets is disabled")
    }
  }
}
