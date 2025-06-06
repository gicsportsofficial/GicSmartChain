package com.gicsports.api.http

import java.time.Instant

import akka.http.scaladsl.server.Route
import com.gicsports.Shutdownable
import com.gicsports.settings.{Constants, RestAPISettings}
import com.gicsports.state.Blockchain
import com.gicsports.utils.ScorexLogging
import play.api.libs.json.Json

case class NodeApiRoute(settings: RestAPISettings, blockchain: Blockchain, application: Shutdownable)
    extends ApiRoute
    with AuthRoute
    with ScorexLogging {

  override lazy val route: Route = pathPrefix("node") {
    stop ~ status ~ version
  }

  def version: Route = (get & path("version")) {
    complete(Json.obj("version" -> Constants.AgentName))
  }

  def stop: Route = (post & path("stop") & withAuth) {
    log.info("Request to stop application")
    application.shutdown()
    complete(Json.obj("stopped" -> true))
  }

  def status: Route = (get & path("status")) {
    val lastUpdated = blockchain.lastBlockHeader.get.header.timestamp
    complete(
      Json.obj(
        "blockchainHeight" -> blockchain.height,
        "stateHeight"      -> blockchain.height,
        "updatedTimestamp" -> lastUpdated,
        "updatedDate"      -> Instant.ofEpochMilli(lastUpdated).toString
      )
    )
  }
}
