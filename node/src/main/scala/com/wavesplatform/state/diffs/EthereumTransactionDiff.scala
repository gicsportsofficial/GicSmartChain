package com.gicsports.state.diffs

import com.google.protobuf.ByteString
import com.gicsports.database.protobuf.EthereumTransactionMeta
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.ValidationError
import com.gicsports.lang.v1.serialization.SerdeV1
import com.gicsports.protobuf.transaction.{PBAmounts, PBRecipients}
import com.gicsports.state.diffs.invoke.InvokeScriptTransactionDiff
import com.gicsports.state.{Blockchain, Diff}
import com.gicsports.transaction.EthereumTransaction
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.smart.script.trace.TracedResult

object EthereumTransactionDiff {
  def meta(blockchain: Blockchain)(e: EthereumTransaction): Diff = {
    val resultEi = e.payload match {
      case et: EthereumTransaction.Transfer =>
        for (assetId <- et.tryResolveAsset(blockchain))
          yield Diff(
            ethereumTransactionMeta = Map(
              e.id() -> EthereumTransactionMeta(
                EthereumTransactionMeta.Payload.Transfer(
                  EthereumTransactionMeta.Transfer(
                    ByteString.copyFrom(PBRecipients.publicKeyHash(et.recipient)),
                    Some(PBAmounts.fromAssetAndAmount(assetId, et.amount))
                  )
                )
              )
            )
          )

      case ei: EthereumTransaction.Invocation =>
        for {
          invocation <- ei.toInvokeScriptLike(e, blockchain)
        } yield Diff(
          ethereumTransactionMeta = Map(
            e.id() -> EthereumTransactionMeta(
              EthereumTransactionMeta.Payload.Invocation(
                EthereumTransactionMeta.Invocation(
                  ByteString.copyFrom(SerdeV1.serialize(invocation.funcCall)),
                  invocation.payments.map(p => PBAmounts.fromAssetAndAmount(p.assetId, p.amount))
                )
              )
            )
          )
        )
    }
    resultEi.getOrElse(Diff.empty)
  }

  def apply(blockchain: Blockchain, currentBlockTs: Long, limitedExecution: Boolean)(e: EthereumTransaction): TracedResult[ValidationError, Diff] = {
    val baseDiff = e.payload match {
      case et: EthereumTransaction.Transfer =>
        for {
          _         <- checkLeadingZeros(e, blockchain)
          asset     <- TracedResult(et.tryResolveAsset(blockchain))
          transfer  <- TracedResult(et.toTransferLike(e, blockchain))
          assetDiff <- TransactionDiffer.assetsVerifierDiff(blockchain, transfer, verify = true, Diff(), Int.MaxValue)
          diff      <- TransferDiff(blockchain)(e.senderAddress(), et.recipient, et.amount, asset, e.fee, e.feeAssetId)
          result    <- assetDiff.combineE(diff)
        } yield result

      case ei: EthereumTransaction.Invocation =>
        for {
          _          <- checkLeadingZeros(e, blockchain)
          invocation <- TracedResult(ei.toInvokeScriptLike(e, blockchain))
          diff       <- InvokeScriptTransactionDiff(blockchain, currentBlockTs, limitedExecution)(invocation)
          result     <- TransactionDiffer.assetsVerifierDiff(blockchain, invocation, verify = true, diff, Int.MaxValue)
        } yield result
    }

    baseDiff.flatMap(bd => TracedResult(bd.combineE(this.meta(blockchain)(e))))
  }

  private def checkLeadingZeros(tx: EthereumTransaction, blockchain: Blockchain): TracedResult[ValidationError, Unit] = {
    TracedResult(
      Either.cond(
        !tx.sender.arr.headOption.contains(0.toByte) || blockchain.isFeatureActivated(BlockchainFeatures.ConsensusImprovements),
        (),
        GenericError("Sender public key with leading zero byte is not allowed")
      )
    )
  }
}
