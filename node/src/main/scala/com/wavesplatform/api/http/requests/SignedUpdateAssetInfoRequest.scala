package com.gicsports.api.http.requests

import cats.instances.option._
import cats.syntax.traverse._
import com.gicsports.account.PublicKey
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.assets.UpdateAssetInfoTransaction
import com.gicsports.transaction.{AssetIdStringLength, Proofs, TxTimestamp, TxVersion}
import play.api.libs.json.{Format, Json}

case class SignedUpdateAssetInfoRequest(
    version: TxVersion,
    chainId: Byte,
    senderPublicKey: String,
    assetId: String,
    name: String,
    description: String,
    timestamp: TxTimestamp,
    fee: Long,
    feeAssetId: Option[String],
    proofs: Proofs
) {

  def toTx: Either[ValidationError, UpdateAssetInfoTransaction] =
    for {
      _sender  <- PublicKey.fromBase58String(senderPublicKey)
      _assetId <- parseBase58(assetId, "invalid.assetId", AssetIdStringLength)
      _feeAssetId <- feeAssetId
        .traverse(parseBase58(_, "invalid.assetId", AssetIdStringLength).map(IssuedAsset(_)))
        .map(_ getOrElse Waves)
      tx <- UpdateAssetInfoTransaction
        .create(version, _sender, _assetId, name, description, timestamp, fee, _feeAssetId, proofs, chainId)
    } yield tx

}

object SignedUpdateAssetInfoRequest {
  implicit val format: Format[SignedUpdateAssetInfoRequest] = Json.format
}
