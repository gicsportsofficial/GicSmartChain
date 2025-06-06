package com.gicsports.api.common

import com.gicsports.account.{Address, Alias}
import com.gicsports.api.common.AddressPortfolio.{assetBalanceIterator, nftIterator}
import com.gicsports.api.common.TransactionMeta.Ethereum
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.database
import com.gicsports.database.{DBExt, KeyTags, Keys}
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.ValidationError
import com.gicsports.state.patch.CancelLeasesToDisabledAliases
import com.gicsports.state.reader.LeaseDetails.Status
import com.gicsports.state.{AccountScriptInfo, AssetDescription, Blockchain, DataEntry, Diff, Height, InvokeScriptResult}
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.EthereumTransaction.Invocation
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.lease.LeaseTransaction
import com.gicsports.transaction.{EthereumTransaction, TransactionType}
import monix.eval.Task
import monix.reactive.Observable
import org.iq80.leveldb.DB

trait CommonAccountsApi {
  import CommonAccountsApi.*

  def balance(address: Address, confirmations: Int = 0): Long

  def effectiveBalance(address: Address, confirmations: Int = 0): Long

  def balanceDetails(address: Address): Either[String, BalanceDetails]

  def assetBalance(address: Address, asset: IssuedAsset): Long

  def portfolio(address: Address): Observable[(IssuedAsset, Long)]

  def nftList(address: Address, after: Option[IssuedAsset]): Observable[(IssuedAsset, AssetDescription)]

  def script(address: Address): Option[AccountScriptInfo]

  def data(address: Address, key: String): Option[DataEntry[?]]

  def dataStream(address: Address, regex: Option[String]): Observable[DataEntry[?]]

  def activeLeases(address: Address): Observable[LeaseInfo]

  def leaseInfo(leaseId: ByteStr): Option[LeaseInfo]

  def resolveAlias(alias: Alias): Either[ValidationError, Address]
}

object CommonAccountsApi {
  def includeNft(blockchain: Blockchain)(assetId: IssuedAsset): Boolean =
    !blockchain.isFeatureActivated(BlockchainFeatures.ReduceNFTFee) || !blockchain.assetDescription(assetId).exists(_.nft)

  final case class BalanceDetails(regular: Long, generating: Long, available: Long, effective: Long, leaseIn: Long, leaseOut: Long)

  def apply(diff: () => Diff, db: DB, blockchain: Blockchain): CommonAccountsApi = new CommonAccountsApi {

    override def balance(address: Address, confirmations: Int = 0): Long =
      blockchain.balance(address, blockchain.height, confirmations)

    override def effectiveBalance(address: Address, confirmations: Int = 0): Long = {
      blockchain.effectiveBalance(address, confirmations)
    }

    override def balanceDetails(address: Address): Either[String, BalanceDetails] = {
      val portfolio = blockchain.wavesPortfolio(address)
      portfolio.effectiveBalance.map(effectiveBalance =>
        BalanceDetails(
          portfolio.balance,
          blockchain.generatingBalance(address),
          portfolio.balance - portfolio.lease.out,
          effectiveBalance,
          portfolio.lease.in,
          portfolio.lease.out
        )
      )
    }

    override def assetBalance(address: Address, asset: IssuedAsset): Long = blockchain.balance(address, asset)

    override def portfolio(address: Address): Observable[(IssuedAsset, Long)] = {
      val currentDiff = diff()
      db.resourceObservable.flatMap { resource =>
        Observable.fromIterator(Task(assetBalanceIterator(resource, address, currentDiff, includeNft(blockchain))))
      }
    }

    override def nftList(address: Address, after: Option[IssuedAsset]): Observable[(IssuedAsset, AssetDescription)] = {
      val currentDiff = diff()
      db.resourceObservable.flatMap { resource =>
        Observable.fromIterator(Task(nftIterator(resource, address, currentDiff, after, blockchain.assetDescription)))
      }
    }

    override def script(address: Address): Option[AccountScriptInfo] = blockchain.accountScript(address)

    override def data(address: Address, key: String): Option[DataEntry[?]] =
      blockchain.accountData(address, key)

    override def dataStream(address: Address, regex: Option[String]): Observable[DataEntry[?]] = Observable.defer {
      val pattern = regex.map(_.r.pattern)
      val entriesFromDiff = diff().accountData
        .get(address)
        .fold[Map[String, DataEntry[?]]](Map.empty)(_.data.filter { case (k, _) => pattern.forall(_.matcher(k).matches()) })

      val entries = db.readOnly { ro =>
        ro.get(Keys.addressId(address)).fold(Seq.empty[DataEntry[?]]) { addressId =>
          val filteredKeys = Set.newBuilder[String]

          ro.iterateOver(KeyTags.ChangedDataKeys.prefixBytes ++ addressId.toByteArray) { e =>
            for (key <- database.readStrings(e.getValue) if !entriesFromDiff.contains(key) && pattern.forall(_.matcher(key).matches()))
              filteredKeys += key
          }

          for {
            key <- filteredKeys.result().toVector
            h   <- ro.get(Keys.dataHistory(address, key)).headOption
            e   <- ro.get(Keys.data(addressId, key)(h))
          } yield e
        }
      }
      Observable.fromIterable((entriesFromDiff.values ++ entries).filterNot(_.isEmpty))
    }

    override def resolveAlias(alias: Alias): Either[ValidationError, Address] = blockchain.resolveAlias(alias)

    override def activeLeases(address: Address): Observable[LeaseInfo] =
      addressTransactions(
        db,
        Some(Height(blockchain.height) -> diff()),
        address,
        None,
        Set(TransactionType.Lease, TransactionType.InvokeScript, TransactionType.InvokeExpression, TransactionType.Ethereum),
        None
      ).flatMapIterable {
        case TransactionMeta(leaseHeight, lt: LeaseTransaction, true) if leaseIsActive(lt.id()) =>
          Seq(
            LeaseInfo(
              lt.id(),
              lt.id(),
              lt.sender.toAddress,
              blockchain.resolveAlias(lt.recipient).explicitGet(),
              lt.amount.value,
              leaseHeight,
              LeaseInfo.Status.Active
            )
          )
        case TransactionMeta.Invoke(invokeHeight, originTransaction, true, _, Some(scriptResult)) =>
          extractLeases(address, scriptResult, originTransaction.id(), invokeHeight)
        case Ethereum(height, tx @ EthereumTransaction(_: Invocation, _, _, _), true, _, _, Some(scriptResult)) =>
          extractLeases(address, scriptResult, tx.id(), height)
        case _ => Seq()
      }

    private def extractLeases(subject: Address, result: InvokeScriptResult, txId: ByteStr, height: Height): Seq[LeaseInfo] = {
      (for {
        lease   <- result.leases
        details <- blockchain.leaseDetails(lease.id) if details.isActive
        sender = details.sender.toAddress
        recipient <- blockchain.resolveAlias(lease.recipient).toOption if subject == sender || subject == recipient
      } yield LeaseInfo(
        lease.id,
        txId,
        sender,
        recipient,
        lease.amount,
        height,
        LeaseInfo.Status.Active
      )) ++ {
        result.invokes.flatMap(i => extractLeases(subject, i.stateChanges, txId, height))
      }
    }

    private def resolveDisabledAlias(leaseId: ByteStr): Either[ValidationError, Address] =
      CancelLeasesToDisabledAliases.patchData
        .get(leaseId)
        .fold[Either[ValidationError, Address]](Left(GenericError("Unknown lease ID"))) { case (_, recipientAddress) =>
          Right(recipientAddress)
        }

    def leaseInfo(leaseId: ByteStr): Option[LeaseInfo] = blockchain.leaseDetails(leaseId) map { ld =>
      LeaseInfo(
        leaseId,
        ld.sourceId,
        ld.sender.toAddress,
        blockchain.resolveAlias(ld.recipient).orElse(resolveDisabledAlias(leaseId)).explicitGet(),
        ld.amount,
        ld.height,
        ld.status match {
          case Status.Active          => LeaseInfo.Status.Active
          case Status.Cancelled(_, _) => LeaseInfo.Status.Canceled
          case Status.Expired(_)      => LeaseInfo.Status.Expired
        },
        ld.status.cancelHeight,
        ld.status.cancelTransactionId
      )
    }

    private[this] def leaseIsActive(id: ByteStr): Boolean =
      blockchain.leaseDetails(id).exists(_.isActive)
  }

}
