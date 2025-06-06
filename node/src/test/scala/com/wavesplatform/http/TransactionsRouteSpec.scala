package com.gicsports.http

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Route
import com.gicsports.account.KeyPair
import com.gicsports.api.common.{CommonTransactionsApi, TransactionMeta}
import com.gicsports.api.http.ApiError.{InvalidIds, *}
import com.gicsports.api.http.{RouteTimeout, TransactionsApiRoute}
import com.gicsports.block.Block
import com.gicsports.block.Block.TransactionProof
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.{Base58, *}
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.features.BlockchainFeatures as BF
import com.gicsports.history.{Domain, defaultSigner, settingsWithFeatures}
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.FunctionHeader
import com.gicsports.lang.v1.compiler.Terms.{CONST_BOOLEAN, CONST_LONG, FUNCTION_CALL}
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.lang.v1.traits.domain.LeaseCancel
import com.gicsports.network.TransactionPublisher
import com.gicsports.state.reader.LeaseDetails
import com.gicsports.state.{Blockchain, Height, InvokeScriptResult, TxMeta}
import com.gicsports.test.*
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.gicsports.transaction.serialization.impl.InvokeScriptTxSerializer
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.smart.script.trace.{AccountVerifierTrace, TracedResult}
import com.gicsports.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.utils.{EthTxGenerator, Signed}
import com.gicsports.transaction.{Asset, AssetIdLength, CreateAliasTransaction, TxHelpers, TxVersion}
import com.gicsports.utils.{EthEncoding, EthHelpers, Schedulers}
import com.gicsports.{BlockGen, BlockchainStubHelpers, TestValues, TestWallet}
import monix.reactive.Observable
import org.scalacheck.Gen.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import play.api.libs.json.*
import play.api.libs.json.Json.JsValueWrapper

import scala.concurrent.duration.*
import scala.concurrent.Future
import scala.util.Random

class TransactionsRouteSpec
    extends RouteSpec("/transactions")
    with RestAPISettingsHelper
    with MockFactory
    with BlockGen
    with OptionValues
    with TestWallet
    with WithDomain
    with EthHelpers
    with BlockchainStubHelpers {

  private val blockchain          = mock[Blockchain]
  private val utxPoolSynchronizer = mock[TransactionPublisher]
  private val addressTransactions = mock[CommonTransactionsApi]
  private val utxPoolSize         = mockFunction[Int]
  private val testTime            = new TestTime

  private val transactionsApiRoute = new TransactionsApiRoute(
    restAPISettings,
    addressTransactions,
    testWallet,
    blockchain,
    utxPoolSize,
    utxPoolSynchronizer,
    testTime,
    new RouteTimeout(60.seconds)(Schedulers.fixedPool(1, "heavy-request-scheduler"))
  )

  private val route = seal(transactionsApiRoute.route)

  private val invalidBase58Gen = alphaNumStr.map(_ + "0")

  routePath("/calculateFee") - {
    "GIC" in {
      val transferTx = Json.obj(
        "type"            -> 4,
        "version"         -> 1,
        "amount"          -> 1000000,
        "feeAssetId"      -> JsNull,
        "senderPublicKey" -> TestValues.keyPair.publicKey,
        "recipient"       -> TestValues.address
      )
      (addressTransactions.calculateFee _).expects(*).returning(Right((Asset.Waves, 2000000L, 0L))).once()

      Post(routePath("/calculateFee"), transferTx) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        (responseAs[JsObject] \ "feeAssetId").asOpt[String] shouldBe empty
        (responseAs[JsObject] \ "feeAmount").as[Long] shouldEqual 2000000
      }
    }

    "asset" in {
      val asset: IssuedAsset = TestValues.asset
      val transferTx = Json.obj(
        "type"            -> 4,
        "version"         -> 2,
        "amount"          -> 1000000,
        "feeAssetId"      -> asset.id.toString,
        "senderPublicKey" -> TestValues.keyPair.publicKey,
        "recipient"       -> TestValues.address
      )

      (addressTransactions.calculateFee _).expects(*).returning(Right((asset, 5L, 0L))).once()

      Post(routePath("/calculateFee"), transferTx) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        (responseAs[JsObject] \ "feeAssetId").as[String] shouldBe asset.id.toString
        (responseAs[JsObject] \ "feeAmount").as[Long] shouldEqual 5
      }
    }
  }

  private def mkRoute(d: Domain): Route =
    seal(
      new TransactionsApiRoute(
        restAPISettings,
        d.commonApi.transactions,
        testWallet,
        d.blockchain,
        () => 0,
        (t, _) => d.commonApi.transactions.broadcastTransaction(t),
        ntpTime,
        new RouteTimeout(60.seconds)(Schedulers.fixedPool(1, "heavy-request-scheduler"))
      ).route
    )

  "returns lease details for lease cancel transaction" in {
    val sender    = testWallet.generateNewAccount().get
    val recipient = testWallet.generateNewAccount().get

    val balances = Seq(
      AddrWithBalance(sender.toAddress, 10.waves),
      AddrWithBalance(recipient.toAddress, 10.waves)
    )

    withDomain(settingsWithFeatures(BF.SmartAccounts), balances) { d =>
      val lease       = LeaseTransaction.selfSigned(2.toByte, sender, recipient.toAddress, 5.waves, 0.02.waves, ntpTime.getTimestamp()).explicitGet()
      val leaseCancel = LeaseCancelTransaction.selfSigned(2.toByte, sender, lease.id(), 0.02.waves, ntpTime.getTimestamp()).explicitGet()
      val sealedRoute = mkRoute(d)

      d.appendBlock(lease)

      def expectedJson(status: String, cancelHeight: Option[Int] = None, cancelTransactionId: Option[ByteStr] = None): JsObject =
        Json
          .parse(s"""{
                    |  "type" : 9,
                    |  "id" : "${leaseCancel.id()}",
                    |  "sender" : "${sender.toAddress}",
                    |  "senderPublicKey" : "${sender.publicKey}",
                    |  "fee" : ${0.02.waves},
                    |  "feeAssetId" : null,
                    |  "timestamp" : ${leaseCancel.timestamp},
                    |  "proofs" : [ "${leaseCancel.signature}" ],
                    |  "version" : 2,
                    |  "leaseId" : "${lease.id()}",
                    |  "chainId" : 84,
                    |  "spentComplexity" : 0,
                    |  "lease" : {
                    |    "id" : "${lease.id()}",
                    |    "originTransactionId" : "${lease.id()}",
                    |    "sender" : "${sender.toAddress}",
                    |    "recipient" : "${recipient.toAddress}",
                    |    "amount" : ${5.waves},
                    |    "height" : 2,
                    |    "status" : "$status",
                    |    "cancelHeight" : ${cancelHeight.getOrElse("null")},
                    |    "cancelTransactionId" : ${cancelTransactionId.fold("null")("\"" + _ + "\"")}
                    |  }
                    |}""".stripMargin)
          .as[JsObject]

      d.utxPool.putIfNew(leaseCancel)

      withClue(routePath("/unconfirmed")) {
        Get(routePath(s"/unconfirmed")) ~> sealedRoute ~> check {
          responseAs[Seq[JsObject]].head should matchJson(expectedJson("active") - "spentComplexity")
        }
      }

      d.appendBlock(leaseCancel)

      val cancelTransactionJson = expectedJson("canceled", Some(3), Some(leaseCancel.id())) ++ Json.obj("height" -> 3)

      withClue(routePath("/address/{address}/limit/{limit}")) {
        Get(routePath(s"/address/${recipient.toAddress}/limit/10")) ~> sealedRoute ~> check {
          val json = (responseAs[JsArray] \ 0 \ 0).as[JsObject]
          json should matchJson(cancelTransactionJson)
        }
      }

      withClue(routePath("/info/{id}")) {
        Get(routePath(s"/info/${leaseCancel.id()}")) ~> sealedRoute ~> check {
          responseAs[JsObject] should matchJson(cancelTransactionJson)
        }
      }
    }
  }

  routePath("/address/{address}/limit/{limit}") - {
    val bytes32StrGen = bytes32gen.map(Base58.encode)
    val addressGen    = accountGen.map(_.toAddress.toString)

    "handles parameter errors with corresponding responses" - {
      "invalid address" in {
        forAll(bytes32StrGen) { badAddress =>
          Get(routePath(s"/address/$badAddress/limit/1")) ~> route should produce(InvalidAddress)
        }
      }

      "invalid limit" - {
        "limit is too big" in {
          forAll(addressGen, choose(MaxTransactionsPerRequest + 1, Int.MaxValue).label("limitExceeded")) { case (address, limit) =>
            Get(routePath(s"/address/$address/limit/$limit")) ~> route should produce(TooBigArrayAllocation)
          }
        }
      }

      "invalid after" in {
        forAll(addressGen, choose(1, MaxTransactionsPerRequest).label("limitCorrect"), invalidBase58Gen) { case (address, limit, invalidBase58) =>
          Get(routePath(s"/address/$address/limit/$limit?after=$invalidBase58")) ~> route ~> check {
            status shouldEqual StatusCodes.BadRequest
            (responseAs[JsObject] \ "message").as[String] shouldEqual s"Unable to decode transaction id $invalidBase58"
          }
        }
      }
    }

    "returns 200 if correct params provided" - {
      "address and limit" in {
        forAll(addressGen, choose(1, MaxTransactionsPerRequest).label("limitCorrect")) { case (address, limit) =>
          (addressTransactions.aliasesOfAddress _).expects(*).returning(Observable.empty).once()
          (addressTransactions.transactionsByAddress _).expects(*, *, *, None).returning(Observable.empty).once()
          Get(routePath(s"/address/$address/limit/$limit")) ~> route ~> check {
            status shouldEqual StatusCodes.OK
          }
        }
      }

      "address, limit and after" in {
        forAll(addressGen, choose(1, MaxTransactionsPerRequest).label("limitCorrect"), bytes32StrGen) { case (address, limit, txId) =>
          (addressTransactions.aliasesOfAddress _).expects(*).returning(Observable.empty).once()
          (addressTransactions.transactionsByAddress _).expects(*, *, *, *).returning(Observable.empty).once()
          Get(routePath(s"/address/$address/limit/$limit?after=$txId")) ~> route ~> check {
            status shouldEqual StatusCodes.OK
          }
        }
      }
    }

    "provides stateChanges" in forAll(accountGen) { account =>
      val transaction = TxHelpers.invoke(account.toAddress)

      (() => blockchain.activatedFeatures).expects().returns(Map.empty).anyNumberOfTimes()
      (addressTransactions.aliasesOfAddress _).expects(*).returning(Observable.empty).once()
      (addressTransactions.transactionsByAddress _)
        .expects(account.toAddress, *, *, None)
        .returning(Observable(TransactionMeta.Invoke(Height(1), transaction, succeeded = true, 0L, Some(InvokeScriptResult()))))
        .once()

      Get(routePath(s"/address/${account.toAddress}/limit/1")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        (responseAs[JsArray] \ 0 \ 0 \ "stateChanges").as[JsObject] shouldBe Json.toJsObject(InvokeScriptResult())
      }
    }

    "provides lease and lease cancel actions stateChanges" in {
      val invokeAddress    = accountGen.sample.get.toAddress
      val leaseId1         = ByteStr(bytes32gen.sample.get)
      val leaseId2         = ByteStr(bytes32gen.sample.get)
      val leaseCancelId    = ByteStr(bytes32gen.sample.get)
      val recipientAddress = accountGen.sample.get.toAddress
      val recipientAlias   = aliasGen.sample.get
      val invoke           = TxHelpers.invoke(invokeAddress)
      val scriptResult = InvokeScriptResult(
        leases = Seq(InvokeScriptResult.Lease(recipientAddress, 100, 1, leaseId1), InvokeScriptResult.Lease(recipientAlias, 200, 3, leaseId2)),
        leaseCancels = Seq(LeaseCancel(leaseCancelId))
      )

      (blockchain.leaseDetails _)
        .expects(leaseId1)
        .returning(Some(LeaseDetails(TestValues.keyPair.publicKey, TestValues.address, 123, LeaseDetails.Status.Active, leaseId1, 1)))
        .anyNumberOfTimes()
      (blockchain.leaseDetails _)
        .expects(leaseId2)
        .returning(Some(LeaseDetails(TestValues.keyPair.publicKey, TestValues.address, 123, LeaseDetails.Status.Active, leaseId2, 1)))
        .anyNumberOfTimes()
      (blockchain.leaseDetails _)
        .expects(leaseCancelId)
        .returning(
          Some(
            LeaseDetails(
              TestValues.keyPair.publicKey,
              TestValues.address,
              123,
              LeaseDetails.Status.Cancelled(2, Some(leaseCancelId)),
              leaseCancelId,
              1
            )
          )
        )
        .anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(leaseId1).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(leaseId2).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(leaseCancelId).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()

      (() => blockchain.activatedFeatures).expects().returning(Map.empty).anyNumberOfTimes()
      (addressTransactions.aliasesOfAddress _).expects(*).returning(Observable.empty).once()
      (addressTransactions.transactionsByAddress _)
        .expects(invokeAddress, *, *, None)
        .returning(Observable(TransactionMeta.Invoke(Height(1), invoke, succeeded = true, 0L, Some(scriptResult))))
        .once()

      Get(routePath(s"/address/${invokeAddress}/limit/1")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val json = (responseAs[JsArray] \ 0 \ 0 \ "stateChanges").as[JsObject]
        json should matchJson(s"""{
                                 |  "data": [],
                                 |  "transfers": [],
                                 |  "issues": [],
                                 |  "reissues": [],
                                 |  "burns": [],
                                 |  "sponsorFees": [],
                                 |  "leases": [
                                 |    {
                                 |      "id": "$leaseId1",
                                 |      "originTransactionId": "$leaseId1",
                                 |      "sender": "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |      "recipient": "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |      "amount": 123,
                                 |      "height": 1,
                                 |      "status":"active",
                                 |      "cancelHeight" : null,
                                 |      "cancelTransactionId" : null
                                 |    },
                                 |    {
                                 |      "id": "$leaseId2",
                                 |      "originTransactionId": "$leaseId2",
                                 |      "sender": "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |      "recipient": "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |      "amount": 123,
                                 |      "height": 1,
                                 |      "status":"active",
                                 |      "cancelHeight" : null,
                                 |      "cancelTransactionId" : null
                                 |    }
                                 |  ],
                                 |  "leaseCancels": [
                                 |    {
                                 |      "id": "$leaseCancelId",
                                 |      "originTransactionId": "$leaseCancelId",
                                 |      "sender": "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |      "recipient": "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |      "amount": 123,
                                 |      "height": 1,
                                 |      "status":"canceled",
                                 |      "cancelHeight" : 2,
                                 |      "cancelTransactionId" : "$leaseCancelId"
                                 |    }
                                 |  ],
                                 |  "invokes": []
                                 |}""".stripMargin)
      }
    }
  }

  routePath("/info/{id}") - {
    "returns meta for eth transfer" in {
      val blockchain = createBlockchainStub { blockchain =>
        blockchain.stub.creditBalance(TxHelpers.defaultEthAddress, Waves)
        blockchain.stub.activateAllFeatures()
      }

      val differ          = blockchain.stub.transactionDiffer().andThen(_.resultE.explicitGet())
      val transaction     = EthTxGenerator.generateEthTransfer(TxHelpers.defaultEthSigner, TxHelpers.secondAddress, 10, Waves)
      val diff            = differ(transaction)
      val transactionsApi = stub[CommonTransactionsApi]
      (transactionsApi.transactionById _)
        .when(transaction.id())
        .returning(
          Some(
            TransactionMeta.Ethereum(
              Height(1),
              transaction,
              succeeded = true,
              15L,
              diff.ethereumTransactionMeta.values.headOption,
              diff.scriptResults.values.headOption
            )
          )
        )

      val route = seal(transactionsApiRoute.copy(blockchain = blockchain, commonApi = transactionsApi).route)
      Get(routePath(s"/info/${transaction.id()}")) ~> route ~> check {
        responseAs[JsObject] should matchJson(s"""{
                                                 |  "type" : 18,
                                                 |  "id" : "${transaction.id()}",
                                                 |  "fee" : 2000000,
                                                 |  "feeAssetId" : null,
                                                 |  "timestamp" : ${transaction.timestamp},
                                                 |  "version" : 1,
                                                 |  "chainId" : 84,
                                                 |  "bytes" : "${EthEncoding.toHexString(transaction.bytes())}",
                                                 |  "sender" : "3NByUD1YE9SQPzmf2KqVqrjGMutNSfc4oBC",
                                                 |  "senderPublicKey" : "5vwTDMooR7Hp57MekN7qHz7fHNVrkn2Nx4CiWdq4cyBR4LNnZWYAr7UfBbzhmSvtNkv6e45aJ4Q4aKCSinyHVw33",
                                                 |  "height" : 1,
                                                 |  "spentComplexity": 15,
                                                 |  "applicationStatus" : "succeeded",
                                                 |  "payload" : {
                                                 |    "type" : "transfer",
                                                 |    "recipient" : "3MuVqVJGmFsHeuFni5RbjRmALuGCkEwzZtC",
                                                 |    "asset" : null,
                                                 |    "amount" : 10
                                                 |  }
                                                 |}""".stripMargin)
      }
    }

    "returns meta and state changes for eth invoke" in {
      val blockchain = createBlockchainStub { blockchain =>
        blockchain.stub.creditBalance(TxHelpers.defaultEthAddress, Waves)
        blockchain.stub.setScript(
          TxHelpers.secondAddress,
          TxHelpers.scriptV5("""@Callable(i)
                               |func test() = []
                               |""".stripMargin)
        )
        blockchain.stub.activateAllFeatures()
      }

      val differ          = blockchain.stub.transactionDiffer().andThen(_.resultE.explicitGet())
      val transaction     = EthTxGenerator.generateEthInvoke(TxHelpers.defaultEthSigner, TxHelpers.secondAddress, "test", Nil, Nil)
      val diff            = differ(transaction)
      val transactionsApi = stub[CommonTransactionsApi]
      (transactionsApi.transactionById _)
        .when(transaction.id())
        .returning(
          Some(
            TransactionMeta.Ethereum(
              Height(1),
              transaction,
              succeeded = true,
              15L,
              diff.ethereumTransactionMeta.values.headOption,
              diff.scriptResults.values.headOption
            )
          )
        )

      val route = seal(transactionsApiRoute.copy(blockchain = blockchain, commonApi = transactionsApi).route)
      Get(routePath(s"/info/${transaction.id()}")) ~> route ~> check {
        responseAs[JsObject] should matchJson(s"""{
                                                 |  "type" : 18,
                                                 |  "id" : "${transaction.id()}",
                                                 |  "fee" : 10000000,
                                                 |  "feeAssetId" : null,
                                                 |  "timestamp" : ${transaction.timestamp},
                                                 |  "version" : 1,
                                                 |  "chainId" : 84,
                                                 |  "bytes" : "${EthEncoding.toHexString(transaction.bytes())}",
                                                 |  "sender" : "3NByUD1YE9SQPzmf2KqVqrjGMutNSfc4oBC",
                                                 |  "senderPublicKey" : "5vwTDMooR7Hp57MekN7qHz7fHNVrkn2Nx4CiWdq4cyBR4LNnZWYAr7UfBbzhmSvtNkv6e45aJ4Q4aKCSinyHVw33",
                                                 |  "height" : 1,
                                                 |  "spentComplexity": 15,
                                                 |  "applicationStatus" : "succeeded",
                                                 |  "payload" : {
                                                 |    "type" : "invocation",
                                                 |    "dApp" : "3MuVqVJGmFsHeuFni5RbjRmALuGCkEwzZtC",
                                                 |    "call" : {
                                                 |      "function" : "test",
                                                 |      "args" : [ ]
                                                 |    },
                                                 |    "payment" : [ ],
                                                 |    "stateChanges" : {
                                                 |      "data" : [ ],
                                                 |      "transfers" : [ ],
                                                 |      "issues" : [ ],
                                                 |      "reissues" : [ ],
                                                 |      "burns" : [ ],
                                                 |      "sponsorFees" : [ ],
                                                 |      "leases" : [ ],
                                                 |      "leaseCancels" : [ ],
                                                 |      "invokes" : [ ]
                                                 |    }
                                                 |  }
                                                 |}""".stripMargin)
      }
    }

    "returns lease tx for lease cancel tx" in {
      val lease       = TxHelpers.lease()
      val leaseCancel = TxHelpers.leaseCancel(lease.id())

      val blockchain = createBlockchainStub { blockchain =>
        (blockchain.transactionInfo _).when(lease.id()).returns(Some(TxMeta(Height(1), true, 0L) -> lease))
        (blockchain.transactionInfo _).when(leaseCancel.id()).returns(Some((TxMeta(Height(1), true, 0L) -> leaseCancel)))
      }

      val transactionsApi = stub[CommonTransactionsApi]
      (transactionsApi.transactionById _).when(lease.id()).returns(Some(TransactionMeta.Default(Height(1), lease, succeeded = true, 0L)))
      (transactionsApi.transactionById _).when(leaseCancel.id()).returns(Some(TransactionMeta.Default(Height(1), leaseCancel, succeeded = true, 0L)))
      (blockchain.transactionMeta _).when(lease.id()).returns(Some(TxMeta(Height(1), true, 0L)))
      (blockchain.leaseDetails _)
        .when(lease.id())
        .returns(
          Some(
            LeaseDetails(lease.sender, lease.recipient, lease.amount.value, LeaseDetails.Status.Cancelled(2, Some(leaseCancel.id())), lease.id(), 1)
          )
        )

      val route = transactionsApiRoute.copy(blockchain = blockchain, commonApi = transactionsApi).route
      Get(routePath(s"/info/${leaseCancel.id()}")) ~> route ~> check {
        val json = responseAs[JsObject]
        json shouldBe Json.parse(s"""{
                                    |  "type" : 9,
                                    |  "id" : "${leaseCancel.id()}",
                                    |  "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                    |  "senderPublicKey" : "9BUoYQYq7K38mkk61q8aMH9kD9fKSVL1Fib7FbH6nUkQ",
                                    |  "fee" : 2000000,
                                    |  "feeAssetId" : null,
                                    |  "timestamp" : ${leaseCancel.timestamp},
                                    |  "proofs" : [ "${leaseCancel.signature}" ],
                                    |  "version" : 2,
                                    |  "leaseId" : "${lease.id()}",
                                    |  "chainId" : 84,
                                    |  "height" : 1,
                                    |  "applicationStatus" : "succeeded",
                                    |  "spentComplexity": 0,
                                    |  "lease" : {
                                    |    "id" : "${lease.id()}",
                                    |    "originTransactionId" : "${lease.id()}",
                                    |    "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                    |    "recipient" : "3MuVqVJGmFsHeuFni5RbjRmALuGCkEwzZtC",
                                    |    "amount" : 1000000000,
                                    |    "height" : 1,
                                    |    "status" : "canceled",
                                    |    "cancelHeight" : 2,
                                    |    "cancelTransactionId" : "${leaseCancel.id()}"
                                    |  }
                                    |}""".stripMargin)
      }
    }

    "handles invalid signature" in {
      forAll(invalidBase58Gen) { invalidBase58 =>
        Get(routePath(s"/info/$invalidBase58")) ~> route should produce(InvalidTransactionId("Wrong char"), matchMsg = true)
      }

      Get(routePath(s"/info/")) ~> route should produce(InvalidTransactionId("Transaction ID was not specified"))
      Get(routePath(s"/info")) ~> route should produce(InvalidTransactionId("Transaction ID was not specified"))
    }

    "working properly otherwise" in {
      val txAvailability = for {
        tx                           <- randomTransactionGen
        height                       <- posNum[Int]
        acceptFailedActivationHeight <- posNum[Int]
        succeed                      <- if (height >= acceptFailedActivationHeight) Arbitrary.arbBool.arbitrary else Gen.const(true)
      } yield (tx, succeed, height, acceptFailedActivationHeight)

      forAll(txAvailability) { case (tx, succeed, height, acceptFailedActivationHeight) =>
        (addressTransactions.transactionById _).expects(tx.id()).returning(Some(TransactionMeta.Default(Height(height), tx, succeed, 0L))).once()
        (() => blockchain.activatedFeatures)
          .expects()
          .returning(Map(BF.BlockV5.id -> acceptFailedActivationHeight))
          .anyNumberOfTimes()

        def validateResponse(): Unit = {
          status shouldEqual StatusCodes.OK

          val extraFields = Seq(
            (if (blockchain.isFeatureActivated(BF.BlockV5, height))
               Json.obj("applicationStatus" -> JsString(if (succeed) "succeeded" else "script_execution_failed"))
             else Json.obj()),
            Json.obj("height" -> height, "spentComplexity" -> 0)
          ).reduce(_ ++ _)

          responseAs[JsValue] should matchJson(tx.json() ++ extraFields)
        }

        Get(routePath(s"/info/${tx.id().toString}")) ~> route ~> check(validateResponse())
      }
    }

    "provides stateChanges" in forAll(accountGen) { account =>
      val transaction = TxHelpers.invoke(account.toAddress)

      (() => blockchain.activatedFeatures).expects().returns(Map.empty).anyNumberOfTimes()
      (addressTransactions.transactionById _)
        .expects(transaction.id())
        .returning(Some(TransactionMeta.Invoke(Height(1), transaction, succeeded = true, 0L, Some(InvokeScriptResult()))))
        .once()

      Get(routePath(s"/info/${transaction.id()}")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        (responseAs[JsObject] \ "stateChanges").as[JsObject] shouldBe Json.toJsObject(InvokeScriptResult())
      }
    }

    "provides lease and lease cancel action stateChanges" in {
      val invokeAddress    = accountGen.sample.get.toAddress
      val recipientAddress = accountGen.sample.get.toAddress
      val recipientAlias   = aliasGen.sample.get

      val leaseId1      = ByteStr(bytes32gen.sample.get)
      val leaseId2      = ByteStr(bytes32gen.sample.get)
      val leaseCancelId = ByteStr(bytes32gen.sample.get)

      val nestedInvokeAddress = accountGen.sample.get.toAddress
      val nestedLeaseId       = ByteStr(bytes32gen.sample.get)
      val nestedLeaseCancelId = ByteStr(bytes32gen.sample.get)

      val invoke = TxHelpers.invoke(invokeAddress)
      val scriptResult = InvokeScriptResult(
        leases = Seq(InvokeScriptResult.Lease(recipientAddress, 100, 1, leaseId1), InvokeScriptResult.Lease(recipientAlias, 200, 3, leaseId2)),
        leaseCancels = Seq(LeaseCancel(leaseCancelId)),
        invokes = Seq(
          InvokeScriptResult.Invocation(
            nestedInvokeAddress,
            InvokeScriptResult.Call("nested", Nil),
            Nil,
            InvokeScriptResult(
              leases = Seq(InvokeScriptResult.Lease(recipientAddress, 100, 1, nestedLeaseId)),
              leaseCancels = Seq(LeaseCancel(nestedLeaseCancelId))
            )
          )
        )
      )

      (blockchain.leaseDetails _)
        .expects(leaseId1)
        .returning(Some(LeaseDetails(TestValues.keyPair.publicKey, TestValues.address, 123, LeaseDetails.Status.Active, leaseId1, 1)))
        .anyNumberOfTimes()
      (blockchain.leaseDetails _)
        .expects(leaseId2)
        .returning(Some(LeaseDetails(TestValues.keyPair.publicKey, TestValues.address, 123, LeaseDetails.Status.Active, leaseId2, 1)))
        .anyNumberOfTimes()
      (blockchain.leaseDetails _)
        .expects(leaseCancelId)
        .returning(
          Some(
            LeaseDetails(
              TestValues.keyPair.publicKey,
              TestValues.address,
              123,
              LeaseDetails.Status.Cancelled(2, Some(leaseCancelId)),
              leaseCancelId,
              1
            )
          )
        )
        .anyNumberOfTimes()
      (blockchain.leaseDetails _)
        .expects(nestedLeaseId)
        .returning(Some(LeaseDetails(TestValues.keyPair.publicKey, TestValues.address, 123, LeaseDetails.Status.Active, nestedLeaseId, 1)))
        .anyNumberOfTimes()
      (blockchain.leaseDetails _)
        .expects(nestedLeaseCancelId)
        .returning(
          Some(
            LeaseDetails(
              TestValues.keyPair.publicKey,
              TestValues.address,
              123,
              LeaseDetails.Status.Cancelled(2, Some(nestedLeaseCancelId)),
              nestedLeaseCancelId,
              1
            )
          )
        )
        .anyNumberOfTimes()

      (blockchain.transactionMeta _).expects(leaseId1).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(leaseId2).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(leaseCancelId).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(nestedLeaseId).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()
      (blockchain.transactionMeta _).expects(nestedLeaseCancelId).returning(Some(TxMeta(Height(1), true, 0L))).anyNumberOfTimes()

      (() => blockchain.activatedFeatures).expects().returns(Map.empty).anyNumberOfTimes()
      (addressTransactions.transactionById _)
        .expects(invoke.id())
        .returning(Some(TransactionMeta.Invoke(Height(1), invoke, succeeded = true, 0L, Some(scriptResult))))
        .once()

      Get(routePath(s"/info/${invoke.id()}")) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val json = (responseAs[JsObject] \ "stateChanges").as[JsObject]
        json should matchJson(s"""{
                                 |  "data" : [ ],
                                 |  "transfers" : [ ],
                                 |  "issues" : [ ],
                                 |  "reissues" : [ ],
                                 |  "burns" : [ ],
                                 |  "sponsorFees" : [ ],
                                 |  "leases" : [ {
                                 |    "id" : "$leaseId1",
                                 |    "originTransactionId" : "$leaseId1",
                                 |    "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |    "recipient" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |    "amount" : 123,
                                 |    "height" : 1,
                                 |    "status":"active",
                                 |    "cancelHeight" : null,
                                 |    "cancelTransactionId" : null
                                 |  }, {
                                 |    "id" : "$leaseId2",
                                 |    "originTransactionId" : "$leaseId2",
                                 |    "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |    "recipient" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |    "amount" : 123,
                                 |    "height" : 1,
                                 |    "status":"active",
                                 |    "cancelHeight" : null,
                                 |    "cancelTransactionId" : null
                                 |  } ],
                                 |  "leaseCancels" : [ {
                                 |    "id" : "$leaseCancelId",
                                 |    "originTransactionId" : "$leaseCancelId",
                                 |    "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |    "recipient" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |    "amount" : 123,
                                 |    "height" : 1,
                                 |    "status" : "canceled",
                                 |    "cancelHeight" : 2,
                                 |    "cancelTransactionId" : "$leaseCancelId"
                                 |  } ],
                                 |  "invokes" : [ {
                                 |    "dApp" : "$nestedInvokeAddress",
                                 |    "call" : {
                                 |      "function" : "nested",
                                 |      "args" : [ ]
                                 |    },
                                 |    "payment" : [ ],
                                 |    "stateChanges" : {
                                 |      "data" : [ ],
                                 |      "transfers" : [ ],
                                 |      "issues" : [ ],
                                 |      "reissues" : [ ],
                                 |      "burns" : [ ],
                                 |      "sponsorFees" : [ ],
                                 |      "leases" : [ {
                                 |        "id" : "$nestedLeaseId",
                                 |        "originTransactionId" : "$nestedLeaseId",
                                 |        "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |        "recipient" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |        "amount" : 123,
                                 |        "height" : 1,
                                 |        "status":"active",
                                 |        "cancelHeight" : null,
                                 |        "cancelTransactionId" : null
                                 |      } ],
                                 |      "leaseCancels" : [ {
                                 |        "id" : "$nestedLeaseCancelId",
                                 |        "originTransactionId" : "$nestedLeaseCancelId",
                                 |        "sender" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |        "recipient" : "3MtGzgmNa5fMjGCcPi5nqMTdtZkfojyWHL9",
                                 |        "amount" : 123,
                                 |        "height" : 1,
                                 |        "status" : "canceled",
                                 |        "cancelHeight" : 2,
                                 |        "cancelTransactionId" : "$nestedLeaseCancelId"
                                 |      } ],
                                 |      "invokes" : [ ]
                                 |    }
                                 |  } ]
                                 |}
                                 |""".stripMargin)
      }
    }

    "handles multiple ids" in {
      val inputLimitErrMsg = TooBigArrayAllocation(transactionsApiRoute.settings.transactionsByAddressLimit).message
      val emptyInputErrMsg = "Transaction ID was not specified"

      val txCount = 5
      val txs     = (1 to txCount).map(_ => TxHelpers.invoke(TxHelpers.defaultSigner.toAddress))
      txs.foreach(tx =>
        (addressTransactions.transactionById _)
          .expects(tx.id())
          .returns(Some(TransactionMeta.Invoke(Height(1), tx, succeeded = true, 85L, Some(InvokeScriptResult()))))
          .anyNumberOfTimes()
      )

      (() => blockchain.activatedFeatures).expects().returns(Map(BF.BlockV5.id -> 1)).anyNumberOfTimes()

      def checkResponse(txs: Seq[InvokeScriptTransaction]): Unit = txs.zip(responseAs[JsArray].value) foreach { case (tx, json) =>
        val extraFields =
          Json.obj("height" -> 1, "spentComplexity" -> 85, "applicationStatus" -> "succeeded", "stateChanges" -> InvokeScriptResult())
        json shouldBe (tx.json() ++ extraFields)
      }

      def checkErrorResponse(errMsg: String): Unit = {
        response.status shouldBe StatusCodes.BadRequest
        (responseAs[JsObject] \ "message").as[String] shouldBe errMsg
      }

      val maxLimitTxs      = Seq.fill(transactionsApiRoute.settings.transactionsByAddressLimit)(txs.head)
      val moreThanLimitTxs = txs.head +: maxLimitTxs

      Get(routePath(s"/info?${txs.map("id=" + _.id()).mkString("&")}")) ~> route ~> check(checkResponse(txs))
      Get(routePath(s"/info?${maxLimitTxs.map("id=" + _.id()).mkString("&")}")) ~> route ~> check(checkResponse(maxLimitTxs))
      Get(routePath(s"/info?${moreThanLimitTxs.map("id=" + _.id()).mkString("&")}")) ~> route ~> check(checkErrorResponse(inputLimitErrMsg))
      Get(routePath("/info")) ~> route ~> check(checkErrorResponse(emptyInputErrMsg))

      Post(routePath("/info"), FormData(txs.map("id" -> _.id().toString)*)) ~> route ~> check(checkResponse(txs))
      Post(routePath("/info"), FormData(maxLimitTxs.map("id" -> _.id().toString)*)) ~> route ~> check(checkResponse(maxLimitTxs))
      Post(routePath("/info"), FormData(moreThanLimitTxs.map("id" -> _.id().toString)*)) ~> route ~> check(checkErrorResponse(inputLimitErrMsg))
      Post(routePath("/info"), FormData()) ~> route ~> check(checkErrorResponse(emptyInputErrMsg))

      Post(
        routePath("/info"),
        HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> Json.arr(txs.map(_.id().toString: JsValueWrapper)*)).toString())
      ) ~> route ~> check(
        checkResponse(txs)
      )
      Post(
        routePath("/info"),
        HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> Json.arr(maxLimitTxs.map(_.id().toString: JsValueWrapper)*)).toString())
      ) ~> route ~> check(
        checkResponse(maxLimitTxs)
      )
      Post(
        routePath("/info"),
        HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> Json.arr(moreThanLimitTxs.map(_.id().toString: JsValueWrapper)*)).toString())
      ) ~> route ~> check(checkErrorResponse(inputLimitErrMsg))
      Post(
        routePath("/info"),
        HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> JsArray.empty).toString())
      ) ~> route ~> check(checkErrorResponse(emptyInputErrMsg))
    }
  }

  routePath("/status/{signature}") - {
    "handles invalid signature" in {
      forAll(invalidBase58Gen) { invalidBase58 =>
        Get(routePath(s"/status?id=$invalidBase58")) ~> route should produce(InvalidIds(Seq(invalidBase58)))
      }
    }

    "handles empty request" in {
      Get(routePath(s"/status?")) ~> route should produce(CustomValidationError("Empty request"))
    }

    "working properly otherwise" in {
      val txAvailability = for {
        tx                           <- randomTransactionGen
        height                       <- Gen.chooseNum(1, 1000)
        acceptFailedActivationHeight <- Gen.chooseNum(1, 1000)
        succeed                      <- if (height >= acceptFailedActivationHeight) Arbitrary.arbBool.arbitrary else Gen.const(true)
      } yield (tx, height, acceptFailedActivationHeight, succeed)

      forAll(txAvailability) { case (tx, height, acceptFailedActivationHeight, succeed) =>
        (blockchain.transactionInfo _).expects(tx.id()).returning(Some(TxMeta(Height(height), succeed, 93L) -> tx)).anyNumberOfTimes()
        (() => blockchain.height).expects().returning(1000).anyNumberOfTimes()
        (() => blockchain.activatedFeatures)
          .expects()
          .returning(Map(BF.BlockV5.id -> acceptFailedActivationHeight))
          .anyNumberOfTimes()

        Get(routePath(s"/status?id=${tx.id().toString}&id=${tx.id().toString}")) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          val obj = {
            val common = Json.obj(
              "id"              -> tx.id().toString,
              "status"          -> "confirmed",
              "height"          -> JsNumber(height),
              "confirmations"   -> JsNumber(1000 - height),
              "spentComplexity" -> 93
            )
            val applicationStatus =
              if (blockchain.isFeatureActivated(BF.BlockV5, height))
                Json.obj("applicationStatus" -> JsString(if (succeed) "succeeded" else "script_execution_failed"))
              else Json.obj()
            common ++ applicationStatus
          }
          responseAs[JsValue] shouldEqual Json.arr(obj, obj)
        }
        Post(routePath("/status"), Json.obj("ids" -> Seq(tx.id().toString, tx.id().toString))) ~> route ~> check {
          status shouldEqual StatusCodes.OK
        }
      }
    }
  }

  routePath("/unconfirmed") - {
    "returns the list of unconfirmed transactions" in {
      val g = for {
        i <- chooseNum(0, 20)
        t <- listOfN(i, randomTransactionGen)
      } yield t

      forAll(g) { txs =>
        (() => addressTransactions.unconfirmedTransactions).expects().returning(txs).once()
        Get(routePath("/unconfirmed")) ~> route ~> check {
          val resp = responseAs[Seq[JsValue]]
          for ((r, t) <- resp.zip(txs)) {
            if ((r \ "version").as[Int] == 1) {
              (r \ "signature").as[String] shouldEqual t.proofs.proofs.head.toString
            } else {
              (r \ "proofs").as[Seq[String]] shouldEqual t.proofs.proofs.map(_.toString)
            }
          }
        }
      }
    }
  }

  routePath("/unconfirmed/size") - {
    "returns the size of unconfirmed transactions" in {
      val g = for {
        i <- chooseNum(0, 20)
        t <- listOfN(i, randomTransactionGen)
      } yield t

      forAll(g) { txs =>
        utxPoolSize.expects().returning(txs.size).once()
        Get(routePath("/unconfirmed/size")) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[JsValue] shouldEqual Json.obj("size" -> JsNumber(txs.size))
        }
      }
    }
  }

  routePath("/unconfirmed/info/{id}") - {
    "handles invalid signature" in {
      forAll(invalidBase58Gen) { invalidBase58 =>
        Get(routePath(s"/unconfirmed/info/$invalidBase58")) ~> route should produce(InvalidTransactionId("Wrong char"), matchMsg = true)
      }

      Get(routePath(s"/unconfirmed/info/")) ~> route should produce(InvalidSignature)
      Get(routePath(s"/unconfirmed/info")) ~> route should produce(InvalidSignature)
    }

    "working properly otherwise" in {
      forAll(randomTransactionGen) { tx =>
        (addressTransactions.unconfirmedTransactionById _).expects(tx.id()).returns(Some(tx)).once()
        Get(routePath(s"/unconfirmed/info/${tx.id().toString}")) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[JsValue] shouldEqual tx.json()
        }
      }
    }
  }

  routePath("/sign") - {
    "function call without args" in {
      val acc1 = testWallet.generateNewAccount().get
      val acc2 = testWallet.generateNewAccount().get

      val funcName          = "func"
      val funcWithoutArgs   = Json.obj("function" -> funcName)
      val funcWithEmptyArgs = Json.obj("function" -> funcName, "args" -> JsArray.empty)
      val funcWithArgs = InvokeScriptTxSerializer.functionCallToJson(
        FUNCTION_CALL(
          FunctionHeader.User(funcName),
          List(CONST_LONG(1), CONST_BOOLEAN(true))
        )
      )

      def invoke(func: JsObject, expectedArgsLength: Int): Unit = {
        val ist = Json.obj(
          "type"       -> InvokeScriptTransaction.typeId,
          "version"    -> Gen.oneOf(InvokeScriptTransaction.supportedVersions.toSeq).sample.get,
          "sender"     -> acc1.toAddress,
          "dApp"       -> acc2.toAddress,
          "call"       -> func,
          "payment"    -> Seq[Payment](),
          "fee"        -> 6000000,
          "feeAssetId" -> JsNull
        )
        Post(routePath("/sign"), ist) ~> ApiKeyHeader ~> route ~> check {
          status shouldEqual StatusCodes.OK
          val jsObject = responseAs[JsObject]
          (jsObject \ "senderPublicKey").as[String] shouldBe acc1.publicKey.toString
          (jsObject \ "call" \ "function").as[String] shouldBe funcName
          (jsObject \ "call" \ "args").as[JsArray].value.length shouldBe expectedArgsLength
        }
      }

      invoke(funcWithoutArgs, 0)
      invoke(funcWithEmptyArgs, 0)
      invoke(funcWithArgs, 2)
    }
  }

  routePath("/broadcast") - {
    def withInvokeScriptTransaction(f: (KeyPair, InvokeScriptTransaction) => Unit): Unit = {
      val seed = new Array[Byte](32)
      Random.nextBytes(seed)
      val sender: KeyPair = KeyPair(seed)
      val ist = Signed.invokeScript(
        TxVersion.V1,
        sender,
        sender.toAddress,
        None,
        Seq.empty,
        6000000L,
        Asset.Waves,
        testTime.getTimestamp()
      )
      f(sender, ist)
    }

    "shows trace when trace is enabled" in withInvokeScriptTransaction { (sender, ist) =>
      val accountTrace = AccountVerifierTrace(sender.toAddress, Some(GenericError("Error in account script")))
      (utxPoolSynchronizer.validateAndBroadcast _)
        .expects(*, None)
        .returning(
          Future.successful(TracedResult(Right(true), List(accountTrace)))
        )
        .once()
      Post(routePath("/broadcast?trace=true"), ist.json()) ~> route ~> check {
        val result = responseAs[JsObject]
        (result \ "trace").as[JsValue] shouldBe Json.arr(accountTrace.json)
      }
    }

    "does not show trace when trace is disabled" in withInvokeScriptTransaction { (sender, ist) =>
      val accountTrace = AccountVerifierTrace(sender.toAddress, Some(GenericError("Error in account script")))
      (utxPoolSynchronizer.validateAndBroadcast _)
        .expects(*, None)
        .returning(
          Future.successful(TracedResult(Right(true), List(accountTrace)))
        )
        .twice()
      Post(routePath("/broadcast"), ist.json()) ~> route ~> check {
        (responseAs[JsObject] \ "trace") shouldBe empty
      }
      Post(routePath("/broadcast?trace=false"), ist.json()) ~> route ~> check {
        (responseAs[JsObject] \ "trace") shouldBe empty
      }
    }

    "generates valid trace with vars" in {
      val sender     = testWallet.generateNewAccount().get
      val aliasOwner = testWallet.generateNewAccount().get
      val recipient  = testWallet.generateNewAccount().get

      val balances = Seq(
        AddrWithBalance(sender.toAddress, 1000.waves),
        AddrWithBalance(aliasOwner.toAddress, 1000.waves)
      )

      withDomain(settingsWithFeatures(BF.SmartAccounts, BF.BlockV5, BF.SynchronousCalls, BF.Ride4DApps), balances) { d =>
        val lease = LeaseTransaction.selfSigned(2.toByte, sender, recipient.toAddress, 50.waves, 0.02.waves, ntpTime.getTimestamp()).explicitGet()

        d.appendBlock(
          CreateAliasTransaction.selfSigned(2.toByte, aliasOwner, "test_alias", 0.02.waves, ntpTime.getTimestamp()).explicitGet(),
          SetScriptTransaction
            .selfSigned(
              2.toByte,
              sender,
              Some(TestCompiler(V5).compileContract(s"""{-# STDLIB_VERSION 5 #-}
                                                       |{-# CONTENT_TYPE DAPP #-}
                                                       |{-# SCRIPT_TYPE ACCOUNT #-}
                                                       |
                                                       |@Callable(i)
                                                       |func default() = {
                                                       |  let leaseToAddress = Lease(Address(base58'${recipient.toAddress}'), ${10.waves})
                                                       |  let leaseToAlias = Lease(Alias("test_alias"), ${20.waves})
                                                       |  strict leaseId = leaseToAddress.calculateLeaseId()
                                                       |
                                                       |  [
                                                       |    leaseToAddress,
                                                       |    leaseToAlias,
                                                       |    LeaseCancel(base58'${lease.id()}')
                                                       |  ]
                                                       |}
                                                       |""".stripMargin)),
              0.06.waves,
              ntpTime.getTimestamp()
            )
            .explicitGet(),
          lease
        )

        val invoke = Signed
          .invokeScript(2.toByte, sender, sender.toAddress, None, Seq.empty, 0.06.waves, Asset.Waves, ntpTime.getTimestamp())

        Post(routePath("/broadcast?trace=true"), invoke.json()) ~> mkRoute(d) ~> check {
          val dappTrace = (responseAs[JsObject] \ "trace").as[Seq[JsObject]].find(jsObject => (jsObject \ "type").as[String] == "dApp").get

          (dappTrace \ "error").get shouldEqual JsNull
          (dappTrace \ "vars" \\ "name").map(_.as[String]) should contain theSameElementsAs Seq(
            "i",
            "default.@args",
            "Address.@args",
            "Lease.@args",
            "Lease.@complexity",
            "@complexityLimit",
            "leaseToAddress",
            "calculateLeaseId.@args",
            "calculateLeaseId.@complexity",
            "@complexityLimit",
            "leaseId",
            "==.@args",
            "==.@complexity",
            "@complexityLimit",
            "Alias.@args",
            "Lease.@args",
            "Lease.@complexity",
            "@complexityLimit",
            "leaseToAlias",
            "LeaseCancel.@args",
            "cons.@args",
            "cons.@complexity",
            "@complexityLimit",
            "cons.@args",
            "cons.@complexity",
            "@complexityLimit",
            "cons.@args",
            "cons.@complexity",
            "@complexityLimit"
          )
        }
      }
    }

    "checks the length of base58 attachment in symbols" in {
      val attachmentSizeInSymbols = TransferTransaction.MaxAttachmentStringSize + 1
      val attachmentStr           = "1" * attachmentSizeInSymbols

      val tx = TxHelpers
        .transfer()
        .copy(attachment = ByteStr(Base58.decode(attachmentStr))) // to bypass a validation
        .signWith(defaultSigner.privateKey)

      Post(routePath("/broadcast"), tx.json()) ~> route should produce(
        WrongJson(errors =
          Seq(
            JsPath \ "attachment" -> Seq(
              JsonValidationError(s"base58-encoded string length ($attachmentSizeInSymbols) exceeds maximum length of 192")
            )
          )
        )
      )
    }

    "checks the length of base58 attachment in bytes" in {
      val attachmentSizeInSymbols = TransferTransaction.MaxAttachmentSize + 1
      val attachmentStr           = "1" * attachmentSizeInSymbols
      val attachment              = ByteStr(Base58.decode(attachmentStr))

      val tx = TxHelpers
        .transfer()
        .copy(attachment = attachment)
        .signWith(defaultSigner.privateKey)

      Post(routePath("/broadcast"), tx.json()) ~> route should produce(
        TooBigInBytes(
          s"Invalid attachment. Length ${attachment.size} bytes exceeds maximum of ${TransferTransaction.MaxAttachmentSize} bytes."
        )
      )
    }
  }

  routePath("/merkleProof") - {
    val transactionsGen = for {
      txsSize <- Gen.choose(1, 10)
      txs     <- Gen.listOfN(txsSize, randomTransactionGen)
    } yield txs

    val invalidBlockGen = for {
      txs     <- transactionsGen
      signer  <- accountGen
      version <- Gen.choose(Block.GenesisBlockVersion, Block.RewardBlockVersion)
      block   <- versionedBlockGen(txs, signer, version)
    } yield block

    val invalidBlocksGen =
      for {
        blockchainHeight <- Gen.choose(1, 10)
        blocks           <- Gen.listOfN(blockchainHeight, invalidBlockGen)
      } yield blocks

    val merkleProofs = for {
      index        <- Gen.choose(0, 50)
      tx           <- randomTransactionGen
      proofsLength <- Gen.choose(1, 5)
      proofBytes   <- Gen.listOfN(proofsLength, bytes32gen)
    } yield (tx, TransactionProof(tx.id(), index, proofBytes))

    def validateSuccess(expectedProofs: Seq[TransactionProof], response: HttpResponse): Unit = {
      response.status shouldBe StatusCodes.OK

      val proofs = responseAs[List[JsObject]]

      proofs.size shouldBe expectedProofs.size

      proofs.zip(expectedProofs).foreach { case (p, e) =>
        val transactionId    = (p \ "id").as[String]
        val transactionIndex = (p \ "transactionIndex").as[Int]
        val digests          = (p \ "merkleProof").as[List[String]].map(s => ByteStr.decodeBase58(s).get)

        transactionId shouldEqual e.id.toString
        transactionIndex shouldEqual e.transactionIndex
        digests shouldEqual e.digests.map(ByteStr(_))
      }
    }

    def validateFailure(response: HttpResponse): Unit = {
      response.status shouldEqual StatusCodes.BadRequest
      (responseAs[JsObject] \ "message").as[String] shouldEqual s"transactions do not exist or block version < ${Block.ProtoBlockVersion}"
    }

    "returns merkle proofs" in {
      forAll(Gen.choose(10, 20).flatMap(n => Gen.listOfN(n, merkleProofs))) { transactionsAndProofs =>
        val (transactions, proofs) = transactionsAndProofs.unzip
        (addressTransactions.transactionProofs _).expects(transactions.map(_.id())).returning(proofs).twice()

        val queryParams = transactions.map(t => s"id=${t.id()}").mkString("?", "&", "")
        val requestBody = Json.obj("ids" -> transactions.map(_.id().toString))

        Get(routePath(s"/merkleProof$queryParams")) ~> route ~> check {
          validateSuccess(proofs, response)
        }

        Post(routePath("/merkleProof"), requestBody) ~> route ~> check {
          validateSuccess(proofs, response)
        }
      }
    }

    "returns error in case of all transactions are filtered" in {
      forAll(invalidBlocksGen) { blocks =>
        val txIdsToBlock = blocks.flatMap(b => b.transactionData.map(tx => (tx.id().toString, b))).toMap

        val queryParams = txIdsToBlock.keySet.map(id => s"id=$id").mkString("?", "&", "")
        val requestBody = Json.obj("ids" -> txIdsToBlock.keySet)

        (addressTransactions.transactionProofs _).expects(*).returning(Nil).anyNumberOfTimes()

        Get(routePath(s"/merkleProof$queryParams")) ~> route ~> check {
          validateFailure(response)
        }

        Post(routePath("/merkleProof"), requestBody) ~> route ~> check {
          validateFailure(response)
        }
      }
    }

    "handles invalid ids" in {
      val invalidIds = Seq(
        ByteStr.fill(AssetIdLength)(1),
        ByteStr.fill(AssetIdLength)(2)
      ).map(bs => s"${bs}0")

      Get(routePath(s"/merkleProof?${invalidIds.map("id=" + _).mkString("&")}")) ~> route should produce(InvalidIds(invalidIds))

      Post(routePath("/merkleProof"), FormData(invalidIds.map("id" -> _)*)) ~> route should produce(InvalidIds(invalidIds))

      Post(routePath("/merkleProof"), Json.obj("ids" -> invalidIds)) ~> route should produce(InvalidIds(invalidIds))
    }

    "handles transactions ids limit" in {
      val inputLimitErrMsg = TooBigArrayAllocation(transactionsApiRoute.settings.transactionsByAddressLimit).message
      val emptyInputErrMsg = "Transaction ID was not specified"

      def checkErrorResponse(errMsg: String): Unit = {
        response.status shouldBe StatusCodes.BadRequest
        (responseAs[JsObject] \ "message").as[String] shouldBe errMsg
      }

      def checkResponse(tx: TransferTransaction, idsCount: Int): Unit = {
        response.status shouldBe StatusCodes.OK

        val result = responseAs[JsArray].value
        result.size shouldBe idsCount
        (1 to idsCount).zip(responseAs[JsArray].value) foreach { case (_, json) =>
          (json \ "id").as[String] shouldBe tx.id().toString
          (json \ "transactionIndex").as[Int] shouldBe 0
        }
      }

      val sender = TxHelpers.signer(1)

      withDomain(DomainPresets.RideV5, balances = AddrWithBalance.enoughBalances(sender)) { d =>
        val transferTx = TxHelpers.transfer(from = sender)
        d.appendBlock(transferTx)

        val route = mkRoute(d)

        val maxLimitIds      = Seq.fill(transactionsApiRoute.settings.transactionsByAddressLimit)(transferTx.id().toString)
        val moreThanLimitIds = transferTx.id().toString +: maxLimitIds

        Get(routePath(s"/merkleProof?${maxLimitIds.map("id=" + _).mkString("&")}")) ~> route ~> check(checkResponse(transferTx, maxLimitIds.size))
        Get(routePath(s"/merkleProof?${moreThanLimitIds.map("id=" + _).mkString("&")}")) ~> route ~> check(checkErrorResponse(inputLimitErrMsg))
        Get(routePath("/merkleProof")) ~> route ~> check(checkErrorResponse(emptyInputErrMsg))

        Post(routePath("/merkleProof"), FormData(maxLimitIds.map("id" -> _)*)) ~> route ~> check(checkResponse(transferTx, maxLimitIds.size))
        Post(routePath("/merkleProof"), FormData(moreThanLimitIds.map("id" -> _)*)) ~> route ~> check(checkErrorResponse(inputLimitErrMsg))
        Post(routePath("/merkleProof"), FormData()) ~> route ~> check(checkErrorResponse(emptyInputErrMsg))

        Post(
          routePath(s"/merkleProof"),
          HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> Json.arr(maxLimitIds.map(id => id: JsValueWrapper)*)).toString())
        ) ~> route ~> check(checkResponse(transferTx, maxLimitIds.size))
        Post(
          routePath(s"/merkleProof"),
          HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> Json.arr(moreThanLimitIds.map(id => id: JsValueWrapper)*)).toString())
        ) ~> route ~> check(checkErrorResponse(inputLimitErrMsg))
        Post(
          routePath("/merkleProof"),
          HttpEntity(ContentTypes.`application/json`, Json.obj("ids" -> JsArray.empty).toString())
        ) ~> route ~> check(checkErrorResponse(emptyInputErrMsg))
      }
    }
  }
}
