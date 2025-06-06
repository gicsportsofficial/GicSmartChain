package com.gicsports.it.sync.smartcontract

import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.api.SyncHttpApi.*
import com.gicsports.it.api.*
import com.gicsports.it.sync.*
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.compiler.Terms.{CONST_LONG, CONST_STRING}
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.test.*
import com.gicsports.transaction.smart.script.ScriptCompiler
import com.gicsports.transaction.transfer.MassTransferTransaction.Transfer
import org.scalactic.source.Position
import org.scalatest.CancelAfterFailure

class InvokeScriptTransactionStateChangesSuite extends BaseTransactionSuite with CancelAfterFailure {

  private def contract  = firstKeyPair
  private def caller    = secondKeyPair
  private def recipient = thirdKeyPair

  var simpleAsset: String               = ""
  var assetSponsoredByDApp: String      = ""
  var assetSponsoredByRecipient: String = ""
  var initCallerTxs: Long               = 0
  var initDAppTxs: Long                 = 0
  var initRecipientTxs: Long            = 0
  var initCallerStateChanges: Long      = 0
  var initDAppStateChanges: Long        = 0
  var initRecipientStateChanges: Long   = 0

  private lazy val contractAddress: String  = contract.toAddress.toString
  private lazy val recipientAddress: String = recipient.toAddress.toString
  private lazy val callerAddress: String    = caller.toAddress.toString

  test("write") {
    val data = 10

    val invokeTx = sender.validateInvokeScript( // Since BlockV5 broadcasting InvokeTx does not return trace
      caller,
      contractAddress,
      func = Some("write"),
      args = List(CONST_LONG(data)),
      fee = 0.06.waves
    )

    val js = invokeTx._2

    (js \ "trace" \ 0 \ "vars" \ 2 \ "name").as[String] shouldBe "value"
    (js \ "trace" \ 0 \ "vars" \ 2 \ "type").as[String] shouldBe "Int"
    (js \ "trace" \ 0 \ "vars" \ 2 \ "value").as[Int] shouldBe data

    val id = sender.signedBroadcast(invokeTx._1, waitForTx = true).id

    nodes.waitForHeightAriseAndTxPresent(id)

    val txInfo = sender.transactionInfo[TransactionInfo](id)

    sender.waitForHeight(txInfo.height + 1)

    val callerTxs          = sender.transactionsByAddress(callerAddress, 100)
    val dAppTxs            = sender.transactionsByAddress(contractAddress, 100)
    val txStateChanges     = sender.stateChanges(id)
    val callerStateChanges = sender.debugStateChangesByAddress(callerAddress, 100)
    val dAppStateChanges   = sender.debugStateChangesByAddress(contractAddress, 100)

    callerTxs.length shouldBe initCallerTxs + 1
    callerTxs.length shouldBe callerStateChanges.length
    dAppTxs.length shouldBe initDAppTxs + 1
    dAppTxs.length shouldBe dAppStateChanges.length

    txInfoShouldBeEqual(txInfo, txStateChanges)

    val expected = StateChangesDetails(Seq(DataResponse.put("integer", 10, "result")), Seq(), Seq(), Seq(), Seq(), Seq(), None)
    txStateChanges.stateChanges.get shouldBe expected
    callerStateChanges.head.stateChanges.get shouldBe expected
    dAppStateChanges.head.stateChanges.get shouldBe expected
  }

  test("sponsored by dApp") {
    val invokeTx = sender.invokeScript(
      caller,
      contractAddress,
      func = Some("sendAsset"),
      args = List(
        CONST_STRING(recipientAddress).explicitGet(),
        CONST_LONG(10),
        CONST_STRING(simpleAsset).explicitGet()
      ),
      fee = 30,
      feeAssetId = Some(assetSponsoredByDApp),
      waitForTx = true
    )

    val txInfo                = sender.transactionInfo[TransactionInfo](invokeTx._1.id)
    val callerTxs             = sender.transactionsByAddress(callerAddress, 100)
    val dAppTxs               = sender.transactionsByAddress(contractAddress, 100)
    val recipientTxs          = sender.transactionsByAddress(recipientAddress, 100)
    val txStateChanges        = sender.stateChanges(invokeTx._1.id)
    val callerStateChanges    = sender.debugStateChangesByAddress(callerAddress, 100)
    val dAppStateChanges      = sender.debugStateChangesByAddress(contractAddress, 100)
    val recipientStateChanges = sender.debugStateChangesByAddress(recipientAddress, 100)

    callerTxs.length shouldBe initCallerTxs + 2
    callerTxs.length shouldBe callerStateChanges.length
    dAppTxs.length shouldBe initDAppTxs + 2
    dAppTxs.length shouldBe dAppStateChanges.length
    recipientTxs.length shouldBe initRecipientTxs + 1
    recipientTxs.length shouldBe recipientStateChanges.length

    txInfoShouldBeEqual(txInfo, txStateChanges)
    txInfoShouldBeEqual(callerTxs.head, txStateChanges)
    txInfoShouldBeEqual(dAppTxs.head, txStateChanges)
    txInfoShouldBeEqual(recipientTxs.head, txStateChanges)
    txInfoShouldBeEqual(txInfo, callerStateChanges.head)
    txInfoShouldBeEqual(txInfo, dAppStateChanges.head)
    txInfoShouldBeEqual(txInfo, recipientStateChanges.head)

    val expected = StateChangesDetails(Seq(), Seq(TransfersInfoResponse(recipientAddress, Some(simpleAsset), 10)), Seq(), Seq(), Seq(), Seq(), None)
    txStateChanges.stateChanges.get shouldBe expected
    callerStateChanges.head.stateChanges.get shouldBe expected
    dAppStateChanges.head.stateChanges.get shouldBe expected
    recipientStateChanges.head.stateChanges.get shouldBe expected
  }

  test("sponsored by recipient") {
    val invokeTx = sender.invokeScript(
      caller,
      contractAddress,
      func = Some("writeAndSendWaves"),
      args = List(CONST_LONG(7), CONST_STRING(callerAddress).explicitGet(), CONST_LONG(10)),
      fee = 25,
      feeAssetId = Some(assetSponsoredByRecipient),
      waitForTx = true
    )

    val txInfo                = sender.transactionInfo[TransactionInfo](invokeTx._1.id)
    val callerTxs             = sender.transactionsByAddress(callerAddress, 100)
    val dAppTxs               = sender.transactionsByAddress(contractAddress, 100)
    val recipientTxs          = sender.transactionsByAddress(recipientAddress, 100)
    val txStateChanges        = sender.stateChanges(invokeTx._1.id)
    val callerStateChanges    = sender.debugStateChangesByAddress(callerAddress, 100)
    val dAppStateChanges      = sender.debugStateChangesByAddress(contractAddress, 100)
    val recipientStateChanges = sender.debugStateChangesByAddress(recipientAddress, 100)

    callerTxs.length shouldBe initCallerTxs + 3
    callerTxs.length shouldBe callerStateChanges.length
    dAppTxs.length shouldBe initDAppTxs + 3
    dAppTxs.length shouldBe dAppStateChanges.length
    recipientTxs.length shouldBe initRecipientTxs + 2
    recipientTxs.length shouldBe recipientStateChanges.length

    txInfoShouldBeEqual(txInfo, txStateChanges)
    txInfoShouldBeEqual(callerTxs.head, txStateChanges)
    txInfoShouldBeEqual(dAppTxs.head, txStateChanges)
    txInfoShouldBeEqual(recipientTxs.head, txStateChanges)
    txInfoShouldBeEqual(txInfo, callerStateChanges.head)
    txInfoShouldBeEqual(txInfo, dAppStateChanges.head)
    txInfoShouldBeEqual(txInfo, recipientStateChanges.head)

    val expected = StateChangesDetails(
      Seq(DataResponse.put("integer", 7, "result")),
      Seq(TransfersInfoResponse(callerAddress, None, 10)),
      Seq(),
      Seq(),
      Seq(),
      Seq(),
      None
    )
    txStateChanges.stateChanges.get shouldBe expected
    callerStateChanges.head.stateChanges.get shouldBe expected
    dAppStateChanges.head.stateChanges.get shouldBe expected
  }

  test("None on wrong tx type") {
    val tx = nodes.head.transfer(
      caller,
      recipientAddress,
      1.waves,
      waitForTx = true
    )

    nodes.head.stateChanges(tx.id).stateChanges shouldBe None
  }

  test("state changes order") {
    val script = ScriptCompiler
      .compile(
        s"""
           |{-# STDLIB_VERSION 4 #-}
           |{-# CONTENT_TYPE DAPP #-}
           |{-# SCRIPT_TYPE ACCOUNT #-}

           |@Callable(i)
           |func order1() = {
           |  [
           |    ScriptTransfer(i.caller, 1, unit),
           |    IntegerEntry("a", 1),
           |    StringEntry("b", "a"),
           |    BinaryEntry("c", base58'a'),
           |    BooleanEntry("d", true),
           |    DeleteEntry("e"),
           |    ScriptTransfer(i.caller, 2, unit),
           |    BooleanEntry("d", false),
           |    DeleteEntry("e"),
           |    StringEntry("f", "a")
           |  ]
           |}
           |
           |@Callable(i)
           |func order2() = {
           |  [
           |    SponsorFee(base58'$assetSponsoredByDApp', 950),
           |    Issue("asset #1", "", 100, 8, true, unit, 0),
           |    Reissue(base58'$simpleAsset', 1, true),
           |    Burn(base58'$simpleAsset', 3),
           |    Reissue(base58'$assetSponsoredByDApp', 2, true),
           |    SponsorFee(base58'$simpleAsset', 500),
           |    Burn(base58'$assetSponsoredByDApp', 4),
           |    Issue("asset #2", "", 100, 8, true, unit, 0),
           |    SponsorFee(base58'$assetSponsoredByDApp', 1000),
           |    Reissue(base58'$simpleAsset', 3, true)
           |  ]
           |}
      """.stripMargin,
        ScriptEstimatorV2
      )
      .explicitGet()
      ._1
      .bytes()
      .base64
    sender.setScript(contract, Some(script), setScriptFee + 0.4.waves, waitForTx = true)

    val invokeTx1 = sender.invokeScript(
      caller,
      contractAddress,
      func = Some("order1"),
      args = List.empty,
      fee = 2000.06.waves,
      waitForTx = true
    )

    val expectedDataResponses = Seq(
      DataResponse.put("integer", 1, "a"),
      DataResponse.put("string", "a", "b"),
      DataResponse.put("binary", "base64:IQ==", "c"),
      DataResponse.put("boolean", true, "d"),
      DataResponse.delete("e"),
      DataResponse.put("boolean", false, "d"),
      DataResponse.delete("e"),
      DataResponse.put("string", "a", "f")
    )
    val expectedTransferResponses = Seq(TransfersInfoResponse(callerAddress, None, 1), TransfersInfoResponse(callerAddress, None, 2))

    val idStateChanges1      = sender.stateChanges(invokeTx1._1.id).stateChanges
    val addressStateChanges1 = sender.debugStateChangesByAddress(callerAddress, 1).head.stateChanges

    Seq(idStateChanges1, addressStateChanges1).foreach { actualStateChanges =>
      actualStateChanges.get.data shouldBe expectedDataResponses
      actualStateChanges.get.transfers shouldBe expectedTransferResponses
    }

    val invokeTx2 = sender.invokeScript(
      caller,
      contractAddress,
      func = Some("order2"),
      args = List.empty,
      fee = 2000.06.waves,
      waitForTx = true
    )

    val expectedSponsorFeeResponses = Seq(
      SponsorFeeResponse(assetSponsoredByDApp, Some(950)),
      SponsorFeeResponse(simpleAsset, Some(500)),
      SponsorFeeResponse(assetSponsoredByDApp, Some(1000))
    )

    val expectedIssueNames = Seq("asset #1", "asset #2")
    val expectedReissueResponses = Seq(
      ReissueInfoResponse(simpleAsset, isReissuable = true, 1),
      ReissueInfoResponse(assetSponsoredByDApp, isReissuable = true, 2),
      ReissueInfoResponse(simpleAsset, isReissuable = true, 3)
    )
    val expectedBurnResponses = Seq(BurnInfoResponse(simpleAsset, 3), BurnInfoResponse(assetSponsoredByDApp, 4))

    val idStateChanges2      = sender.stateChanges(invokeTx2._1.id).stateChanges
    val addressStateChanges2 = sender.debugStateChangesByAddress(callerAddress, 1).head.stateChanges

    Seq(idStateChanges2, addressStateChanges2).foreach { actualStateChanges =>
      actualStateChanges.get.sponsorFees shouldBe expectedSponsorFeeResponses
      actualStateChanges.get.reissues shouldBe expectedReissueResponses
      actualStateChanges.get.burns shouldBe expectedBurnResponses
      actualStateChanges.get.issues.map(_.name) shouldBe expectedIssueNames
      actualStateChanges.get.issues.foreach { actualIssue =>
        actualIssue.quantity shouldBe 100
        actualIssue.decimals shouldBe 8
        actualIssue.isReissuable shouldBe true
      }
    }

  }

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    simpleAsset = sender.issue(contract, "simple", "", 9000, 0).id
    assetSponsoredByDApp = sender.issue(contract, "DApp asset", "", 9000, 0).id
    assetSponsoredByRecipient = sender.issue(recipient, "Recipient asset", "", 9000, 0, waitForTx = true).id
    sender.massTransfer(contract, List(Transfer(callerAddress, 3000), Transfer(recipientAddress, 3000)), 0.4.waves, assetId = Some(simpleAsset))
    sender.massTransfer(
      contract,
      List(Transfer(callerAddress, 3000), Transfer(recipientAddress, 3000)),
      0.4.waves,
      assetId = Some(assetSponsoredByDApp)
    )
    sender.massTransfer(
      recipient,
      List(Transfer(callerAddress, 3000), Transfer(contractAddress, 3000)),
      0.4.waves,
      assetId = Some(assetSponsoredByRecipient)
    )
    sender.sponsorAsset(contract, assetSponsoredByDApp, 1, fee = sponsorReducedFee + smartFee)
    sender.sponsorAsset(recipient, assetSponsoredByRecipient, 5, fee = sponsorReducedFee + smartFee)

    val script = ScriptCompiler
      .compile(
        """
          |{-# STDLIB_VERSION 3 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |{-# SCRIPT_TYPE ACCOUNT #-}
          |
          |@Callable(i)
          |func write(value: Int) = {
          |    WriteSet([DataEntry("result", value)])
          |}
          |
          |@Callable(i)
          |func sendAsset(recipient: String, amount: Int, assetId: String) = {
          |    TransferSet([ScriptTransfer(Address(recipient.fromBase58String()), amount, assetId.fromBase58String())])
          |}
          |
          |@Callable(i)
          |func writeAndSendWaves(value: Int, recipient: String, amount: Int) = {
          |    ScriptResult(
          |        WriteSet([DataEntry("result", value)]),
          |        TransferSet([ScriptTransfer(Address(recipient.fromBase58String()), amount, unit)])
          |    )
          |}
      """.stripMargin,
        ScriptEstimatorV2
      )
      .explicitGet()
      ._1
      .bytes()
      .base64
    sender.setScript(contract, Some(script), setScriptFee, waitForTx = true)
    nodes.waitForEmptyUtx()
    nodes.waitForHeightArise()

    initCallerTxs = sender.transactionsByAddress(callerAddress, 100).length
    initDAppTxs = sender.transactionsByAddress(contractAddress, 100).length
    initRecipientTxs = sender.transactionsByAddress(recipientAddress, 100).length
    initCallerStateChanges = sender.debugStateChangesByAddress(callerAddress, 100).length
    initDAppStateChanges = sender.debugStateChangesByAddress(contractAddress, 100).length
    initRecipientStateChanges = sender.debugStateChangesByAddress(recipientAddress, 100).length
    initCallerTxs shouldBe initCallerStateChanges
    initDAppTxs shouldBe initDAppStateChanges
    initRecipientTxs shouldBe initRecipientStateChanges
  }

  def txInfoShouldBeEqual(info: TransactionInfo, stateChanges: StateChanges)(implicit pos: Position): Unit = {
    info._type shouldBe stateChanges._type
    info.id shouldBe stateChanges.id
    info.fee shouldBe stateChanges.fee
    info.timestamp shouldBe stateChanges.timestamp
    info.sender shouldBe stateChanges.sender
    info.height shouldBe stateChanges.height
    info.minSponsoredAssetFee shouldBe stateChanges.minSponsoredAssetFee
    info.script shouldBe stateChanges.script
  }
}
