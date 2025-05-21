package com.gicsports.transaction.validation.impl

import com.gicsports.transaction.Asset
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.assets.UpdateAssetInfoTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}
import com.gicsports.utils.StringBytes

object UpdateAssetInfoTxValidator extends TxValidator[UpdateAssetInfoTransaction] {
  override def validate(tx: UpdateAssetInfoTransaction): ValidatedV[UpdateAssetInfoTransaction] =
    V.seq(tx)(
      V.asset[IssuedAsset](tx.assetId),
      V.asset[Asset](tx.feeAsset),
      V.assetName(tx.name.toByteString),
      V.assetDescription(tx.description.toByteString)
    )
}
