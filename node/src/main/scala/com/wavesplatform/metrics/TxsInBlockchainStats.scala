package com.gicsports.metrics

import org.influxdb.dto.Point

object TxsInBlockchainStats {
  def record(number: Int): Unit = Metrics.write(
    Point
      .measurement("applied-txs")
      .addField("n", number)
  )
}
