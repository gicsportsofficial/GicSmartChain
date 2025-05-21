package com.gicsports.it.sync.smartcontract

import com.typesafe.config.Config
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.features.BlockchainFeatures
import com.gicsports.it.NodeConfigs
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync._
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.estimator.v3.ScriptEstimatorV3
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.smart.script.ScriptCompiler

class InvokePaymentsAvailabilitySuite extends BaseTransactionSuite {

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .overrideBase(
        _.preactivatedFeatures(
          (BlockchainFeatures.Ride4DApps.id, 0),
          (BlockchainFeatures.BlockV5.id, 0),
          (BlockchainFeatures.SynchronousCalls.id, 0)
        )
      )
      .withDefault(1)
      .buildNonConflicting()

  private lazy val (caller, callerAddress)           = (firstKeyPair, firstAddress)
  private lazy val (callingDApp, callingDAppAddress) = (secondKeyPair, secondAddress)
  private lazy val (proxyDApp, proxyDAppAddress)     = (thirdKeyPair, thirdAddress)

  private def syncDApp(dApp: String) =
    ScriptCompiler(
      s"""
       |{-# STDLIB_VERSION 5 #-}
       |{-# CONTENT_TYPE DAPP #-}
       |{-# SCRIPT_TYPE ACCOUNT #-}
       |
       | let dApp2 = Address(base58'$dApp')
       |
       | @Callable(inv)
       | func default() = {
       |    let pmt = inv.payments[0]
       |    strict invokeV4 = dApp2.invoke("default", nil, [AttachedPayment(pmt.assetId, pmt.amount)])
       |    [
       |       IntegerEntry("balance_self", this.assetBalance(pmt.assetId.value())),
       |       IntegerEntry("balance_calling_dApp", dApp2.assetBalance(pmt.assetId.value()))
       |    ]
       | }
       |
         """.stripMargin,
      isAssetScript = false,
      ScriptEstimatorV3(fixOverflow = true, overhead = false)
    ).explicitGet()._1.bytes().base64

  private val dApp =
    ScriptCompiler(
      s"""
       | {-# STDLIB_VERSION 5       #-}
       | {-# CONTENT_TYPE   DAPP    #-}
       | {-# SCRIPT_TYPE    ACCOUNT #-}
       |
       | @Callable(inv)
       | func default() = {
       |   let pmtAssetId = inv.payments[0].assetId.value()
       |   [
       |     IntegerEntry("balance_self", this.assetBalance(pmtAssetId)),
       |     IntegerEntry("balance_caller", inv.caller.assetBalance(pmtAssetId))
       |   ]
       | }
     """.stripMargin,
      isAssetScript = false,
      ScriptEstimatorV3(fixOverflow = true, overhead = false)
    ).explicitGet()._1.bytes().base64

  private val paymentAmount = 12345
  private val issueAmount   = 1000 * 1000

  test("payments availability in sync call") {
    val assetId = sender.issue(caller, quantity = issueAmount, waitForTx = true).id
    val asset   = IssuedAsset(ByteStr.decodeBase58(assetId).get)
    sender.setScript(proxyDApp, Some(syncDApp(callingDAppAddress)), waitForTx = true)

    sender.setScript(callingDApp, Some(dApp), waitForTx = true)

    val callerStartBalance      = sender.assetBalance(callerAddress, assetId).balance
    val proxyStartBalance       = sender.assetBalance(proxyDAppAddress, assetId).balance
    val callingDAppStartBalance = sender.assetBalance(callingDAppAddress, assetId).balance

    sender.invokeScript(caller, proxyDAppAddress, payment = Seq(Payment(paymentAmount, asset)), fee = invokeFee, waitForTx = true)
    sender.assetBalance(callerAddress, assetId).balance shouldBe callerStartBalance - paymentAmount

    val expectingProxyDAppBalance = 0
    List[Any](
      sender.assetBalance(proxyDAppAddress, assetId).balance,
      sender.getData(proxyDAppAddress, "balance_self").head.value
    ).foreach(_ shouldBe proxyStartBalance + expectingProxyDAppBalance)

    val expectingCallingDAppBalance = paymentAmount
    List[Any](
      sender.assetBalance(callingDAppAddress, assetId).balance,
      sender.getData(proxyDAppAddress, "balance_calling_dApp").head.value
    ).foreach(_ shouldBe callingDAppStartBalance + expectingCallingDAppBalance)

    val expectingCallingDAppBalanceInsideCallingDApp = paymentAmount
    val expectingProxyDAppBalanceInsideCallingDApp   = 0
    sender.getData(callingDAppAddress, "balance_self").head.value shouldBe callingDAppStartBalance + expectingCallingDAppBalanceInsideCallingDApp
    sender.getData(callingDAppAddress, "balance_caller").head.value shouldBe proxyStartBalance + expectingProxyDAppBalanceInsideCallingDApp
  }
}
