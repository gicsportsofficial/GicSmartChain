package com.gicsports.state.diffs.smart

import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.features.BlockchainFeatures.{BlockV5, SynchronousCalls}
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.settings.TestFunctionalitySettings
import com.gicsports.test.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TxHelpers
import org.scalatest.EitherValues

class VerifierComplexityLimitTest extends PropSpec with WithDomain with EitherValues {

  private val verifier = TestCompiler(V5).compileExpression {
    s"""
       | func f0() = true
       | ${(0 until 12).map(i => s"func f${i + 1}() = if (f$i()) then f$i() else f$i()").mkString("\n")}
       | ${(12 until 65).map(i => s"func f${i + 1}() = if (f$i()) then throw() else f$i()").mkString("\n")}
       | f65()
     """.stripMargin
  }

  private def features(fix: Boolean) =
    TestFunctionalitySettings
      .withFeatures(BlockV5, SynchronousCalls)
      .copy(estimatorSumOverflowFixHeight = if (fix) 3 else 999)

  property("account verifier evaluation should be limited after RideV6 activation") {
    val account1 = TxHelpers.signer(1)
    val account2 = TxHelpers.signer(2)

    val balances = AddrWithBalance.enoughBalances(account1)

    val setScript = TxHelpers.setScript(account1, verifier)
    val checkTx   = () => TxHelpers.transfer(account1, account2.toAddress, 1)

    withDomain(domainSettingsWithFS(features(fix = false)), balances) { d =>
      d.appendBlock(setScript)
      d.appendBlockE(checkTx()) should produce("Explicit script termination")
    }
    withDomain(domainSettingsWithFS(features(fix = true)), balances) { d =>
      d.appendBlock(setScript)
      d.appendBlockE(checkTx()) should produce("Verifier complexity limit = 2000 is exceeded")
    }
  }

  property("asset verifier evaluation should be limited after RideV6 activation") {
    val account1 = TxHelpers.signer(1)
    val account2 = TxHelpers.signer(2)

    val balances = AddrWithBalance.enoughBalances(account1)

    val issue   = TxHelpers.issue(account1, 1, script = Some(verifier))
    val asset   = IssuedAsset(issue.id())
    val checkTx = () => TxHelpers.transfer(account1, account2.toAddress, 1, asset)

    withDomain(domainSettingsWithFS(features(fix = false)), balances) { d =>
      d.appendBlock(issue)
      d.appendBlockE(checkTx()) should produce("Explicit script termination")
    }
    withDomain(domainSettingsWithFS(features(fix = true)), balances) { d =>
      d.appendBlock(issue)
      d.appendBlockE(checkTx()) should produce("Verifier complexity limit = 4000 is exceeded")
    }
  }
}
