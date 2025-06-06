package com.gicsports.it.sync.grpc

import com.google.protobuf.ByteString
import com.gicsports.common.utils.{Base58, EitherExt2}
import com.gicsports.it.api.SyncGrpcApi._
import com.gicsports.it.sync._
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.protobuf.Amount
import com.gicsports.protobuf.transaction.{PBTransactions, Recipient}
import com.gicsports.transaction.smart.script.ScriptCompiler
import io.grpc.Status.Code

class InvokeScriptErrorMsgGrpcSuite extends GrpcBaseTransactionSuite {
  private val (contract, contractAddress) = (firstAcc, firstAddress)
  private val caller                      = secondAcc

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    val scriptText =
      """
        |{-# STDLIB_VERSION 3 #-}
        |{-# CONTENT_TYPE DAPP #-}
        |
        |@Callable(inv)
        |func default() = {
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
    val contractScript = ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()._1
    sender.setScript(contract, Right(Some(contractScript)), setScriptFee, waitForTx = true)

    sender.setScript(caller, Right(Some(script)), setScriptFee, waitForTx = true)
  }

  test("cannot invoke script without having enough fee; error message is informative") {
    val asset1 = PBTransactions
      .vanilla(
        sender.broadcastIssue(
          caller,
          "ScriptedAsset",
          someAssetAmount,
          decimals = 0,
          reissuable = true,
          fee = issueFee + smartFee,
          script = Right(Some(script)),
          waitForTx = true
        ),
        unsafe = false
      )
      .explicitGet()
      .id()
      .toString

    val payments = Seq(Amount.of(ByteString.copyFrom(Base58.decode(asset1)), 10))
    assertGrpcError(
      sender.broadcastInvokeScript(
        caller,
        Recipient().withPublicKeyHash(contractAddress),
        None,
        payments = payments,
        fee = 1000
      ),
      "Transaction sent from smart account. Requires 4000000 extra fee. Transaction involves 1 scripted assets",
      Code.INVALID_ARGUMENT
    )

    assertGrpcError(
      sender.broadcastInvokeScript(
        caller,
        Recipient().withPublicKeyHash(contractAddress),
        None,
        payments = payments,
        fee = 14000000
      ),
    "Fee in GIC for InvokeScriptTransaction .* with 12 total scripts invoked does not exceed minimal value"
    )
  }

}
