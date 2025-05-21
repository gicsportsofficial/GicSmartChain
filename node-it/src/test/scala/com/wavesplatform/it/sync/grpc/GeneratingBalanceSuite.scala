package com.gicsports.it.sync.grpc

import com.google.protobuf.ByteString
import com.gicsports.account.KeyPair
import com.gicsports.it.api.SyncGrpcApi._
import com.gicsports.it.sync.minFee
import com.gicsports.protobuf.transaction.Recipient

class GeneratingBalanceSuite extends GrpcBaseTransactionSuite {

  test("Generating balance should be correct") {
    val amount = 1000000000L

    val senderAddress = ByteString.copyFrom(sender.keyPair.toAddress.bytes)

    val recipient        = KeyPair("recipient".getBytes)
    val recipientAddress = ByteString.copyFrom(recipient.toAddress.bytes)

    val initialBalance = sender.wavesBalance(senderAddress)

    sender.broadcastTransfer(sender.keyPair, Recipient().withPublicKeyHash(recipientAddress), amount, minFee, 2, waitForTx = true)

    val afterTransferBalance = sender.wavesBalance(senderAddress)

    sender.broadcastTransfer(recipient, Recipient().withPublicKeyHash(senderAddress), amount - minFee, minFee, 2, waitForTx = true)

    val finalBalance = sender.wavesBalance(senderAddress)

    assert(initialBalance.generating <= initialBalance.effective, "initial incorrect")
    assert(afterTransferBalance.generating <= afterTransferBalance.effective, "after transfer incorrect")
    assert(finalBalance.generating <= finalBalance.effective, "final incorrect")
  }
}
