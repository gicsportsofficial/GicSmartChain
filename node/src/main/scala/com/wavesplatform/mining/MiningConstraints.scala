package com.gicsports.mining

import cats.data.NonEmptyList
import com.gicsports.block.Block
import com.gicsports.features.BlockchainFeatures
import com.gicsports.settings.MinerSettings
import com.gicsports.state.Blockchain

case class MiningConstraints(total: MiningConstraint, keyBlock: MiningConstraint, micro: MiningConstraint)

object MiningConstraints {
  val MaxScriptRunsInBlock = 100
  object MaxScriptsComplexityInBlock {
    val BeforeRideV5 = 1000000
    val AfterRideV5  = 2500000
  }

  val ClassicAmountOfTxsInBlock = 100
  val MaxTxsSizeInBytes         = 1 * 1024 * 1024 // 1 megabyte

  def apply(blockchain: Blockchain, height: Int, minerSettings: Option[MinerSettings] = None): MiningConstraints = {
    val activatedFeatures     = blockchain.activatedFeaturesAt(height)
    val isNgEnabled           = activatedFeatures.contains(BlockchainFeatures.NG.id)
    val isMassTransferEnabled = activatedFeatures.contains(BlockchainFeatures.MassTransfer.id)
    val isScriptEnabled       = activatedFeatures.contains(BlockchainFeatures.SmartAccounts.id)
    val isDAppsEnabled        = activatedFeatures.contains(BlockchainFeatures.Ride4DApps.id)

    val total: MiningConstraint =
      if (isMassTransferEnabled) OneDimensionalMiningConstraint(MaxTxsSizeInBytes, TxEstimators.sizeInBytes, "MaxTxsSizeInBytes")
      else {
        val maxTxs = if (isNgEnabled) Block.MaxTransactionsPerBlockVer3 else ClassicAmountOfTxsInBlock
        OneDimensionalMiningConstraint(maxTxs, TxEstimators.one, "MaxTxs")
      }

    new MiningConstraints(
      total =
        if (isDAppsEnabled) {
          val complexityLimit =
            if (blockchain.isFeatureActivated(BlockchainFeatures.SynchronousCalls)) MaxScriptsComplexityInBlock.AfterRideV5
            else MaxScriptsComplexityInBlock.BeforeRideV5
          MultiDimensionalMiningConstraint(
            NonEmptyList
              .of(OneDimensionalMiningConstraint(complexityLimit, TxEstimators.scriptsComplexity, "MaxScriptsComplexityInBlock"), total)
          )
        } else if (isScriptEnabled)
          MultiDimensionalMiningConstraint(
            NonEmptyList.of(OneDimensionalMiningConstraint(MaxScriptRunsInBlock, TxEstimators.scriptRunNumber, "MaxScriptRunsInBlock"), total)
          )
        else total,
      keyBlock =
        if (isNgEnabled) OneDimensionalMiningConstraint(0, TxEstimators.one, "MaxTxsInKeyBlock")
        else OneDimensionalMiningConstraint(ClassicAmountOfTxsInBlock, TxEstimators.one, "MaxTxsInKeyBlock"),
      micro =
        if (isNgEnabled && minerSettings.isDefined)
          OneDimensionalMiningConstraint(minerSettings.get.maxTransactionsInMicroBlock, TxEstimators.one, "MaxTxsInMicroBlock")
        else MiningConstraint.Unlimited
    )
  }
}
