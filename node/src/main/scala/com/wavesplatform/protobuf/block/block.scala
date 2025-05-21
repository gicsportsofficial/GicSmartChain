package com.gicsports.protobuf

package object block {
  type PBBlock = com.gicsports.protobuf.block.Block
  val PBBlock = com.gicsports.protobuf.block.Block

  type VanillaBlock = com.gicsports.block.Block
  val VanillaBlock = com.gicsports.block.Block

  type PBBlockHeader = com.gicsports.protobuf.block.Block.Header
  val PBBlockHeader = com.gicsports.protobuf.block.Block.Header

  type VanillaBlockHeader = com.gicsports.block.BlockHeader
  val VanillaBlockHeader = com.gicsports.block.BlockHeader

  type PBSignedMicroBlock = com.gicsports.protobuf.block.SignedMicroBlock
  val PBSignedMicroBlock = com.gicsports.protobuf.block.SignedMicroBlock

  type PBMicroBlock = com.gicsports.protobuf.block.MicroBlock
  val PBMicroBlock = com.gicsports.protobuf.block.MicroBlock

  type VanillaMicroBlock = com.gicsports.block.MicroBlock
  val VanillaMicroBlock = com.gicsports.block.MicroBlock
}
