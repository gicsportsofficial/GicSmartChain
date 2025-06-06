package com.gicsports.it.sync.grpc

import com.google.protobuf.ByteString
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.api.SyncGrpcApi.*
import com.gicsports.it.sync.*
import com.gicsports.protobuf.transaction.MassTransferTransactionData.Transfer
import com.gicsports.protobuf.transaction.{PBTransactions, Recipient}
import com.gicsports.transaction.transfer.MassTransferTransaction.MaxTransferCount
import com.gicsports.transaction.transfer.TransferTransaction.MaxAttachmentSize
import io.grpc.Status.Code

class MassTransferTransactionGrpcSuite extends GrpcBaseTransactionSuite {

  test("asset mass transfer changes asset balances and sender's.GIC balance is decreased by fee.") {
    for (v <- massTransferTxSupportedVersions) {
      val firstBalance  = sender.wavesBalance(firstAddress)
      val secondBalance = sender.wavesBalance(secondAddress)
      val attachment    = ByteString.copyFrom("mass transfer description".getBytes("UTF-8"))

      val transfers = List(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), transferAmount))
      val assetId = PBTransactions
        .vanilla(
          sender.broadcastIssue(firstAcc, "name", issueAmount, 8, reissuable = false, issueFee, waitForTx = true),
          true
        )
        .explicitGet()
        .id()
        .toString
      sender.waitForTransaction(assetId)

      val massTransferTransactionFee = calcMassTransferFee(transfers.size)
      sender.broadcastMassTransfer(firstAcc, Some(assetId), transfers, attachment, massTransferTransactionFee, waitForTx = true)

      val firstBalanceAfter  = sender.wavesBalance(firstAddress)
      val secondBalanceAfter = sender.wavesBalance(secondAddress)

      firstBalanceAfter.regular shouldBe firstBalance.regular - issueFee - massTransferTransactionFee
      firstBalanceAfter.effective shouldBe firstBalance.effective - issueFee - massTransferTransactionFee
      sender.assetsBalance(firstAddress, Seq(assetId)).getOrElse(assetId, 0L) shouldBe issueAmount - transferAmount
      secondBalanceAfter.regular shouldBe secondBalance.regular
      secondBalanceAfter.effective shouldBe secondBalance.effective
      sender.assetsBalance(secondAddress, Seq(assetId)).getOrElse(assetId, 0L) shouldBe transferAmount
    }
  }

  test("waves mass transfer changes waves balances") {
    val firstBalance  = sender.wavesBalance(firstAddress)
    val secondBalance = sender.wavesBalance(secondAddress)
    val thirdBalance  = sender.wavesBalance(thirdAddress)
    val transfers = List(
      Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), transferAmount),
      Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(thirdAddress))), 2 * transferAmount)
    )

    val massTransferTransactionFee = calcMassTransferFee(transfers.size)
    sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee, waitForTx = true)

    val firstBalanceAfter  = sender.wavesBalance(firstAddress)
    val secondBalanceAfter = sender.wavesBalance(secondAddress)
    val thirdBalanceAfter  = sender.wavesBalance(thirdAddress)

    firstBalanceAfter.regular shouldBe firstBalance.regular - massTransferTransactionFee - 3 * transferAmount
    firstBalanceAfter.effective shouldBe firstBalance.effective - massTransferTransactionFee - 3 * transferAmount
    secondBalanceAfter.regular shouldBe secondBalance.regular + transferAmount
    secondBalanceAfter.effective shouldBe secondBalance.effective + transferAmount
    thirdBalanceAfter.regular shouldBe thirdBalance.regular + 2 * transferAmount
    thirdBalanceAfter.effective shouldBe thirdBalance.effective + 2 * transferAmount
  }

  test("can not make mass transfer without having enough waves") {
    val firstBalance  = sender.wavesBalance(firstAddress)
    val secondBalance = sender.wavesBalance(secondAddress)
    val transfers = List(
      Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), firstBalance.regular / 2),
      Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(thirdAddress))), firstBalance.regular / 2)
    )

    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = calcMassTransferFee(transfers.size)),
      "Attempt to transfer unavailable funds",
      Code.INVALID_ARGUMENT
    )

    nodes.foreach(n => n.waitForHeight(n.height + 1))
    sender.wavesBalance(firstAddress) shouldBe firstBalance
    sender.wavesBalance(secondAddress) shouldBe secondBalance
  }

  test("cannot make mass transfer when fee less then minimal ") {
    val firstBalance               = sender.wavesBalance(firstAddress)
    val secondBalance              = sender.wavesBalance(secondAddress)
    val transfers                  = List(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), transferAmount))
    val massTransferTransactionFee = calcMassTransferFee(transfers.size)

    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee - 1),
      s"does not exceed minimal value of $massTransferTransactionFee GIC",
      Code.INVALID_ARGUMENT
    )

    nodes.foreach(n => n.waitForHeight(n.height + 1))
    sender.wavesBalance(firstAddress) shouldBe firstBalance
    sender.wavesBalance(secondAddress) shouldBe secondBalance
  }

  test("cannot make mass transfer without having enough of effective balance") {
    val firstBalance  = sender.wavesBalance(firstAddress)
    val secondBalance = sender.wavesBalance(secondAddress)
    val transfers     = List(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), firstBalance.regular - 2 * minFee))
    val massTransferTransactionFee = calcMassTransferFee(transfers.size)

    sender.broadcastLease(firstAcc, Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress)), leasingAmount, minFee, waitForTx = true)

    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee),
      "Attempt to transfer unavailable funds",
      Code.INVALID_ARGUMENT
    )
    nodes.foreach(n => n.waitForHeight(n.height + 1))
    sender.wavesBalance(firstAddress).regular shouldBe firstBalance.regular - minFee
    sender.wavesBalance(firstAddress).effective shouldBe firstBalance.effective - minFee - leasingAmount
    sender.wavesBalance(secondAddress).regular shouldBe secondBalance.regular
    sender.wavesBalance(secondAddress).effective shouldBe secondBalance.effective + leasingAmount
  }

  test("cannot broadcast invalid mass transfer tx") {
    val firstBalance    = sender.wavesBalance(firstAddress)
    val secondBalance   = sender.wavesBalance(secondAddress)
    val defaultTransfer = List(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), transferAmount))

    val negativeTransfer = List(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), -1))
    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = negativeTransfer, fee = calcMassTransferFee(negativeTransfer.size)),
      "negative amount: -1 of asset",
      Code.INVALID_ARGUMENT
    )

    val tooManyTransfers = List.fill(MaxTransferCount + 1)(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(secondAddress))), 1))
    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = tooManyTransfers, fee = calcMassTransferFee(MaxTransferCount + 1)),
      s"Number of transfers ${MaxTransferCount + 1} is greater than 100",
      Code.INVALID_ARGUMENT
    )

    val tooBigAttachment = ByteString.copyFrom(("a" * (MaxAttachmentSize + 1)).getBytes("UTF-8"))
    assertGrpcError(
      sender.broadcastMassTransfer(firstAcc, transfers = defaultTransfer, attachment = tooBigAttachment, fee = calcMassTransferFee(1)),
      s"Invalid attachment. Length ${MaxAttachmentSize + 1} bytes exceeds maximum of $MaxAttachmentSize bytes.",
      Code.INVALID_ARGUMENT
    )

    sender.wavesBalance(firstAddress) shouldBe firstBalance
    sender.wavesBalance(secondAddress) shouldBe secondBalance
  }

  test("huge transactions are allowed") {
    val firstBalance  = sender.wavesBalance(firstAddress)
    val fee           = calcMassTransferFee(MaxTransferCount)
    val amount        = (firstBalance.available - fee) / MaxTransferCount
    val maxAttachment = ByteString.copyFrom(("a" * MaxAttachmentSize).getBytes("UTF-8"))

    val transfers = List.fill(MaxTransferCount)(Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(firstAddress))), amount))
    sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = fee, attachment = maxAttachment, waitForTx = true)

    sender.wavesBalance(firstAddress).regular shouldBe firstBalance.regular - fee
    sender.wavesBalance(firstAddress).effective shouldBe firstBalance.effective - fee
  }

  test("able to mass transfer to alias") {
    val firstBalance  = sender.wavesBalance(firstAddress)
    val secondBalance = sender.wavesBalance(secondAddress)

    val alias = "masstest_alias"

    sender.broadcastCreateAlias(secondAcc, alias, aliasFeeAmount, waitForTx = true)

    val transfers =
      List(
        Transfer(Some(Recipient.of(Recipient.Recipient.PublicKeyHash(firstAddress))), transferAmount),
        Transfer(Some(Recipient.of(Recipient.Recipient.Alias(alias))), transferAmount)
      )

    val massTransferTransactionFee = calcMassTransferFee(transfers.size)
    sender.broadcastMassTransfer(firstAcc, transfers = transfers, fee = massTransferTransactionFee, waitForTx = true)

    sender.wavesBalance(firstAddress).regular shouldBe firstBalance.regular - massTransferTransactionFee - transferAmount
    sender.wavesBalance(firstAddress).effective shouldBe firstBalance.effective - massTransferTransactionFee - transferAmount
    sender.wavesBalance(secondAddress).regular shouldBe secondBalance.regular + transferAmount - aliasFeeAmount
    sender.wavesBalance(secondAddress).effective shouldBe secondBalance.effective + transferAmount - aliasFeeAmount
  }
}
