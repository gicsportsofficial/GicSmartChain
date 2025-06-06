package com.gicsports.state.diffs

import com.gicsports.account.Address
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.v1.compiler.Terms.CONST_BOOLEAN
import com.gicsports.settings.TestFunctionalitySettings
import com.gicsports.test.*
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.assets.*
import com.gicsports.transaction.transfer.*
import com.gicsports.transaction.{Asset, GenesisTransaction, TxHelpers, TxValidationError, TxVersion}

class TransferDiffTest extends PropSpec with WithState {

  val preconditionsAndTransfer: Seq[(GenesisTransaction, IssueTransaction, IssueTransaction, TransferTransaction)] = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis = TxHelpers.genesis(master.toAddress)

    val issue1 = TxHelpers.issue(master, ENOUGH_AMT, name = "asset1", version = TxVersion.V1)
    val issue2 = TxHelpers.issue(master, ENOUGH_AMT, name = "asset2", version = TxVersion.V1)

    for {
      maybeAsset1   <- Seq(None, Some(issue1.id())).map(Asset.fromCompatId)
      maybeAsset2   <- Seq(None, Some(issue2.id())).map(Asset.fromCompatId)
      maybeFeeAsset <- Seq(maybeAsset1, maybeAsset2)
      transferV1 = TxHelpers.transfer(master, recipient.toAddress, asset = maybeAsset1, feeAsset = maybeFeeAsset, version = TxVersion.V1)
      transferV2 = TxHelpers.transfer(master, recipient.toAddress, asset = maybeAsset1, feeAsset = maybeFeeAsset)
      transfer <- Seq(transferV1, transferV2)
    } yield (genesis, issue1, issue2, transfer)
  }

  property("transfers assets to recipient preserving waves invariant") {
    preconditionsAndTransfer.foreach { case (genesis, issue1, issue2, transfer) =>
      assertDiffAndState(Seq(TestBlock.create(Seq(genesis, issue1, issue2))), TestBlock.create(Seq(transfer))) { case (totalDiff, newState) =>
        assertBalanceInvariant(totalDiff)

        val recipient: Address = transfer.recipient.asInstanceOf[Address]
        if (transfer.sender.toAddress != recipient) {
          transfer.assetId match {
            case aid @ IssuedAsset(_) =>
              newState.balance(recipient) shouldBe 0
              newState.balance(recipient, aid) shouldBe transfer.amount.value
            case Waves =>
              newState.balance(recipient) shouldBe transfer.amount.value
          }
        }
      }
    }
  }

  val transferWithSmartAssetFee: Seq[(GenesisTransaction, IssueTransaction, IssueTransaction, TransferTransaction)] = {
    val master    = TxHelpers.signer(1)
    val recipient = TxHelpers.signer(2)

    val genesis    = TxHelpers.genesis(master.toAddress)
    val issue      = TxHelpers.issue(master, ENOUGH_AMT, version = TxVersion.V1)
    val feeIssue   = TxHelpers.issue(master, script = Some(ExprScript(CONST_BOOLEAN(true)).explicitGet()))
    val transferV1 = TxHelpers.transfer(master, recipient.toAddress, asset = issue.asset, feeAsset = feeIssue.asset, version = TxVersion.V1)
    val transferV2 = TxHelpers.transfer(master, recipient.toAddress, asset = issue.asset, feeAsset = feeIssue.asset)

    Seq(transferV1, transferV2).map { transfer =>
      (genesis, issue, feeIssue, transfer)
    }
  }

  property("handle transactions with amount + fee > Long.MaxValue") {
    val precs = {
      val master    = TxHelpers.signer(1)
      val recipient = TxHelpers.signer(2)

      val genesis  = TxHelpers.genesis(master.toAddress)
      val issue    = TxHelpers.issue(master, Long.MaxValue, version = TxVersion.V1)
      val asset    = issue.asset
      val transfer = TxHelpers.transfer(master, recipient.toAddress, Long.MaxValue, asset, fee = 2000000, version = TxVersion.V1)

      (genesis, issue, transfer)
    }

    val rdEnabled = TestFunctionalitySettings.Stub

    val rdDisabled = rdEnabled.copy(preActivatedFeatures =
      Map(
        BlockchainFeatures.SmartAccounts.id -> 0,
        BlockchainFeatures.SmartAssets.id   -> 0,
        BlockchainFeatures.FairPoS.id       -> 0
      )
    )

    val (genesis, issue, transfer) = precs

    assertDiffEi(Seq(TestBlock.create(Seq(genesis, issue))), TestBlock.create(Seq(transfer)), rdEnabled) { diffEi =>
      diffEi shouldBe an[Right[?, ?]]
    }

    assertDiffEi(Seq(TestBlock.create(Seq(genesis, issue))), TestBlock.create(Seq(transfer)), rdDisabled) { diffEi =>
      diffEi shouldBe Left(TransactionDiffer.TransactionValidationError(TxValidationError.OverflowError, transfer))
    }
  }

  property("fails, if smart asset used as a fee") {
    import smart.*

    transferWithSmartAssetFee.foreach { case (genesis, issue, fee, transfer) =>
      assertDiffAndState(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(issue, fee)), smartEnabledFS) { case (_, state) =>
        val diffOrError = TransferDiff(state)(
          transfer.sender.toAddress,
          transfer.recipient,
          transfer.amount.value,
          transfer.assetId,
          transfer.fee.value,
          transfer.feeAssetId
        )
        diffOrError shouldBe Left(GenericError("Smart assets can't participate in TransferTransactions as a fee"))
      }
    }
  }
}
