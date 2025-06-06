package com.gicsports.database

import com.gicsports.block.Block
import com.gicsports.common.state.ByteStr
import com.gicsports.state.Diff

trait Storage {
  def append(diff: Diff, carryFee: Long, totalFee: Long, reward: Option[Long], hitSource: ByteStr, block: Block): Unit
  def lastBlock: Option[Block]
  def rollbackTo(height: Int): Either[String, Seq[(Block, ByteStr)]]
  def safeRollbackHeight: Int
}
