package com.gicsports.settings

import com.gicsports.network.InvalidBlockStorageImpl.InvalidBlockStorageSettings
import com.gicsports.settings.SynchronizationSettings._

import scala.concurrent.duration.FiniteDuration

case class SynchronizationSettings(
    maxRollback: Int,
    synchronizationTimeout: FiniteDuration,
    scoreTTL: FiniteDuration,
    maxBaseTarget: Option[Long],
    invalidBlocksStorage: InvalidBlockStorageSettings,
    microBlockSynchronizer: MicroblockSynchronizerSettings,
    historyReplier: HistoryReplierSettings,
    utxSynchronizer: UtxSynchronizerSettings
)

object SynchronizationSettings {
  case class MicroblockSynchronizerSettings(
      waitResponseTimeout: FiniteDuration,
      processedMicroBlocksCacheTimeout: FiniteDuration,
      invCacheTimeout: FiniteDuration
  )

  case class HistoryReplierSettings(maxMicroBlockCacheSize: Int, maxBlockCacheSize: Int)

  case class UtxSynchronizerSettings(networkTxCacheSize: Int, maxThreads: Int, maxQueueSize: Int, allowTxRebroadcasting: Boolean)
}
