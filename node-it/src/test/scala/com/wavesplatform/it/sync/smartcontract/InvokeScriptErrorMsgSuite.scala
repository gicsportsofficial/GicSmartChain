package com.gicsports.it.sync.smartcontract

import com.gicsports.api.http.ApiError.ScriptExecutionError
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync._
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.test._
import com.gicsports.transaction.Asset
import com.gicsports.transaction.smart.InvokeScriptTransaction
import com.gicsports.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeScriptErrorMsgSuite extends BaseTransactionSuite with CancelAfterFailure {
  private def contract = firstKeyPair
  private def caller   = secondKeyPair

  private lazy val contractAddress: String = contract.toAddress.toString

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    sender.transfer(sender.keyPair, recipient = contractAddress, assetId = None, amount = 5.waves, fee = minFee, waitForTx = true).id
    sender.transfer(sender.keyPair, recipient = contractAddress, assetId = None, amount = 5.waves, fee = minFee, waitForTx = true).id

    val scriptText =
      """
        |{-# STDLIB_VERSION 3 #-}
        |{-# CONTENT_TYPE DAPP #-}
        |
        |@Callable(inv)
        |func f() = {
        | let pmt = inv.payment.extract()
        | TransferSet([ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId),
        | ScriptTransfer(inv.caller, 1, pmt.assetId)])
        |}
        |""".stripMargin
    val script = ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()._1.bytes().base64
    sender.setScript(contract, Some(script), setScriptFee, waitForTx = true).id

    sender.setScript(caller, Some(scriptBase64), setScriptFee, waitForTx = true).id
  }

  test("error message is informative") {
    val asset1 = sender
      .issue(
        caller,
        "MyAsset1",
        "Test Asset #1",
        someAssetAmount,
        0,
        fee = issueFee + 4000000,
        script = Some(scriptBase64),
        waitForTx = true
      )
      .id

    assertBadRequestAndMessage(
      sender.invokeScript(
        caller,
        contractAddress,
        Some("f"),
        payment = Seq(
          InvokeScriptTransaction.Payment(10, Asset.fromString(Some(asset1)))
        ),
        fee = 1000
      ),
      "State check failed. Reason: Transaction sent from smart account. Requires 4000000 extra fee. Transaction involves 1 scripted assets." +
        " Requires 4000000 extra fee. Fee for InvokeScriptTransaction (1000 in GIC) does not exceed minimal value of 14000000 GIC."
    )

    assertApiError(
      sender
        .invokeScript(
          caller,
          contractAddress,
          Some("f"),
          payment = Seq(
            InvokeScriptTransaction.Payment(10, Asset.fromString(Some(asset1)))
          ),
          fee = 14000000
        ),
      AssertiveApiError(
        ScriptExecutionError.Id,
        "Error while executing dApp: Fee in GIC for InvokeScriptTransaction (14000000 in GIC) with 12 total scripts invoked does not exceed minimal value of 54000000 GIC."
      )
    )
  }
}
