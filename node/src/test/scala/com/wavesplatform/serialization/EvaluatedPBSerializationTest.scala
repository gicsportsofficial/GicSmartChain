package com.gicsports.serialization

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.gicsports.account.Address
import com.gicsports.api.http.{ApiMarshallers, RouteTimeout, TransactionsApiRoute}
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.history.Domain
import com.gicsports.http.RestAPISettingsHelper
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.PropSpec
import com.gicsports.transaction.TxHelpers
import com.gicsports.transaction.smart.InvokeScriptTransaction
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.utils.{JsonMatchers, Schedulers}
import play.api.libs.json.*

import scala.concurrent.duration.*
import scala.concurrent.Future

class EvaluatedPBSerializationTest
    extends PropSpec
    with WithDomain
    with RestAPISettingsHelper
    with ScalatestRouteTest
    with JsonMatchers
    with ApiMarshallers {

  property("correctly serialize/deserialize EVALUATED args for callable functions") {
    val masterDApp  = TxHelpers.signer(1)
    val serviceDApp = TxHelpers.signer(2)
    val invoker     = TxHelpers.signer(3)

    Seq(
      "unit"                                 -> Seq("Unit"),
      "1"                                    -> Seq("Int"),
      "base16'52696465'"                     -> Seq("ByteVector"),
      "base58'8t38fWQhrYJsqxXtPpi'"          -> Seq("ByteVector"),
      "base64'UmlkZQ=='"                     -> Seq("ByteVector"),
      "\"str\""                              -> Seq("String"),
      "true"                                 -> Seq("Boolean"),
      "[1, 2, 3]"                            -> Seq("Array", "Int", "Int", "Int"),
      "(1, 2, 3)"                            -> Seq("Tuple", "Int", "Int", "Int"),
      "getInteger(this,\"integerVal\")"      -> Seq("Unit"),
      "Address(base58'8t38fWQhrYJsqxXtPpi')" -> Seq("Address", "ByteVector"),
      "BalanceDetails(10, 10, 10, 10)"       -> Seq("BalanceDetails", "Int", "Int", "Int", "Int"),
      "GenesisTransaction(1, Address(base58''), base58'', 1, 1, 1)" -> Seq(
        "GenesisTransaction",
        "Address",
        "ByteVector",
        "Int",
        "Int",
        "Int",
        "ByteVector",
        "Int"
      )
    ).foreach { case (argValue, argType) =>
      withDomain(DomainPresets.RideV5, AddrWithBalance.enoughBalances(masterDApp, serviceDApp, invoker)) { d =>
        val setMasterScript  = TxHelpers.setScript(masterDApp, masterScript(argValue, serviceDApp.toAddress))
        val setServiceScript = TxHelpers.setScript(serviceDApp, serviceScript)
        val invoke           = TxHelpers.invoke(masterDApp.toAddress, Some("test"), Seq.empty, invoker = invoker)

        val route = transactionsApiRoute(d).route

        d.appendBlock(setMasterScript, setServiceScript)

        d.appendBlock(invoke)
        checkTxInfoResult(invoke, argType)(route)

        d.appendKeyBlock()
        checkTxInfoResult(invoke, argType)(route)
      }
    }
  }

  private def masterScript(arg: String, serviceDApp: Address): Script =
    TestCompiler(V5).compileContract(
      s"""
         |{-# STDLIB_VERSION 5 #-}
         |{-# SCRIPT_TYPE ACCOUNT #-}
         |{-# CONTENT_TYPE DAPP #-}
         |
         |@Callable(i)
         |func test() = {
         |  strict res = invoke(Address(base58'$serviceDApp'), "test", [$arg], [])
         |  []
         |}
         |""".stripMargin
    )

  private def serviceScript: Script =
    TestCompiler(V5).compileContract(
      s"""
         |{-# STDLIB_VERSION 5 #-}
         |{-# SCRIPT_TYPE ACCOUNT #-}
         |{-# CONTENT_TYPE DAPP #-}
         |
         |@Callable(i)
         |func test(p: Boolean) = []
         |""".stripMargin
    )

  private def checkTxInfoResult(invoke: InvokeScriptTransaction, argType: Seq[String])(route: Route): Unit =
    Get(s"/transactions/info/${invoke.id()}") ~> route ~> check {
      val callJsObj = (responseAs[JsObject] \\ "call")(1)
      (callJsObj \ "function").as[String] shouldBe "test"
      (callJsObj \\ "type").map(_.as[String]) shouldBe argType
    }

  private def transactionsApiRoute(d: Domain) = new TransactionsApiRoute(
    restAPISettings,
    d.transactionsApi,
    d.wallet,
    d.blockchain,
    () => d.utxPool.size,
    (_, _) => Future.successful(TracedResult(Right(true))),
    ntpTime,
    new RouteTimeout(60.seconds)(Schedulers.fixedPool(1, "heavy-request-scheduler"))
  )
}
