package com.gicsports.lang.v1.traits.domain

import com.gicsports.common.state.ByteStr
import com.gicsports.lang.v1.traits.domain.Recipient.Address

case class ScriptAssetInfo(
  id:            ByteStr,
  name:          String,
  description:   String,
  quantity:      Long,
  decimals:      Int,
  issuer:        Address,
  issuerPk:      ByteStr,
  reissuable:    Boolean,
  scripted:      Boolean,
  minSponsoredFee: Option[Long]
)
