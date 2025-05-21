package com.gicsports.state.diffs.ci.sync

import com.gicsports.TransactionGenBase
import com.gicsports.account.Address
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.ci.ciFee
import com.gicsports.test.*
import com.gicsports.test.DomainPresets.*
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.utils.Signed
import com.gicsports.transaction.{GenesisTransaction, TxVersion}

class SyncDAppBalanceCheckTest extends PropSpec with WithDomain with TransactionGenBase {

  private val time = new TestTime
  private def ts   = time.getTimestamp()

  private def dApp1Script(dApp2: Address): Script =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default() = {
         |    strict r = Address(base58'$dApp2').invoke("default", [], [AttachedPayment(unit, 100)])
         |    []
         | }
       """.stripMargin
    )

  private val dApp2Script: Script =
    TestCompiler(V5).compileContract(
      s"""
         | @Callable(i)
         | func default() =
         |   [
         |     ScriptTransfer(i.caller, 100, unit)
         |   ]
       """.stripMargin
    )

  private val scenario =
    for {
      invoker <- accountGen
      dApp1   <- accountGen
      dApp2   <- accountGen
      fee     <- ciFee()
      gTx1     = GenesisTransaction.create(invoker.toAddress, ENOUGH_AMT, ts).explicitGet()
      gTx2     = GenesisTransaction.create(dApp1.toAddress, 1.waves, ts).explicitGet()
      gTx3     = GenesisTransaction.create(dApp2.toAddress, ENOUGH_AMT, ts).explicitGet()
      ssTx1    = SetScriptTransaction.selfSigned(1.toByte, dApp1, Some(dApp1Script(dApp2.toAddress)), 1.waves, ts).explicitGet()
      ssTx2    = SetScriptTransaction.selfSigned(1.toByte, dApp2, Some(dApp2Script), 1.waves, ts).explicitGet()
      invokeTx = () => Signed.invokeScript(TxVersion.V3, invoker, dApp1.toAddress, None, Nil, fee, Waves, ts)
    } yield (Seq(gTx1, gTx2, gTx3, ssTx1, ssTx2), invokeTx)

  property("temporary negative balance of sync call produces error") {
    val (preparingTxs, invoke) = scenario.sample.get
    val settings =
      DomainPresets.RideV5
        .configure(_.copy(enforceTransferValidationAfter = 2))
        .setFeaturesHeight(BlockchainFeatures.RideV6 -> 4)

    withDomain(settings) { d =>
      d.appendBlock(preparingTxs*)

      val invoke1 = invoke()
      d.appendAndCatchError(invoke1).toString should include("Negative GIC balance")
    }
  }
}
