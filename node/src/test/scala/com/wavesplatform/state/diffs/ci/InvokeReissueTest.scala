package com.gicsports.state.diffs.ci
import com.gicsports.TestValues.invokeFee
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.lang.directives.values._
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.PropSpec
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TxHelpers._

class InvokeReissueTest extends PropSpec with WithDomain {
  import DomainPresets._

  property("invoke fails on reissue of foreign asset") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner,defaultSigner)) { d =>
      val issueTx = issue()
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() = [
           |   Reissue(base58'$asset', 1, true)
           | ]
        """.stripMargin
      )
      d.appendBlock(issueTx)
      d.appendBlock(setScript(secondSigner, dApp))
      d.appendAndAssertFailed(invoke(), "Asset was issued by other address")
    }
  }

  property("set reissuable = false and try to reissue via invoke") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      val issueTx = issue(secondSigner)
      val asset   = IssuedAsset(issueTx.id())
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() =
           |   [
           |     Reissue(base58'$asset', 1, false)
           |   ]
         """.stripMargin
      )
      d.appendBlock(setScript(secondSigner, dApp), issueTx)
      d.appendAndAssertSucceed(invoke())
      d.appendAndAssertFailed(invoke(), "Asset is not reissuable")
    }
  }

  property("Reissue transaction for asset issued via invoke") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner, defaultSigner)) { d =>
      val dApp = TestCompiler(V5).compileContract(
        s"""
           | @Callable(i)
           | func default() =
           |   [
           |     Issue("name", "description", 1000, 4, true, unit, 0)
           |   ]
         """.stripMargin
      )
      d.appendBlock(setScript(secondSigner, dApp))
      d.appendBlock(invoke(fee = invokeFee(issues = 1)))
      val asset = d.liquidDiff.issuedAssets.head._1
      d.appendBlock(reissue(asset, secondSigner, 234))
      d.blockchain.assetDescription(asset).get.totalVolume shouldBe 1234
    }
  }
}
