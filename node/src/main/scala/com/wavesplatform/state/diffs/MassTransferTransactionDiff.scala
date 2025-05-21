package com.gicsports.state.diffs

import cats.implicits.{toBifunctorOps, toFoldableOps}
import cats.instances.list.*
import cats.syntax.traverse.*
import com.gicsports.account.Address
import com.gicsports.lang.ValidationError
import com.gicsports.state.*
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.TxValidationError.{GenericError, Validation}
import com.gicsports.transaction.transfer.*
import com.gicsports.transaction.transfer.MassTransferTransaction.ParsedTransfer

object MassTransferTransactionDiff {

  def apply(blockchain: Blockchain, blockTime: Long)(tx: MassTransferTransaction): Either[ValidationError, Diff] = {
    def parseTransfer(xfer: ParsedTransfer): Validation[(Map[Address, Portfolio], Long)] = {
      for {
        recipientAddr <- blockchain.resolveAlias(xfer.address)
        portfolio = tx.assetId
          .fold(Map[Address, Portfolio](recipientAddr -> Portfolio(xfer.amount.value))) { asset =>
            Map(recipientAddr -> Portfolio.build(asset, xfer.amount.value))
          }
      } yield (portfolio, xfer.amount.value)
    }
    val portfoliosEi = tx.transfers.toList.traverse(parseTransfer)

    portfoliosEi.flatMap { list: List[(Map[Address, Portfolio], Long)] =>
      val sender   = Address.fromPublicKey(tx.sender)
      val foldInit = (Map[Address, Portfolio](sender -> Portfolio(-tx.fee.value)), 0L)
      list
        .foldM(foldInit) { case ((totalPortfolios, totalTransferAmount), (portfolios, transferAmount)) =>
          Diff.combine(totalPortfolios, portfolios).map((_, totalTransferAmount + transferAmount))
        }
        .flatMap { case (recipientPortfolios, totalAmount) =>
          Diff.combine(
            recipientPortfolios,
            tx.assetId
              .fold(Map[Address, Portfolio](sender -> Portfolio(-totalAmount))) { asset =>
                Map[Address, Portfolio](sender -> Portfolio.build(asset, -totalAmount))
              }
          )
        }
        .leftMap(GenericError(_))
        .flatMap { completePortfolio =>
          val assetIssued =
            tx.assetId match {
              case Waves                  => true
              case asset @ IssuedAsset(_) => blockchain.assetDescription(asset).isDefined
            }
          Either.cond(
            assetIssued,
            Diff(portfolios = completePortfolio, scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)),
            GenericError(s"Attempt to transfer a nonexistent asset")
          )
        }
    }
  }
}
