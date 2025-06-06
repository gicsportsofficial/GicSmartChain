package com.gicsports.it.sync.smartcontract.freecall

import com.google.protobuf.ByteString
import com.typesafe.config.Config
import com.gicsports.account.AddressScheme
import com.gicsports.features.BlockchainFeatures.ContinuationTransaction
import com.gicsports.it.NodeConfigs
import com.gicsports.it.NodeConfigs.Default
import com.gicsports.it.api.SyncGrpcApi.*
import com.gicsports.it.api.{PutDataResponse, StateChangesDetails}
import com.gicsports.it.sync.grpc.GrpcBaseTransactionSuite
import com.gicsports.it.sync.invokeExpressionFee
import com.gicsports.lang.directives.values.V6
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.protobuf.block.VanillaBlock
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.InvokeExpressionTransaction
import org.scalatest.{Assertion, CancelAfterFailure}

class InvokeExpressionGrpcSuite extends GrpcBaseTransactionSuite with CancelAfterFailure {
  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs
      .Builder(Default, 2, Seq.empty)
      .overrideBase(_.preactivatedFeatures((ContinuationTransaction.id, 1)))
      .buildNonConflicting()

  private val expr: ExprScript =
    TestCompiler(V6).compileFreeCall(
      """
        | [
        |   BooleanEntry("check", true)
        | ]
      """.stripMargin
    )

  test("successful applying to the state") {
    val id     = sender.broadcastInvokeExpression(firstAcc, expr, waitForTx = true).id
    val height = sender.getTransactionInfo(id).height.toInt

    val lastBlock          = sender.blockAt(height)
    val blockById          = sender.blockById(ByteString.copyFrom(lastBlock.id.value().arr))
    val blocksSeq          = sender.blockSeq(1, 100)
    val blocksSeqByAddress = sender.blockSeqByAddress(lastBlock.header.generator.toAddress.toString, 1, 100)
    List(
      findTxInBlock(lastBlock, id),
      findTxInBlock(blockById, id),
      findTxInBlockSeq(blocksSeq, id),
      findTxInBlockSeq(blocksSeqByAddress, id)
    ).foreach(checkTx)

    val stateChangesById      = sender.stateChanges(id)._2
    val stateChangesByAddress = sender.stateChanges(ByteString.copyFrom(firstAcc.toAddress.bytes)).head._2
    List(stateChangesById, stateChangesByAddress).foreach(checkStateChanges)

    sender.getDataByKey(firstAddress, "check").head.value.boolValue.get shouldBe true
  }

  private def findTxInBlock(b: VanillaBlock, id: String): InvokeExpressionTransaction =
    findTxInBlockSeq(Seq(b), id)

  private def findTxInBlockSeq(b: Seq[VanillaBlock], id: String): InvokeExpressionTransaction =
    b.flatMap(_.transactionData).find(_.id.value().toString == id).get.asInstanceOf[InvokeExpressionTransaction]

  private def checkTx(tx: InvokeExpressionTransaction): Assertion = {
    tx.fee.value shouldBe invokeExpressionFee
    tx.feeAssetId shouldBe Waves
    tx.sender shouldBe firstAcc.publicKey
    tx.expression shouldBe expr
    tx.version shouldBe 1
    tx.timestamp should be > 0L
    tx.proofs.size shouldBe 1
    tx.chainId shouldBe AddressScheme.current.chainId
  }

  private def checkStateChanges(s: StateChangesDetails): Assertion =
    s.data.head shouldBe PutDataResponse("boolean", true, "check")
}
