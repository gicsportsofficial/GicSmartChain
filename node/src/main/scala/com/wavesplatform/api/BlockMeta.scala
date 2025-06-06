package com.gicsports.api

import com.gicsports.block.Block.protoHeaderHash
import com.gicsports.block.serialization.BlockHeaderSerializer
import com.gicsports.block.{Block, BlockHeader, SignedBlockHeader}
import com.gicsports.common.state.ByteStr
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}

case class BlockMeta(
    header: BlockHeader,
    signature: ByteStr,
    headerHash: Option[ByteStr],
    height: Int,
    size: Int,
    transactionCount: Int,
    totalFeeInWaves: Long,
    reward: Option[Long],
    vrf: Option[ByteStr]
) {
  def toSignedHeader: SignedBlockHeader = SignedBlockHeader(header, signature)
  def id: ByteStr                       = headerHash.getOrElse(signature)

  val json: Coeval[JsObject] = Coeval.evalOnce {
    BlockHeaderSerializer.toJson(header, size, transactionCount, signature) ++
      Json.obj("height" -> height, "totalFee" -> totalFeeInWaves) ++
      reward.fold(Json.obj())(r => Json.obj("reward" -> r)) ++
      vrf.fold(Json.obj())(v => Json.obj("VRF"       -> v.toString)) ++
      headerHash.fold(Json.obj())(h => Json.obj("id" -> h.toString))
  }
}

object BlockMeta {
  def fromBlock(block: Block, height: Int, totalFee: Long, reward: Option[Long], vrf: Option[ByteStr]): BlockMeta = BlockMeta(
    block.header,
    block.signature,
    if (block.header.version >= Block.ProtoBlockVersion) Some(protoHeaderHash(block.header)) else None,
    height,
    block.bytes().length,
    block.transactionData.length,
    totalFee,
    reward,
    vrf
  )
}
