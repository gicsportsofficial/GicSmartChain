package com.gicsports.transaction

import com.gicsports.account.Address

case class AssetAcc(account: Address, assetId: Option[Asset])
