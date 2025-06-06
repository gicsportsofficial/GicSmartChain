package com.gicsports.api.http.requests

import com.gicsports.account.PublicKey
import com.gicsports.lang.ValidationError
import com.gicsports.lang.script.Script
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.transaction.Proofs
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.smart.InvokeExpressionTransaction
import play.api.libs.json.*

case class InvokeExpressionRequest(
    version: Option[Byte],
    sender: String,
    fee: Long,
    feeAssetId: Option[String],
    expression: String,
    timestamp: Option[Long] = None
)

case class SignedInvokeExpressionRequest(
    version: Option[Byte],
    senderPublicKey: String,
    fee: Long,
    feeAssetId: Option[String],
    expression: String,
    timestamp: Long,
    proofs: Proofs
) {
  def toTx: Either[ValidationError, InvokeExpressionTransaction] =
    for {
      _sender     <- PublicKey.fromBase58String(senderPublicKey)
      _feeAssetId <- parseBase58ToAsset(feeAssetId.filter(_.nonEmpty), "Invalid feeAssetId")
      _expression <- Script.fromBase64String(expression).flatMap {
        case script: ExprScript => Right(script)
        case _                  => Left(GenericError("Unexpected expression type for InvokeExpression"))
      }
      t <- InvokeExpressionTransaction.create(
        version.getOrElse(1.toByte),
        _sender,
        _expression,
        fee,
        _feeAssetId,
        timestamp,
        proofs
      )
    } yield t
}

object InvokeExpressionRequest {
  implicit val unsignedInvokeExpressionRequestReads: Reads[InvokeExpressionRequest]     = Json.reads[InvokeExpressionRequest]
  implicit val signedInvokeExpressionRequestReads: Reads[SignedInvokeExpressionRequest] = Json.reads[SignedInvokeExpressionRequest]
}
