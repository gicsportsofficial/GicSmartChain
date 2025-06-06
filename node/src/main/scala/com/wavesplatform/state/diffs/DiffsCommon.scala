package com.gicsports.state.diffs

import cats.instances.option.*
import cats.syntax.apply.*
import cats.syntax.either.*
import cats.syntax.ior.*
import cats.syntax.traverse.*
import com.gicsports.account.{Address, AddressOrAlias, PublicKey}
import com.gicsports.common.state.ByteStr
import com.gicsports.features.BlockchainFeatures
import com.gicsports.features.ComplexityCheckPolicyProvider.*
import com.gicsports.features.EstimatorProvider.*
import com.gicsports.lang.ValidationError
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.estimator.ScriptEstimatorV1
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.lang.v1.traits.domain.*
import com.gicsports.state.reader.LeaseDetails
import com.gicsports.state.{AssetVolumeInfo, Blockchain, Diff, LeaseBalance, Portfolio, SponsorshipValue}
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.{Authorized, Transaction}

object DiffsCommon {
  def countScriptRuns(blockchain: Blockchain, tx: Transaction & Authorized): Int =
    tx.smartAssets(blockchain).size + Some(tx.sender.toAddress).count(blockchain.hasAccountScript)

  def countVerifierComplexity(
      script: Option[Script],
      blockchain: Blockchain,
      isAsset: Boolean
  ): Either[ValidationError, Option[(Script, Long)]] =
    script
      .traverse { script =>
        val useV1PreCheck =
          blockchain.height > blockchain.settings.functionalitySettings.estimatorPreCheckHeight &&
            !blockchain.isFeatureActivated(BlockchainFeatures.BlockV5)

        val fixEstimateOfVerifier = blockchain.isFeatureActivated(BlockchainFeatures.RideV6)
        val cost =
          if (useV1PreCheck)
            Script.verifierComplexity(script, ScriptEstimatorV1, fixEstimateOfVerifier, !isAsset && blockchain.useReducedVerifierComplexityLimit) *>
              Script.verifierComplexity(script, ScriptEstimatorV2, fixEstimateOfVerifier, !isAsset && blockchain.useReducedVerifierComplexityLimit)
          else
            Script.verifierComplexity(script, blockchain.estimator, fixEstimateOfVerifier, !isAsset && blockchain.useReducedVerifierComplexityLimit)

        cost.map((script, _))
      }
      .leftMap(GenericError(_))

  def validateAsset(
      blockchain: Blockchain,
      asset: IssuedAsset,
      sender: Address,
      issuerOnly: Boolean
  ): Either[ValidationError, Unit] = {
    @inline
    def validIssuer(issuerOnly: Boolean, sender: Address, issuer: Address) =
      !issuerOnly || sender == issuer

    blockchain.assetDescription(asset) match {
      case Some(ad) if !validIssuer(issuerOnly, sender, ad.issuer.toAddress) =>
        Left(GenericError("Asset was issued by other address"))
      case None =>
        Left(GenericError("Referenced assetId not found"))
      case Some(_) =>
        Right({})
    }
  }

  def processReissue(
      blockchain: Blockchain,
      sender: Address,
      blockTime: Long,
      fee: Long,
      reissue: Reissue
  ): Either[ValidationError, Diff] = {
    val asset = IssuedAsset(reissue.assetId)
    validateAsset(blockchain, asset, sender, issuerOnly = true)
      .flatMap { _ =>
        val oldInfo = blockchain.assetDescription(asset).get

        val isDataTxActivated = blockchain.isFeatureActivated(BlockchainFeatures.DataTransaction, blockchain.height)
        if (oldInfo.reissuable || (blockTime <= blockchain.settings.functionalitySettings.allowInvalidReissueInSameBlockUntilTimestamp)) {
          if ((Long.MaxValue - reissue.quantity) < oldInfo.totalVolume && isDataTxActivated) {
            Left(GenericError("Asset total value overflow"))
          } else {
            val volumeInfo = AssetVolumeInfo(reissue.isReissuable, BigInt(reissue.quantity))
            val portfolio  = Portfolio.build(-fee, asset, reissue.quantity)

            Right(
              Diff(
                portfolios = Map(sender                          -> portfolio),
                updatedAssets = Map(IssuedAsset(reissue.assetId) -> volumeInfo.rightIor)
              )
            )
          }
        } else {
          Left(GenericError("Asset is not reissuable"))
        }
      }
  }

  def processBurn(blockchain: Blockchain, sender: Address, fee: Long, burn: Burn): Either[ValidationError, Diff] = {
    val burnAnyTokensEnabled = blockchain.isFeatureActivated(BlockchainFeatures.BurnAnyTokens)
    val asset                = IssuedAsset(burn.assetId)

    validateAsset(blockchain, asset, sender, !burnAnyTokensEnabled).map { _ =>
      val volumeInfo = AssetVolumeInfo(isReissuable = true, volume = -burn.quantity)
      val portfolio  = Portfolio.build(-fee, asset, -burn.quantity)

      Diff(
        portfolios = Map(sender   -> portfolio),
        updatedAssets = Map(asset -> volumeInfo.rightIor)
      )
    }
  }

  def processSponsor(blockchain: Blockchain, sender: Address, fee: Long, sponsorFee: SponsorFee): Either[ValidationError, Diff] = {
    val asset = IssuedAsset(sponsorFee.assetId)
    validateAsset(blockchain, asset, sender, issuerOnly = true).flatMap { _ =>
      Either.cond(
        !blockchain.hasAssetScript(asset),
        Diff(
          portfolios = Map(sender -> Portfolio(balance = -fee)),
          sponsorship = Map(asset -> SponsorshipValue(sponsorFee.minSponsoredAssetFee.getOrElse(0)))
        ),
        GenericError("Sponsorship smart assets is disabled.")
      )
    }
  }

  def processLease(
      blockchain: Blockchain,
      amount: Long,
      sender: PublicKey,
      recipient: AddressOrAlias,
      fee: Long,
      leaseId: ByteStr,
      txId: ByteStr
  ): Either[ValidationError, Diff] = {
    val senderAddress = sender.toAddress
    for {
      recipientAddress <- blockchain.resolveAlias(recipient)
      _ <- Either.cond(
        recipientAddress != senderAddress,
        (),
        GenericError("Cannot lease to self")
      )
      _ <- Either.cond(
        blockchain.leaseDetails(leaseId).isEmpty,
        (),
        GenericError(s"Lease with id=$leaseId is already in the state")
      )
      leaseBalance    = blockchain.leaseBalance(senderAddress)
      senderBalance   = blockchain.balance(senderAddress, Waves)
      requiredBalance = if (blockchain.isFeatureActivated(BlockchainFeatures.SynchronousCalls)) amount + fee else amount
      _ <- Either.cond(
        senderBalance - leaseBalance.out >= requiredBalance,
        (),
        GenericError(s"Cannot lease more than own: Balance: $senderBalance, already leased: ${leaseBalance.out}")
      )
      portfolioDiff = Map(
        senderAddress    -> Portfolio(-fee, LeaseBalance(0, amount)),
        recipientAddress -> Portfolio(0, LeaseBalance(amount, 0))
      )
      details = LeaseDetails(sender, recipient, amount, LeaseDetails.Status.Active, txId, blockchain.height)
    } yield Diff(
      portfolios = portfolioDiff,
      leaseState = Map((leaseId, details))
    )
  }

  def processLeaseCancel(
      blockchain: Blockchain,
      sender: PublicKey,
      fee: Long,
      time: Long,
      leaseId: ByteStr,
      cancelTxId: ByteStr
  ): Either[ValidationError, Diff] = {
    val allowedTs = blockchain.settings.functionalitySettings.allowMultipleLeaseCancelTransactionUntilTimestamp
    for {
      lease     <- blockchain.leaseDetails(leaseId).toRight(GenericError(s"Lease with id=$leaseId not found"))
      recipient <- blockchain.resolveAlias(lease.recipient)
      _ <- Either.cond(
        lease.isActive || time <= allowedTs,
        (),
        GenericError(s"Cannot cancel already cancelled lease")
      )
      _ <- Either.cond(
        sender == lease.sender || time < allowedTs,
        (),
        GenericError(
          s"LeaseTransaction was leased by other sender and " +
            s"time=$time > allowMultipleLeaseCancelTransactionUntilTimestamp=$allowedTs"
        )
      )
      senderPortfolio    = Map[Address, Portfolio](sender.toAddress -> Portfolio(-fee, LeaseBalance(0, -lease.amount)))
      recipientPortfolio = Map(recipient -> Portfolio(0, LeaseBalance(-lease.amount, 0)))
      actionInfo         = lease.copy(status = LeaseDetails.Status.Cancelled(blockchain.height, Some(cancelTxId)))
      portfolios <- Diff.combine(senderPortfolio, recipientPortfolio).leftMap(GenericError(_))
    } yield Diff(
      portfolios = portfolios,
      leaseState = Map((leaseId, actionInfo))
    )
  }
}
