package com.gicsports.state.diffs.ci.sync

import com.gicsports.TransactionGenBase
import com.gicsports.account.Address
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.features.BlockchainFeatures._
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.settings.TestFunctionalitySettings
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.ci.ciFee
import com.gicsports.test._
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.gicsports.transaction.utils.Signed
import com.gicsports.transaction.{GenesisTransaction, Transaction, TxVersion}

class SyncDAppListArgTypesTest extends PropSpec with WithDomain with TransactionGenBase {
  private val time = new TestTime
  private def ts   = time.getTimestamp()

  private def dApp1Script(dApp2: Address, args: String): Script =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default() = {
         |    strict r = Address(base58'$dApp2').invoke("default", [$args], [])
         |    []
         | }
       """.stripMargin
    )

  private val dApp2Script: Script =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default(a: Int) = []
       """.stripMargin
    )

  private def scenario(args: String): (Seq[Transaction], () => InvokeScriptTransaction) = {
    val invoker  = accountGen.sample.get
    val dApp1    = accountGen.sample.get
    val dApp2    = accountGen.sample.get
    val fee      = ciFee().sample.get
    val gTxs     = Seq(invoker, dApp1, dApp2).map(acc => GenesisTransaction.create(acc.toAddress, ENOUGH_AMT, ts).explicitGet())
    val ssTx1    = SetScriptTransaction.selfSigned(1.toByte, dApp1, Some(dApp1Script(dApp2.toAddress, args)), fee, ts).explicitGet()
    val ssTx2    = SetScriptTransaction.selfSigned(1.toByte, dApp2, Some(dApp2Script), fee, ts).explicitGet()
    val invokeTx = () => Signed.invokeScript(TxVersion.V3, invoker, dApp1.toAddress, None, Nil, fee, Waves, ts)
    (gTxs ++ Seq(ssTx1, ssTx2), invokeTx)
  }

  private val settings =
    TestFunctionalitySettings.Enabled
      .copy(preActivatedFeatures = Map(Ride4DApps.id -> 0, BlockV5.id -> 0, SynchronousCalls.id -> 0, RideV6.id -> 3))

  private def assert(forbidAfterActivation: Boolean, args: String) = {
    withDomain(domainSettingsWithFS(settings)) { d =>
      val (preparingTxs, invoke) = scenario(args)
      d.appendBlock(preparingTxs: _*)

      val invoke1 = invoke()
      d.appendBlock(invoke1)
      d.blockchain.transactionSucceeded(invoke1.id.value()) shouldBe true

      val invoke2 = invoke()
      if (forbidAfterActivation) {
        (the[Exception] thrownBy d.appendBlock(invoke2)).getMessage should include(
          s"All arguments of InvokeScript must be one of the types: List[], Boolean, Int, ByteVector, String"
        )
      } else {
        d.appendBlock(invoke2)
        d.blockchain.transactionSucceeded(invoke2.id.value()) shouldBe true
      }
    }
  }

  property("sync call args types check") {
    assert(forbidAfterActivation = false, "1")
    assert(forbidAfterActivation = false, """ "s" """)
    assert(forbidAfterActivation = false, "true")
    assert(forbidAfterActivation = false, "base58''")
    assert(forbidAfterActivation = false, """ [1, "s", true, base58''] """)
    assert(forbidAfterActivation = false, """ [] """)

    assert(forbidAfterActivation = true, "unit")
    assert(forbidAfterActivation = true, "toBigInt(1)")
    assert(forbidAfterActivation = true, "[toBigInt(1)]")
    assert(forbidAfterActivation = true, "[unit]")
    assert(forbidAfterActivation = true, "[[]]")
  }
}
