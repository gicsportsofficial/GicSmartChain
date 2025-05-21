package com.gicsports.state.diffs.ci

import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.v1.ExprScript.ExprScriptImpl
import com.gicsports.lang.v1.compiler.Terms.TRUE
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.{PropSpec, produce}
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TxHelpers.{defaultSigner, invoke, issue, secondSigner, setScript}

class ScriptActionsTest extends PropSpec with WithDomain {
  import DomainPresets._

  property("ScriptTransfer after burning whole amount") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      val issueTx = issue(secondSigner)
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() =
           |   [
           |     Burn(base58'$asset', ${issueTx.quantity}),
           |     ScriptTransfer(i.caller, 1, base58'$asset')
           |   ]
         """.stripMargin
      )
      d.appendBlock(setScript(secondSigner, dApp), issueTx)
      d.appendBlockE(invoke()) should produce("negative asset balance")
    }
  }

  property("Burn after transferring whole amount") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      val issueTx = issue(secondSigner)
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() =
           |   [
           |     ScriptTransfer(i.caller, ${issueTx.quantity}, base58'$asset'),
           |     Burn(base58'$asset', 1)
           |   ]
         """.stripMargin
      )
      d.appendBlock(setScript(secondSigner, dApp), issueTx)
      d.appendBlockE(invoke()) should produce("negative asset balance")
    }
  }

  property("SponsorFee smart asset") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      val issueTx = issue(secondSigner, script = Some(ExprScriptImpl(V5, false, TRUE)))
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() =
           |   [
           |     SponsorFee(base58'$asset', 1)
           |   ]
         """.stripMargin
      )
      d.appendBlock(setScript(secondSigner, dApp), issueTx)
      d.appendAndAssertFailed(invoke(), "Sponsorship smart assets is disabled")
    }
  }

  property("SponsorFee foreign asset") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner,defaultSigner)) { d =>
      val issueTx = issue()
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() =
           |   [
           |     SponsorFee(base58'$asset', 1)
           |   ]
         """.stripMargin
      )
      d.appendBlock(setScript(secondSigner, dApp), issueTx)
      d.appendAndAssertFailed(invoke(), s"SponsorFee assetId=$asset was not issued from address of current dApp")
    }
  }
}
