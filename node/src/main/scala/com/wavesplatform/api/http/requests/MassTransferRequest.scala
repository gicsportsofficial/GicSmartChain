package com.gicsports.api.http.requests

import com.gicsports.common.state.ByteStr
import com.gicsports.transaction.transfer.MassTransferTransaction.Transfer
import play.api.libs.json.Json

case class MassTransferRequest(
    version: Option[Byte],
    assetId: Option[String],
    sender: String,
    transfers: List[Transfer],
    fee: Long,
    attachment: Option[ByteStr] = None,
    timestamp: Option[Long] = None
)

object MassTransferRequest {
  implicit val jsonFormat = Json.format[MassTransferRequest]
}
