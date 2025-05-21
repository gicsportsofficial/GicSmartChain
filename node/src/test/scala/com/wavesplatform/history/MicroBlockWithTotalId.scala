package com.gicsports.history

import com.gicsports.block.Block.BlockId
import com.gicsports.block.MicroBlock

class MicroBlockWithTotalId(val microBlock: MicroBlock, val totalBlockId: BlockId)
object MicroBlockWithTotalId {
  implicit def toMicroBlock(mb: MicroBlockWithTotalId): MicroBlock = mb.microBlock
}
