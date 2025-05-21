package com.gicsports.api.http.requests

import com.gicsports.account.{AddressOrAlias, PublicKey}
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.{Asset, Proofs}
import play.api.libs.json.*

case class TransferRequest(
    version: Option[Byte],
    sender: Option[String],
    senderPublicKey: Option[String],
    recipient: String,
    assetId: Option[Asset],
    amount: Long,
    feeAssetId: Option[Asset],
    fee: Long,
    attachment: Option[ByteStr] = None,
    timestamp: Option[Long] = None,
    signature: Option[ByteStr] = None,
    proofs: Option[Proofs] = None
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, TransferTransaction] =
    for {
      validRecipient <- AddressOrAlias.fromString(recipient)
      validProofs    <- toProofs(signature, proofs)
      tx <- TransferTransaction.create(
        version.getOrElse(1.toByte),
        sender,
        validRecipient,
        assetId.getOrElse(Asset.Waves),
        amount,
        feeAssetId.getOrElse(Asset.Waves),
        fee,
        attachment.getOrElse(ByteStr.empty),
        timestamp.getOrElse(0L),
        validProofs
      )
    } yield tx
}

object TransferRequest {
  implicit val jsonFormat: Format[TransferRequest] = Json.format
}
