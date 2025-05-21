package com.gicsports.api.common

import com.gicsports.account.Address
import com.gicsports.api.{BlockMeta, common}
import com.gicsports.block
import com.gicsports.block.Block
import com.gicsports.block.Block.TransactionProof
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.ValidationError
import com.gicsports.state.diffs.FeeValidation
import com.gicsports.state.diffs.FeeValidation.FeeDetails
import com.gicsports.state.{Blockchain, Diff, Height, TxMeta}
import com.gicsports.transaction.TransactionType.TransactionType
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.transaction.{Asset, CreateAliasTransaction, Transaction}
import com.gicsports.utx.UtxPool
import monix.reactive.Observable
import org.iq80.leveldb.DB

import scala.concurrent.Future

trait CommonTransactionsApi {

  def aliasesOfAddress(address: Address): Observable[(Height, CreateAliasTransaction)]

  def transactionById(txId: ByteStr): Option[TransactionMeta]

  def unconfirmedTransactions: Seq[Transaction]

  def unconfirmedTransactionById(txId: ByteStr): Option[Transaction]

  def calculateFee(tx: Transaction): Either[ValidationError, (Asset, Long, Long)]

  def broadcastTransaction(tx: Transaction): Future[TracedResult[ValidationError, Boolean]]

  def transactionsByAddress(
      subject: Address,
      sender: Option[Address],
      transactionTypes: Set[TransactionType],
      fromId: Option[ByteStr] = None
  ): Observable[TransactionMeta]

  def transactionProofs(transactionIds: List[ByteStr]): List[TransactionProof]
}

object CommonTransactionsApi {
  def apply(
      maybeDiff: => Option[(Height, Diff)],
      db: DB,
      blockchain: Blockchain,
      utx: UtxPool,
      publishTransaction: Transaction => Future[TracedResult[ValidationError, Boolean]],
      blockAt: Int => Option[(BlockMeta, Seq[(TxMeta, Transaction)])]
  ): CommonTransactionsApi = new CommonTransactionsApi {
    override def aliasesOfAddress(address: Address): Observable[(Height, CreateAliasTransaction)] = common.aliasesOfAddress(db, maybeDiff, address)

    override def transactionsByAddress(
        subject: Address,
        sender: Option[Address],
        transactionTypes: Set[TransactionType],
        fromId: Option[ByteStr] = None
    ): Observable[TransactionMeta] =
      common.addressTransactions(db, maybeDiff, subject, sender, transactionTypes, fromId)

    override def transactionById(transactionId: ByteStr): Option[TransactionMeta] =
      blockchain.transactionInfo(transactionId).map(common.loadTransactionMeta(db, maybeDiff))

    override def unconfirmedTransactions: Seq[Transaction] = utx.all

    override def unconfirmedTransactionById(transactionId: ByteStr): Option[Transaction] =
      utx.transactionById(transactionId)

    override def calculateFee(tx: Transaction): Either[ValidationError, (Asset, Long, Long)] =
      FeeValidation
        .getMinFee(blockchain, tx)
        .map {
          case FeeDetails(asset, _, feeInAsset, feeInWaves) =>
            (asset, feeInAsset, feeInWaves)
        }

    override def broadcastTransaction(tx: Transaction): Future[TracedResult[ValidationError, Boolean]] = publishTransaction(tx)

    override def transactionProofs(transactionIds: List[ByteStr]): List[TransactionProof] =
      for {
        transactionId           <- transactionIds
        (txm, tx)               <- blockchain.transactionInfo(transactionId)
        (meta, allTransactions) <- blockAt(txm.height) if meta.header.version >= Block.ProtoBlockVersion
        transactionProof        <- block.transactionProof(tx, allTransactions.map(_._2))
      } yield transactionProof
  }
}
