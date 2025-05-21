package com.gicsports.transaction

import com.gicsports.TestValues
import com.gicsports.account.AddressScheme
import com.gicsports.common.state.ByteStr
import com.gicsports.crypto.DigestLength
import com.gicsports.lang.ValidationError
import com.gicsports.test.PropSpec
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.TxValidationError.{InvalidName, TooBigArray}
import com.gicsports.transaction.assets.IssueTransaction.{MaxAssetDescriptionLength, MaxAssetNameLength, MinAssetNameLength}
import com.gicsports.transaction.assets.UpdateAssetInfoTransaction

class UpdateAssetInfoTransactionSpecification extends PropSpec {

  property("new asset name validation") {
    val invalidNames = Seq(
      "",
      "a" * (MinAssetNameLength - 1),
      "a" * (MaxAssetNameLength + 1),
      "~!|#$%^&*()_+=\";:/?><|\\][{}"
    )
    val validNames = Seq("a" * MinAssetNameLength, "a" * MaxAssetNameLength)

    invalidNames.foreach { name =>
       createUpdateAssetInfoTx(name = name) shouldBe Left(InvalidName)
    }

    validNames.foreach { name =>
      createUpdateAssetInfoTx(name = name) should beRight
    }
  }

  property("new asset description validation") {
    val invalidDescs = Seq("a" * (MaxAssetDescriptionLength + 1))
    val validDescs = Seq("", "a" * MaxAssetDescriptionLength)

    invalidDescs.foreach { desc =>
      createUpdateAssetInfoTx(description = desc) shouldBe Left(TooBigArray)
    }

    validDescs.foreach { desc =>
      createUpdateAssetInfoTx(description = desc) should beRight
    }
  }

  def createUpdateAssetInfoTx(name: String = "updated_name",
                              description: String = "updated_description"): Either[ValidationError, UpdateAssetInfoTransaction] =
    UpdateAssetInfoTransaction.create(
      version = TxVersion.V1,
      sender = TxHelpers.signer(1).publicKey,
      assetId = ByteStr.fill(DigestLength)(1),
      name = name,
      description = description,
      timestamp = System.currentTimeMillis(),
      feeAmount = TestValues.fee,
      feeAsset = Waves,
      proofs = Proofs.empty,
      chainId = AddressScheme.current.chainId
    )
}
