package com.gicsports.api.http.requests

import cats.instances.option._
import cats.syntax.traverse._
import com.gicsports.account.PublicKey
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.assets.UpdateAssetInfoTransaction
import com.gicsports.transaction.{AssetIdStringLength, Proofs, TxTimestamp, TxVersion}
import play.api.libs.json.Json

case class UpdateAssetInfoRequest(
    version: TxVersion,
    chainId: Byte,
    sender: Option[String],
    senderPublicKey: Option[String],
    assetId: String,
    name: String,
    description: String,
    timestamp: Option[TxTimestamp],
    fee: Long,
    feeAssetId: Option[String],
    proofs: Option[Proofs]
) extends TxBroadcastRequest {
  override def toTxFrom(sender: PublicKey): Either[ValidationError, UpdateAssetInfoTransaction] =
    for {
      _assetId <- parseBase58(assetId, "invalid.assetId", AssetIdStringLength)
      _feeAssetId <- feeAssetId
        .traverse(parseBase58(_, "invalid.assetId", AssetIdStringLength).map(IssuedAsset(_)))
        .map(_ getOrElse Waves)
      tx <- UpdateAssetInfoTransaction
        .create(version, sender, _assetId, name, description, timestamp.getOrElse(0L), fee, _feeAssetId, proofs.getOrElse(Proofs.empty), chainId)
    } yield tx
}

object UpdateAssetInfoRequest {
  implicit val jsonFormat = Json.format[UpdateAssetInfoRequest]
}
