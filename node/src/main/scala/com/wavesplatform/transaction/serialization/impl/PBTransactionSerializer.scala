package com.gicsports.transaction.serialization.impl

import cats.syntax.applicativeError._
import com.gicsports.protobuf.transaction.{PBTransactions, SignedTransaction => PBSignedTransaction}
import com.gicsports.protobuf.utils.PBUtils
import com.gicsports.transaction.{PBParsingError, Transaction}

import scala.util.Try

object PBTransactionSerializer {
  def bodyBytes(tx: Transaction): Array[Byte] =
    PBUtils.encodeDeterministic(PBTransactions.protobuf(tx).getWavesTransaction)

  def bytes(tx: Transaction): Array[Byte] =
    PBUtils.encodeDeterministic(PBTransactions.protobuf(tx))

  def parseBytes(bytes: Array[Byte]): Try[Transaction] =
    PBSignedTransaction
      .validate(bytes)
      .adaptErr { case err => PBParsingError(err) }
      .flatMap(PBTransactions.tryToVanilla)
}
