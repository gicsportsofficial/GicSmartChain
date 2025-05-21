package com.gicsports.api.grpc.test

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import com.gicsports.account.Address
import com.gicsports.common.state.ByteStr
import com.gicsports.test.{FlatSpec, TestTime}
import com.gicsports.transaction.Asset.Waves
import com.gicsports.BlockchainStubHelpers
import com.gicsports.api.common.{CommonTransactionsApi, TransactionMeta}
import com.gicsports.api.grpc.TransactionsApiGrpcImpl
import com.gicsports.block.Block
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.ValidationError
import com.gicsports.protobuf.transaction.PBTransactions
import com.gicsports.state.{Blockchain, Height}
import com.gicsports.transaction.{Asset, CreateAliasTransaction, Transaction, TxHelpers, TxVersion}
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.transaction.TransactionType.TransactionType
import com.gicsports.transaction.utils.EthTxGenerator
import com.gicsports.utils.{DiffMatchers, EthHelpers}
import io.grpc.StatusException
import monix.execution.Scheduler
import monix.reactive.Observable
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.BeforeAndAfterAll

class GRPCBroadcastSpec
    extends FlatSpec
    with BeforeAndAfterAll
    with PathMockFactory
    with BlockchainStubHelpers
    with EthHelpers
    with DiffMatchers {
  // Fake NTP time
  val FakeTime: TestTime = TestTime(100)

  "GRPC broadcast" should "accept Exchange with ETH orders" in {
    import com.gicsports.transaction.assets.exchange.EthOrderSpec.{ethBuyOrder, ethSellOrder}

    val blockchain = createBlockchainStub { blockchain =>
      val sh = StubHelpers(blockchain)
      sh.creditBalance(ethBuyOrder.senderAddress, *)
      sh.creditBalance(ethSellOrder.senderAddress, *)
      sh.issueAsset(ByteStr(EthStubBytes32))
    }

    val transaction = TxHelpers.exchange(ethBuyOrder, ethSellOrder, price = 100, version = TxVersion.V3, timestamp = 100)
    FakeTime.setTime(transaction.timestamp)
    blockchain.assertBroadcast(transaction)
  }

  it should "reject eth transactions" in {
    val blockchain = createBlockchainStub { blockchain =>
      val sh = StubHelpers(blockchain)
      sh.creditBalance(TxHelpers.defaultEthAddress, Waves)
      sh.activateFeatures(BlockchainFeatures.BlockV5, BlockchainFeatures.SynchronousCalls)
    }

    val transaction = EthTxGenerator.generateEthTransfer(TxHelpers.defaultEthSigner, TxHelpers.secondAddress, 10, Waves)
    FakeTime.setTime(transaction.timestamp)
    intercept[Exception](blockchain.assertBroadcast(transaction)).toString should include("ETH transactions should not be broadcasted over gRPC")
  }

  //noinspection NotImplementedCode
  implicit class BlockchainBroadcastExt(blockchain: Blockchain) {
    def grpcTxApi: TransactionsApiGrpcImpl =
      new TransactionsApiGrpcImpl(blockchain, new CommonTransactionsApi {
        def aliasesOfAddress(address: Address): Observable[(Height, CreateAliasTransaction)] = ???
        def transactionById(txId: ByteStr): Option[TransactionMeta]                          = ???
        def unconfirmedTransactions: Seq[Transaction]                                        = ???
        def unconfirmedTransactionById(txId: ByteStr): Option[Transaction]                   = ???
        def calculateFee(tx: Transaction): Either[ValidationError, (Asset, Long, Long)]      = ???
        def transactionsByAddress(
            subject: Address,
            sender: Option[Address],
            transactionTypes: Set[TransactionType],
            fromId: Option[ByteStr]
        ): Observable[TransactionMeta]                                                     = ???
        def transactionProofs(transactionIds: List[ByteStr]): List[Block.TransactionProof] = ???
        def broadcastTransaction(tx: Transaction): Future[TracedResult[ValidationError, Boolean]] = {
          val differ = blockchain.stub.transactionDiffer(FakeTime)
          Future.successful(differ(tx).map(_ => true))
        }
      })(Scheduler.global)

    @throws[StatusException]("on failed broadcast")
    def assertBroadcast(tx: Transaction): Unit = {
      Await.result(grpcTxApi.broadcast(PBTransactions.protobuf(tx)), Duration.Inf)
    }
  }
}
