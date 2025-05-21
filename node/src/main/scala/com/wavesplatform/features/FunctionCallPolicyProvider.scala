package com.gicsports.features

import com.gicsports.state.Blockchain

object FunctionCallPolicyProvider {
  implicit class MultiPaymentAllowedExt(b: Blockchain) {
    def callableListArgumentsAllowed: Boolean =
      b.isFeatureActivated(BlockchainFeatures.BlockV5)

    def callableListArgumentsCorrected: Boolean =
      b.isFeatureActivated(BlockchainFeatures.RideV6)

    def checkSyncCallArgumentsTypes: Boolean =
      b.isFeatureActivated(BlockchainFeatures.RideV6)
  }
}
