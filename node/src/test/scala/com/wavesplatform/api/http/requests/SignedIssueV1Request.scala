package com.gicsports.api.http.requests

import com.gicsports.account.PublicKey
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.assets.IssueTransaction
import com.gicsports.transaction.{Proofs, TxVersion}
import play.api.libs.json.{Format, Json}

object SignedIssueV1Request {
  implicit val assetIssueRequestReads: Format[SignedIssueV1Request] = Json.format
}

case class SignedIssueV1Request(
    senderPublicKey: String,
    name: String,
    description: String,
    quantity: Long,
    decimals: Byte,
    reissuable: Boolean,
    fee: Long,
    timestamp: Long,
    signature: String
) {
  def toTx: Either[ValidationError, IssueTransaction] =
    for {
      _sender    <- PublicKey.fromBase58String(senderPublicKey)
      _signature <- parseBase58(signature, "invalid signature", SignatureStringLength)
      _t <- IssueTransaction.create(
        TxVersion.V1,
        _sender,
        name,
        description,
        quantity,
        decimals,
        reissuable,
        script = None,
        fee,
        timestamp,
        Proofs(_signature)
      )
    } yield _t
}
