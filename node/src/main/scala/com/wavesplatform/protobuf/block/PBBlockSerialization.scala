package com.gicsports.protobuf.block
import com.google.protobuf.ByteString
import com.gicsports.protobuf.utils.PBUtils

private[block] object PBBlockSerialization {
  def signedBytes(block: PBBlock): Array[Byte] = {
    PBUtils.encodeDeterministic(block)
  }

  def unsignedBytes(block: PBBlock): Array[Byte] = {
    PBUtils.encodeDeterministic(block.withSignature(ByteString.EMPTY))
  }
}
