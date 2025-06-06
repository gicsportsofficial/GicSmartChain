package com.gicsports.api.http.leasing

import akka.http.scaladsl.server.Route
import com.gicsports.api.common.{CommonAccountsApi, LeaseInfo}
import com.gicsports.api.http.{BroadcastRoute, *}
import com.gicsports.api.http.requests.{LeaseCancelRequest, LeaseRequest}
import com.gicsports.api.http.ApiError.{InvalidIds, TransactionDoesNotExist}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.Base58
import com.gicsports.network.TransactionPublisher
import com.gicsports.settings.RestAPISettings
import com.gicsports.state.Blockchain
import com.gicsports.transaction.*
import com.gicsports.utils.Time
import com.gicsports.wallet.Wallet
import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.*

case class LeaseApiRoute(
    settings: RestAPISettings,
    wallet: Wallet,
    blockchain: Blockchain,
    transactionPublisher: TransactionPublisher,
    time: Time,
    commonAccountApi: CommonAccountsApi,
    routeTimeout: RouteTimeout
) extends ApiRoute
    with BroadcastRoute
    with AuthRoute {
  import LeaseApiRoute.*

  override val route: Route = pathPrefix("leasing") {
    active ~ deprecatedRoute
  }

  private def deprecatedRoute: Route =
    (path("lease") & withAuth) {
      broadcast[LeaseRequest](TransactionFactory.lease(_, wallet, time))
    } ~ (path("cancel") & withAuth) {
      broadcast[LeaseCancelRequest](TransactionFactory.leaseCancel(_, wallet, time))
    } ~ pathPrefix("broadcast") {
      path("lease")(broadcast[LeaseRequest](_.toTx)) ~
        path("cancel")(broadcast[LeaseCancelRequest](_.toTx))
    } ~ pathPrefix("info")(leaseInfo)

  private[this] def active: Route = (pathPrefix("active") & get) {
    path(AddrSegment) { address =>
      routeTimeout.executeToFuture(
        commonAccountApi.activeLeases(address).map(Json.toJson(_)).toListL
      )
    }
  }

  private[this] def leaseInfo: Route =
    (get & path(TransactionId)) { leaseId =>
      val result = commonAccountApi
        .leaseInfo(leaseId)
        .toRight(TransactionDoesNotExist)

      complete(result)
    } ~ anyParam("id", limit = settings.transactionsByAddressLimit) { ids =>
      leasingInfosMap(ids) match {
        case Left(err) => complete(err)
        case Right(leaseInfoByIdMap) =>
          val results = ids.map(leaseInfoByIdMap).toVector
          complete(results)
      }
    }

  private[this] def leasingInfosMap(ids: Iterable[String]): Either[InvalidIds, Map[String, LeaseInfo]] = {
    val infos = ids.map(id =>
      (for {
        id <- Base58.tryDecodeWithLimit(id).toOption
        li <- commonAccountApi.leaseInfo(ByteStr(id))
      } yield li).toRight(id)
    )
    val failed = infos.flatMap(_.left.toOption)

    if (failed.isEmpty) {
      Right(infos.collect { case Right(li) =>
        li.id.toString -> li
      }.toMap)
    } else {
      Left(InvalidIds(failed.toVector))
    }
  }
}

object LeaseApiRoute {
  implicit val leaseStatusWrites: Writes[LeaseInfo.Status] =
    Writes(s => JsString(s.toString.toLowerCase))

  implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(optionHandlers = OptionHandlers.WritesNull)

  implicit val leaseInfoWrites: OWrites[LeaseInfo] = {
    import com.gicsports.utils.byteStrFormat
    Json.writes[LeaseInfo]
  }
}
