package com.gicsports.state.diffs.ci

import com.gicsports.account.Alias
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.PropSpec
import com.gicsports.transaction.TxHelpers._

class InvokeAffectedAddressTest extends PropSpec with WithDomain {
  import DomainPresets._

  private def dApp(failed: Boolean) =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default() = [${if (failed) "Burn(base58'', 1)" else ""}]
       """.stripMargin
    )

  property("tx belongs to dApp address without actions") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      Seq(true, false).foreach { failed =>
        d.appendBlock(setScript(secondSigner, dApp(failed)))
        if (failed)
          d.appendAndAssertFailed(invoke(secondAddress))
        else
          d.appendAndAssertSucceed(invoke(secondAddress))
        d.liquidDiff.transactions.head.affected shouldBe Set(defaultAddress, secondAddress)
      }
    }
  }

  property("tx belongs to dApp address when called by alias") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      d.appendBlock(createAlias("alias", secondSigner))
      Seq(true, false).foreach { failed =>
        d.appendBlock(setScript(secondSigner, dApp(failed)))
        if (failed)
          d.appendAndAssertFailed(invoke(Alias.create("alias").explicitGet()))
        else
          d.appendAndAssertSucceed(invoke(Alias.create("alias").explicitGet()))
        d.liquidDiff.transactions.head.affected shouldBe Set(defaultAddress, secondAddress)
      }
    }
  }

  property("tx belongs to dApp address when called by alias created in current block") {
    withDomain(RideV5, AddrWithBalance.enoughBalances(secondSigner)) { d =>
      Seq(true, false).foreach { failed =>
        val invokeTx = invoke(Alias.create(s"$failed").explicitGet())
        val aliasTx  = createAlias(s"$failed", secondSigner)
        d.appendBlock(setScript(secondSigner, dApp(failed)))
        if (failed) {
          d.appendBlock(aliasTx, invokeTx)
          d.liquidDiff.errorMessage(invokeTx.id()) shouldBe defined
        } else
          d.appendAndAssertSucceed(aliasTx, invokeTx)
        d.liquidDiff.transaction(invokeTx.id()).get.affected shouldBe Set(defaultAddress, secondAddress)
      }
    }
  }
}
