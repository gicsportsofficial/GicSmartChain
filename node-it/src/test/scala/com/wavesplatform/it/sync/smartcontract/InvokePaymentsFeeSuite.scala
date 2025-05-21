package com.gicsports.it.sync.smartcontract

import com.typesafe.config.Config
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.features.BlockchainFeatures
import com.gicsports.it.NodeConfigs
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync.smartMinFee
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.estimator.v3.ScriptEstimatorV3
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.smart.script.ScriptCompiler

class InvokePaymentsFeeSuite extends BaseTransactionSuite {

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

  private lazy val (caller, callerAddress) = (firstKeyPair, firstAddress)
  private lazy val (dApp, dAppAddress)     = (secondKeyPair, secondAddress)

  val verifier: String = {
    val script = s"""
                    | {-# STDLIB_VERSION 4        #-}
                    | {-# SCRIPT_TYPE ASSET       #-}
                    | {-# CONTENT_TYPE EXPRESSION #-}
                    |
                    | !(sigVerify_32Kb(base58'', base58'', base58'') ||
                    |   sigVerify_32Kb(base58'', base58'', base58'') ||
                    |   sigVerify_32Kb(base58'', base58'', base58'')
                    |  )
                    |
                    """.stripMargin
    ScriptCompiler.compile(script, ScriptEstimatorV3(fixOverflow = true, overhead = false)).explicitGet()._1.bytes().base64
  }

  private def dApp(assetId: String): String =
    ScriptCompiler
      .compile(
        s"""
         | {-# STDLIB_VERSION 4       #-}
         | {-# CONTENT_TYPE   DAPP    #-}
         | {-# SCRIPT_TYPE    ACCOUNT #-}
         |
         | @Callable(i)
         | func default() =
         |   [
         |     ScriptTransfer(i.caller, 1, base58'$assetId'),
         |     Burn(base58'$assetId', 1),
         |     Reissue(base58'$assetId', 1, false)
         |   ]
       """.stripMargin,
        ScriptEstimatorV3(fixOverflow = true, overhead = false)
      )
      .explicitGet()
      ._1
      .bytes()
      .base64

  test(s"fee for asset scripts is not required after activation ${BlockchainFeatures.SynchronousCalls}") {
    val assetId = sender.issue(dApp, script = Some(verifier), waitForTx = true).id
    val asset   = IssuedAsset(ByteStr.decodeBase58(assetId).get)

    sender.transfer(dApp, callerAddress, 100, assetId = Some(assetId), fee = smartMinFee, waitForTx = true)
    sender.setScript(dApp, Some(dApp(assetId)), waitForTx = true)

    val payments = Seq(Payment(1, asset), Payment(1, asset))
    val invokeId = sender.invokeScript(caller, dAppAddress, payment = payments, waitForTx = true)._1.id
    sender.transactionStatus(invokeId).status shouldBe "confirmed"
  }
}
