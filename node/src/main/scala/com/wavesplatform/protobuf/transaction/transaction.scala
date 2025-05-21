package com.gicsports.protobuf

//noinspection TypeAnnotation
package object transaction {
  type PBOrder = com.gicsports.protobuf.order.Order
  val PBOrder = com.gicsports.protobuf.order.Order

  type VanillaOrder = com.gicsports.transaction.assets.exchange.Order
  val VanillaOrder = com.gicsports.transaction.assets.exchange.Order

  type PBTransaction = com.gicsports.protobuf.transaction.Transaction
  val PBTransaction = com.gicsports.protobuf.transaction.Transaction

  type PBSignedTransaction = com.gicsports.protobuf.transaction.SignedTransaction
  val PBSignedTransaction = com.gicsports.protobuf.transaction.SignedTransaction

  type VanillaTransaction = com.gicsports.transaction.Transaction
  val VanillaTransaction = com.gicsports.transaction.Transaction

  type VanillaAssetId = com.gicsports.transaction.Asset
}
