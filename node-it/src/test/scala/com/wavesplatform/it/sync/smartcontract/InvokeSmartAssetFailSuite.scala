package com.gicsports.it.sync.smartcontract
import com.gicsports.api.http.ApiError.ScriptExecutionError
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync.{setScriptFee, _}
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.compiler.Terms.CONST_BOOLEAN
import com.gicsports.lang.v1.estimator.v3.ScriptEstimatorV3
import com.gicsports.state.BinaryDataEntry
import com.gicsports.transaction.TxVersion
import com.gicsports.transaction.smart.script.ScriptCompiler

// because of SC-655 bug
class InvokeSmartAssetFailSuite extends BaseTransactionSuite {
  private lazy val caller = firstKeyPair
  private lazy val dApp   = secondKeyPair

  val dAppText =
    """
      |{-# STDLIB_VERSION 4 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |
      |let bob = addressFromPublicKey(base58'BzFTfc4TB9s25d8b3sfhptj4STZafEM2FNkR5kQ8mJeA')
      |
      |@Callable(i)
      |func some(fail: Boolean) = {
      |  let issue = Issue("asset", "test asset", 500, 2, true)
      |  let assetId = if fail then this.getBinaryValue("assetId") else issue.calculateAssetId()
      |
      |  let result = [
      |    BinaryEntry("bin", i.transactionId),
      |    BooleanEntry("bool", true),
      |    IntegerEntry("int", i.fee),
      |    StringEntry("str", i.caller.toString()),
      |    DeleteEntry("remove")
      |  ]
      |
      |  if fail then {
      |    result ++ [
      |      Reissue(assetId, 10, false)
      |    ]
      |  } else {
      |    result ++ [
      |      issue,
      |      Reissue(assetId, 10, false),
      |      Burn(assetId, 5),
      |      SponsorFee(assetId, 2),
      |      ScriptTransfer(bob, 7, assetId)
      |    ]
      |  }
      |
      |}
    """.stripMargin

  test("extracted funcs") {
    val assetId = ByteStr.decodeBase58(sender.issue(caller, waitForTx = true).id).get
    val data    = List(BinaryDataEntry("assetId", assetId))
    val dataFee = calcDataFee(data, TxVersion.V2)
    sender.putData(dApp, data, fee = dataFee, waitForTx = true)

    val script = ScriptCompiler.compile(dAppText, ScriptEstimatorV3(fixOverflow = true, overhead = false)).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(script), setScriptFee, waitForTx = true)
    assertApiError(
      sender.invokeScript(caller, dApp.toAddress.toString, Some("some"), List(CONST_BOOLEAN(true))),
      AssertiveApiError(ScriptExecutionError.Id, "Error while executing dApp: Asset was issued by other address")
    )
  }
}
