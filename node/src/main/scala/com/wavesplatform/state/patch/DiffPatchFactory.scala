package com.gicsports.state.patch

import scala.io.Source

import com.gicsports.account.AddressScheme
import com.gicsports.features.BlockchainFeature
import com.gicsports.state.{Blockchain, Diff}
import play.api.libs.json.{Json, Reads}

trait PatchDataLoader {
  protected def readPatchData[T: Reads](): T =
    Json
      .parse(
        Source
          .fromResource(s"patches/${getClass.getSimpleName.replace("$", "")}-${AddressScheme.current.chainId.toChar}.json")
          .mkString
      )
      .as[T]
}

trait DiffPatchFactory extends PartialFunction[Blockchain, Diff]

abstract class PatchAtHeight(chainIdToHeight: (Char, Int)*) extends PatchDataLoader with DiffPatchFactory {
  private[this] val chainIdToHeightMap   = chainIdToHeight.toMap
  protected def patchHeight: Option[Int] = chainIdToHeightMap.get(AddressScheme.current.chainId.toChar)

  override def isDefinedAt(blockchain: Blockchain): Boolean =
    chainIdToHeightMap.get(blockchain.settings.addressSchemeCharacter).contains(blockchain.height)
}

abstract class PatchOnFeature(feature: BlockchainFeature, networks: Set[Char] = Set.empty) extends PatchDataLoader with DiffPatchFactory {
  override def isDefinedAt(blockchain: Blockchain): Boolean = {
    (networks.isEmpty || networks.contains(blockchain.settings.addressSchemeCharacter)) &&
    blockchain.featureActivationHeight(feature.id).contains(blockchain.height)
  }
}
