package com.gicsports.features

import com.gicsports.state.Blockchain

object ScriptTransferValidationProvider {
  implicit class PassCorrectAssetIdExt(b: Blockchain) {
    def passCorrectAssetId: Boolean =
      b.isFeatureActivated(BlockchainFeatures.BlockV5)
  }
}
