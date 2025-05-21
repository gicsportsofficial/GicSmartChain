package com.gicsports.utils

import com.typesafe.config.ConfigFactory
import com.gicsports.account.{Address, Alias}
import com.gicsports.block.SignedBlockHeader
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.ValidationError
import com.gicsports.settings.BlockchainSettings
import com.gicsports.state._
import com.gicsports.state.reader.LeaseDetails
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.transfer.TransferTransactionLike
import com.gicsports.transaction.{Asset, ERC20Address, Transaction}

trait EmptyBlockchain extends Blockchain {
  override lazy val settings: BlockchainSettings = BlockchainSettings.fromRootConfig(ConfigFactory.load())

  override def height: Int = 0

  override def score: BigInt = 0

  override def blockHeader(height: Int): Option[SignedBlockHeader] = None

  override def hitSource(height: Int): Option[ByteStr] = None

  override def carryFee: Long = 0

  override def heightOf(blockId: ByteStr): Option[Int] = None

  /** Features related */
  override def approvedFeatures: Map[Short, Int] = Map.empty

  override def activatedFeatures: Map[Short, Int] = Map.empty

  override def featureVotes(height: Int): Map[Short, Int] = Map.empty

  /** Block reward related */
  override def blockReward(height: Int): Option[Long] = None

  override def blockRewardVotes(height: Int): Seq[Long] = Seq.empty

  override def wavesAmount(height: Int): BigInt = 0

  override def transferById(id: ByteStr): Option[(Int, TransferTransactionLike)] = None

  override def transactionInfo(id: ByteStr): Option[(TxMeta, Transaction)] = None

  override def transactionMeta(id: ByteStr): Option[TxMeta] = None

  override def containsTransaction(tx: Transaction): Boolean = false

  override def assetDescription(id: IssuedAsset): Option[AssetDescription] = None

  override def resolveAlias(a: Alias): Either[ValidationError, Address] = Left(GenericError("Empty blockchain"))

  override def leaseDetails(leaseId: ByteStr): Option[LeaseDetails] = None

  override def filledVolumeAndFee(orderId: ByteStr): VolumeAndFee = VolumeAndFee(0, 0)

  /** Retrieves Waves balance snapshot in the [from, to] range (inclusive) */
  override def balanceAtHeight(address: Address, height: Int, assetId: Asset = Waves): Option[(Int, Long)] = Option.empty
  override def balanceSnapshots(address: Address, from: Int, to: Option[ByteStr]): Seq[BalanceSnapshot]    = Seq.empty

  override def accountScript(address: Address): Option[AccountScriptInfo] = None

  override def hasAccountScript(address: Address): Boolean = false

  override def assetScript(asset: IssuedAsset): Option[AssetScriptInfo] = None

  override def accountData(acc: Address, key: String): Option[DataEntry[_]] = None

  override def hasData(acc: Address): Boolean = false

  override def balance(address: Address, mayBeAssetId: Asset): Long = 0

  override def leaseBalance(address: Address): LeaseBalance = LeaseBalance.empty

  override def resolveERC20Address(address: ERC20Address): Option[IssuedAsset] = None
}

object EmptyBlockchain extends EmptyBlockchain
