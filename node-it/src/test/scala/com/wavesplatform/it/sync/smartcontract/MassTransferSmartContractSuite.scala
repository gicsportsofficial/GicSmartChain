package com.gicsports.it.sync.smartcontract

import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.{Base58, EitherExt2}
import com.gicsports.crypto
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync._
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.Proofs
import com.gicsports.transaction.smart.script.ScriptCompiler
import com.gicsports.transaction.transfer.MassTransferTransaction.Transfer
import com.gicsports.transaction.transfer._
import org.scalatest.CancelAfterFailure

import scala.concurrent.duration._

/*
Scenario:
every month a foundation makes payments from two MassTransactions(type == 11):
1) 80% to users
2) 10% as tax and 10% to bank go after 30sec of payment from step 1)
 */

class MassTransferSmartContractSuite extends BaseTransactionSuite with CancelAfterFailure {
  private lazy val fourthAddress: String = notMiner.createKeyPair().toAddress.toString

  test("airdrop emulation via MassTransfer") {
    val scriptText = s"""
       |{-# STDLIB_VERSION 2 #-}
       |match tx {
       |  case ttx: MassTransferTransaction =>
       |    let commonAmount = (ttx.transfers[0].amount + ttx.transfers[1].amount)
       |    let totalAmountToUsers = commonAmount == 8000000000
       |    let totalAmountToGov = commonAmount == 2000000000
       |    let massTxSize = size(ttx.transfers) == 2
       |
       |    let accountPK = base58'${notMiner.publicKey}'
       |    let accSig = sigVerify(ttx.bodyBytes,ttx.proofs[0],accountPK)
       |
       |    let txToUsers = (massTxSize && totalAmountToUsers)
       |
       |    let mTx = transactionById(ttx.proofs[1])
       |
       |    if (txToUsers && accSig) then true
       |    else
       |    if(isDefined(mTx)) then
       |        match extract(mTx) {
       |          case mt2: MassTransferTransaction =>
       |            let txToGov = (massTxSize && totalAmountToGov)
       |            let txToGovComplete = (ttx.timestamp > mt2.timestamp + 30000) && sigVerify(mt2.bodyBytes,mt2.proofs[0], accountPK)
       |            txToGovComplete && accSig && txToGov
       |          case _ => false
       |        }
       |    else false
       |case _ => false
       |}
       |""".stripMargin

    // set script
    val script = ScriptCompiler(scriptText, isAssetScript = false, ScriptEstimatorV2).explicitGet()._1.bytes().base64
    notMiner.setScript(notMiner.keyPair, Some(script), setScriptFee, waitForTx = true).id

    notMiner.addressScriptInfo(notMiner.address).scriptText.isEmpty shouldBe false

    //save time
    val currTime = System.currentTimeMillis()

    //make transfer to users
    val transfers =
      MassTransferTransaction
        .parseTransfersList(List(Transfer(thirdAddress, 4 * tokenTransferAmount), Transfer(secondAddress, 4 * tokenTransferAmount)))
        .explicitGet()

    val unsigned =
      MassTransferTransaction
        .create(1.toByte, notMiner.publicKey, Waves, transfers, calcMassTransferFee(2) + smartFee, currTime, ByteStr.empty, Proofs.empty)
        .explicitGet()

    val accountSig = crypto.sign(notMiner.keyPair.privateKey, unsigned.bodyBytes())
    val signed     = unsigned.copy(1.toByte, proofs = Proofs(Seq(accountSig)))
    val toUsersID  = notMiner.signedBroadcast(signed.json(), waitForTx = true).id

    //make transfer with incorrect time
    val heightBefore = notMiner.height

    val transfersToGov =
      MassTransferTransaction.parseTransfersList(List(Transfer(firstAddress, tokenTransferAmount), Transfer(fourthAddress, tokenTransferAmount))).explicitGet()

    val unsignedToGov =
      MassTransferTransaction
        .create(1.toByte, notMiner.publicKey, Waves, transfersToGov, calcMassTransferFee(2) + smartFee, currTime, ByteStr.empty, Proofs.empty)
        .explicitGet()
    val accountSigToGovFail = crypto.sign(notMiner.keyPair.privateKey, unsignedToGov.bodyBytes())
    val signedToGovFail     = unsignedToGov.copy(1.toByte, proofs = Proofs(Seq(accountSigToGovFail)))

    assertBadRequestAndResponse(
      notMiner.signedBroadcast(signedToGovFail.json()),
      "Transaction is not allowed by account-script"
    )

    //make correct transfer to government after some time
    notMiner.waitForHeight(heightBefore + 10, 5.minutes)

    val unsignedToGovSecond =
      MassTransferTransaction
        .create(
          1.toByte,
          notMiner.publicKey,
          Waves,
          transfersToGov,
          calcMassTransferFee(2) + smartFee,
          System.currentTimeMillis(),
          ByteStr.empty,
          Proofs.empty
        )
        .explicitGet()

    val accountSigToGov = crypto.sign(notMiner.keyPair.privateKey, unsignedToGovSecond.bodyBytes())
    val signedToGovGood = unsignedToGovSecond.copy(1.toByte, proofs = Proofs(Seq(accountSigToGov, ByteStr(Base58.tryDecodeWithLimit(toUsersID).get))))
    notMiner.signedBroadcast(signedToGovGood.json(), waitForTx = true).id
  }
}
