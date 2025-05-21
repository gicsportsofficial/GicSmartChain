package com.gicsports.state.diffs.smart.predef

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.lang.directives.DirectiveDictionary
import com.gicsports.lang.directives.values.{StdLibVersion, V3, V4}
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.state.diffs.ci.ciFee
import com.gicsports.test.*
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.GenesisTransaction
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.utils.Signed
import org.scalatest.EitherValues

class GenericRideActivationTest extends PropSpec with WithDomain with EitherValues {

  import DomainPresets.*

  private val time = new TestTime
  private def ts   = time.getTimestamp()

  private def dApp(version: StdLibVersion) = TestCompiler(version).compileContract(
    s"""
       | @Callable(i)
       | func default() = ${if (version == V3) "WriteSet([])" else "[]"}
     """.stripMargin
  )

  private def verifier(version: StdLibVersion) = TestCompiler(version).compileExpression("true")

  private def scenario(version: StdLibVersion) =
    for {
      master  <- accountGen
      invoker <- accountGen
      fee     <- ciFee(sc = 1)
      gTx1   = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      gTx2   = GenesisTransaction.create(invoker.toAddress, ENOUGH_AMT, ts).explicitGet()
      ssTx   = SetScriptTransaction.selfSigned(1.toByte, master, Some(dApp(version)), 1.waves, ts).explicitGet()
      ssTx2  = SetScriptTransaction.selfSigned(1.toByte, invoker, Some(verifier(version)), 1.waves, ts).explicitGet()
      invoke = Signed.invokeScript(1.toByte, invoker, master.toAddress, None, Nil, fee, Waves, ts)
    } yield (Seq(gTx1, gTx2), Seq(ssTx, ssTx2), invoke)

  property("RIDE versions activation") {
    DirectiveDictionary[StdLibVersion].all
      .filter(_ >= V3)
      .zip(DirectiveDictionary[StdLibVersion].all.filter(_ >= V4).map(Some(_)).toList :+ None) // (V3, V4), (V4, V5), ..., (Vn, None)
      .foreach {
        case (currentVersion, nextVersion) =>
          val (genesisTxs, setScriptTxs, invoke) = scenario(currentVersion).sample.get
          withDomain(settingsForRide(currentVersion)) { d =>
            d.appendBlock(genesisTxs*)
            d.appendBlock(setScriptTxs*)
            d.appendBlock(invoke)
            d.blockchain.transactionSucceeded(invoke.id.value()) shouldBe true
            nextVersion.foreach { v =>
              val (_, setScriptTxs, _) = scenario(v).sample.get
              (the[RuntimeException] thrownBy d.appendBlock(setScriptTxs*)).getMessage should include("ActivationError")
            }
          }
      }
  }
}
