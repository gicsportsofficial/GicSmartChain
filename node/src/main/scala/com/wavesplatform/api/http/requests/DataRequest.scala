package com.gicsports.api.http.requests

import com.gicsports.account.PublicKey
import com.gicsports.lang.ValidationError
import com.gicsports.state.DataEntry
import com.gicsports.transaction.{DataTransaction, Proofs}
import play.api.libs.json.{Format, Json}

object DataRequest {
  implicit val unsignedDataRequestReads: Format[DataRequest] = Json.format
}

case class DataRequest(
    version: Byte,
    sender: String,
    data: List[DataEntry[_]],
    fee: Long,
    timestamp: Option[Long] = None
)

case class SignedDataRequest(version: Byte, senderPublicKey: String, data: List[DataEntry[_]], fee: Long, timestamp: Long, proofs: Proofs) {
  def toTx: Either[ValidationError, DataTransaction] =
    for {
      _sender <- PublicKey.fromBase58String(senderPublicKey)
      t       <- DataTransaction.create(version, _sender, data, fee, timestamp, proofs)
    } yield t
}

object SignedDataRequest {
  implicit val signedDataRequestReads: Format[SignedDataRequest] = Json.format
}
