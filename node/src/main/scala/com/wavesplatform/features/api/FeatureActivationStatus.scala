package com.gicsports.features.api

import com.gicsports.features.BlockchainFeatureStatus

case class FeatureActivationStatus(
    id: Short,
    description: String,
    blockchainStatus: BlockchainFeatureStatus,
    nodeStatus: NodeFeatureStatus,
    activationHeight: Option[Int],
    supportingBlocks: Option[Int]
)
