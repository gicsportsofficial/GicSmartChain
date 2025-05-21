package com.gicsports.state.patch

import com.gicsports.account.{Address, PublicKey}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils._
import com.gicsports.state.{Blockchain, Diff, LeaseBalance, Portfolio}
import com.gicsports.state.reader.LeaseDetails
import play.api.libs.json.{Json, OFormat}

case object CancelAllLeases extends PatchAtHeight('W' -> 462000, 'T' -> 51500) {
  private[patch] case class LeaseData(senderPublicKey: String, amount: Long, recipient: String, id: String)

  private[patch] case class CancelledLeases(balances: Map[String, LeaseBalance], cancelledLeases: Seq[LeaseData]) {
    private[this] val height: Int = patchHeight.getOrElse(0)
    val leaseStates: Map[ByteStr, LeaseDetails] = cancelledLeases.map { data =>
      val sender    = PublicKey(ByteStr.decodeBase58(data.senderPublicKey).get)
      val recipient = Address.fromString(data.recipient).explicitGet()
      val id        = ByteStr.decodeBase58(data.id).get
      (id, LeaseDetails(sender, recipient, data.amount, status = LeaseDetails.Status.Expired(height), id, height))
    }.toMap
  }

  private[patch] object CancelledLeases {
    implicit val dataFormat: OFormat[LeaseData]       = Json.format[LeaseData]
    implicit val jsonFormat: OFormat[CancelledLeases] = Json.format[CancelledLeases]
  }

  def apply(blockchain: Blockchain): Diff = {
    val patch = readPatchData[CancelledLeases]()
    Diff(portfolios = patch.balances.map {
      case (address, lb) =>
        Address.fromString(address).explicitGet() -> Portfolio(lease = lb)
    }, leaseState = patch.leaseStates)
  }
}
