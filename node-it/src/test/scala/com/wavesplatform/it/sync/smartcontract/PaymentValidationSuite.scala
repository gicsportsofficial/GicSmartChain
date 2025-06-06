package com.gicsports.it.sync.smartcontract

import com.gicsports.api.http.ApiError.ScriptExecutionError
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.{Base58, EitherExt2}
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync.{issueFee, setScriptFee, smartFee}
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.estimator.v3.ScriptEstimatorV3
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.smart.script.ScriptCompiler

class PaymentValidationSuite extends BaseTransactionSuite {

  test("payment's validation order check") {
    val dApp             = firstKeyPair
    val caller           = secondKeyPair
    val (wrKey, wrValue) = ("key", "value")

    val sourceV4 =
      s"""{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE DAPP #-}
         |{-# SCRIPT_TYPE ACCOUNT #-}
         |
         |@Callable(i)
         |func write() = {
         |  [StringEntry("$wrKey", "$wrValue")]
         |}
      """.stripMargin
    val scriptV4 = ScriptCompiler.compile(sourceV4, ScriptEstimatorV3(fixOverflow = true, overhead = false)).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(scriptV4), setScriptFee, waitForTx = true)

    val scr = ScriptCompiler(
      s"""
         |{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |{-# SCRIPT_TYPE ASSET #-}
         |
         |getStringValue(addressFromString("${dApp.toAddress.toString}").value(), "$wrKey") == "$wrValue"
         """.stripMargin,
      isAssetScript = true,
      ScriptEstimatorV3(fixOverflow = true, overhead = false)
    ).explicitGet()._1.bytes().base64
    val smartAssetId = sender.issue(caller, script = Some(scr), fee = issueFee + smartFee, waitForTx = true).id

    assertApiError(
      sender.invokeScript(
        caller,
        dApp.toAddress.toString,
        func = Some("write"),
        payment = Seq(Payment(1000L, IssuedAsset(ByteStr(Base58.decode(smartAssetId))))),
        fee = issueFee
      )
    ) { err =>
      err.message should include regex "value by key 'key' not found for the address"
      err.id shouldBe ScriptExecutionError.Id
    }

  }
}
