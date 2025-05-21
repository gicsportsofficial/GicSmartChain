package com.gicsports.state.diffs.smart.predef

import com.gicsports.account.{Address, Alias}
import com.gicsports.block.Block.BlockId
import com.gicsports.block.SignedBlockHeader
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.lang.ValidationError
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.Terms.CONST_BOOLEAN
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.lang.v1.traits.domain.Recipient
import com.gicsports.settings.BlockchainSettings
import com.gicsports.state.*
import com.gicsports.state.reader.LeaseDetails
import com.gicsports.test.PropSpec
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.script.ScriptRunner
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.{Asset, ERC20Address, Transaction}
import org.scalamock.scalatest.MockFactory
import shapeless.Coproduct

class MatcherBlockchainTest extends PropSpec with MockFactory with WithDomain {
  property("ScriptRunner.applyGeneric() avoids Blockchain calls") {
    val blockchain: Blockchain = new Blockchain {
      override def settings: BlockchainSettings                                                             = ???
      override def height: Int                                                                              = ???
      override def score: BigInt                                                                            = ???
      override def blockHeader(height: Int): Option[SignedBlockHeader]                                      = ???
      override def hitSource(height: Int): Option[ByteStr]                                                  = ???
      override def carryFee: Long                                                                           = ???
      override def heightOf(blockId: ByteStr): Option[Int]                                                  = ???
      override def approvedFeatures: Map[Short, Int]                                                        = ???
      override def activatedFeatures: Map[Short, Int]                                                       = ???
      override def featureVotes(height: Int): Map[Short, Int]                                               = ???
      override def blockReward(height: Int): Option[Long]                                                   = ???
      override def blockRewardVotes(height: Int): Seq[Long]                                                 = ???
      override def wavesAmount(height: Int): BigInt                                                         = ???
      override def transferById(id: ByteStr): Option[(Int, TransferTransaction)]                            = ???
      override def transactionInfo(id: ByteStr): Option[(TxMeta, Transaction)]                              = ???
      override def transactionMeta(id: ByteStr): Option[TxMeta]                                             = ???
      override def containsTransaction(tx: Transaction): Boolean                                            = ???
      override def assetDescription(id: Asset.IssuedAsset): Option[AssetDescription]                        = ???
      override def resolveAlias(a: Alias): Either[ValidationError, Address]                                 = ???
      override def leaseDetails(leaseId: ByteStr): Option[LeaseDetails]                                     = ???
      override def filledVolumeAndFee(orderId: ByteStr): VolumeAndFee                                       = ???
      override def balanceAtHeight(address: Address, height: Int, assetId: Asset): Option[(Int, Long)]      = ???
      override def balanceSnapshots(address: Address, from: Int, to: Option[BlockId]): Seq[BalanceSnapshot] = ???
      override def accountScript(address: Address): Option[AccountScriptInfo]                               = ???
      override def hasAccountScript(address: Address): Boolean                                              = ???
      override def assetScript(id: Asset.IssuedAsset): Option[AssetScriptInfo]                              = ???
      override def accountData(acc: Address, key: String): Option[DataEntry[?]]                             = ???
      override def hasData(address: Address): Boolean                                                       = ???
      override def leaseBalance(address: Address): LeaseBalance                                             = ???
      override def balance(address: Address, mayBeAssetId: Asset): Long                                     = ???
      override def resolveERC20Address(address: ERC20Address): Option[Asset.IssuedAsset]                    = ???
    }

    val tx = TransferTransaction.selfSigned(1.toByte, accountGen.sample.get, accountGen.sample.get.toAddress, Waves, 1, Waves, 1, ByteStr.empty, 0)
    val scripts =
      Seq(
        TestCompiler(V5).compileExpression("true"),
        TestCompiler(V5).compileContract(
          """
            |@Callable(i)
            |func foo() = []
            |""".stripMargin
        ),
        TestCompiler(V5).compileContract(
          """
            |@Callable(i)
            |func foo() = []
            |
            |@Verifier(tx)
            |func bar() = true
            |""".stripMargin
        )
      )

    scripts.foreach { script =>
      ScriptRunner
        .applyGeneric(
          Coproduct(tx.explicitGet()),
          blockchain,
          script,
          isAssetScript = false,
          Coproduct(Recipient.Address(ByteStr.empty)),
          defaultLimit = 2000,
          default = null,
          useCorrectScriptVersion = true,
          fixUnicodeFunctions = true,
          useNewPowPrecision = true,
          checkEstimatorSumOverflow = true,
          newEvaluatorMode = true,
          checkWeakPk = true,
          fixBigScriptField = true
        )
        ._3 shouldBe Right(CONST_BOOLEAN(true))
    }
  }
}
