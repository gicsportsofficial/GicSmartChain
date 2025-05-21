package com.gicsports.state

import com.gicsports.block.Block.BlockId
import com.gicsports.block.MicroBlock
import com.gicsports.common.state.ByteStr

trait NG {
  def microBlock(id: ByteStr): Option[MicroBlock]

  def bestLastBlockInfo(maxTimestamp: Long): Option[BlockMinerInfo]

  def microblockIds: Seq[BlockId]
}
