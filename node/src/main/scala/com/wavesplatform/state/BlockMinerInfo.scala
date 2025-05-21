package com.gicsports.state

import com.gicsports.block.Block.BlockId
import com.gicsports.common.state.ByteStr

case class BlockMinerInfo(baseTarget: Long, generationSignature: ByteStr, timestamp: Long, blockId: BlockId)
