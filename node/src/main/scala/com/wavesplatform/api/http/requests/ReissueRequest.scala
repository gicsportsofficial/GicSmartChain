package com.gicsports.api.http.requests

import com.gicsports.account.PublicKey
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.Proofs
import com.gicsports.transaction.assets.ReissueTransaction
import play.api.libs.json.{Format, Json}

case class ReissueRequest(
    version: Option[Byte],
    sender: Option[String],
    senderPublicKey: Option[String],
    assetId: IssuedAsset,
    quantity: Long,
    reissuable: Boolean,
    fee: Long,
    timestamp: Option[Long],
    signature: Option[ByteStr],
    proofs: Option[Proofs]
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, ReissueTransaction] =
    for {
      validProofs <- toProofs(signature, proofs)
      tx <- ReissueTransaction.create(
        version.getOrElse(defaultVersion),
        sender,
        assetId,
        quantity,
        reissuable,
        fee,
        timestamp.getOrElse(defaultTimestamp),
        validProofs
      )
    } yield tx
}

object ReissueRequest {
  implicit val jsonFormat: Format[ReissueRequest] = Json.format
}
