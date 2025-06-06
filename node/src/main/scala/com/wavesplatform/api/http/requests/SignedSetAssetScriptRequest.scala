package com.gicsports.api.http.requests

import com.gicsports.account.PublicKey
import com.gicsports.lang.ValidationError
import com.gicsports.lang.script.Script
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.Proofs
import com.gicsports.transaction.assets.SetAssetScriptTransaction
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

object SignedSetAssetScriptRequest {
  implicit val signedSetAssetScriptRequestReads: Reads[SignedSetAssetScriptRequest] = (
    (JsPath \ "version").readNullable[Byte] and
      (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "assetId").read[IssuedAsset] and
      (JsPath \ "script").readNullable[String] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "proofs").read[Proofs]
  )(SignedSetAssetScriptRequest.apply _)
}

case class SignedSetAssetScriptRequest(
    version: Option[Byte],
    senderPublicKey: String,
    assetId: IssuedAsset,
    script: Option[String],
    fee: Long,
    timestamp: Long,
    proofs: Proofs
) {
  def toTx: Either[ValidationError, SetAssetScriptTransaction] =
    for {
      _sender <- PublicKey.fromBase58String(senderPublicKey)
      _script <- script match {
        case None | Some("") => Right(None)
        case Some(s)         => Script.fromBase64String(s).map(Some(_))
      }
      t <- SetAssetScriptTransaction.create(version.getOrElse(1.toByte), _sender, assetId, _script, fee, timestamp, proofs)
    } yield t
}
