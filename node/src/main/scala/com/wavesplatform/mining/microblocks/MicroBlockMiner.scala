package com.gicsports.mining.microblocks

import com.gicsports.account.KeyPair
import com.gicsports.block.Block
import com.gicsports.mining.{MinerDebugInfo, MiningConstraint}
import com.gicsports.settings.MinerSettings
import com.gicsports.state.Blockchain
import com.gicsports.transaction.BlockchainUpdater
import com.gicsports.utx.UtxPoolImpl
import io.netty.channel.group.ChannelGroup
import monix.eval.Task
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable

trait MicroBlockMiner {
  def generateMicroBlockSequence(
      account: KeyPair,
      accumulatedBlock: Block,
      restTotalConstraint: MiningConstraint,
      lastMicroBlock: Long
  ): Task[Unit]
}

object MicroBlockMiner {
  def apply(
      setDebugState: MinerDebugInfo.State => Unit,
      allChannels: ChannelGroup,
      blockchainUpdater: BlockchainUpdater with Blockchain,
      utx: UtxPoolImpl,
      settings: MinerSettings,
      minerScheduler: SchedulerService,
      appenderScheduler: SchedulerService,
      transactionAdded: Observable[Unit],
      nextMicroBlockSize: Int => Int = identity
  ): MicroBlockMiner =
    new MicroBlockMinerImpl(
      setDebugState,
      allChannels,
      blockchainUpdater,
      utx,
      settings,
      minerScheduler,
      appenderScheduler,
      transactionAdded,
      nextMicroBlockSize
    )
}
