package com.gicsports.http

import com.gicsports.lang.ValidationError
import com.gicsports.network.TransactionPublisher
import com.gicsports.transaction.Transaction
import com.gicsports.transaction.smart.script.trace.TracedResult

import scala.concurrent.Future

object DummyTransactionPublisher {
  val accepting: TransactionPublisher = { (_, _) =>
    Future.successful(TracedResult(Right(true)))
  }

  def rejecting(error: Transaction => ValidationError): TransactionPublisher = { (tx, _) =>
    Future.successful(TracedResult(Left(error(tx))))
  }
}
