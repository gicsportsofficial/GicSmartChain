package com.gicsports.utx

import scala.concurrent.duration.FiniteDuration

import com.gicsports.common.state.ByteStr
import com.gicsports.lang.ValidationError
import com.gicsports.mining.MultiDimensionalMiningConstraint
import com.gicsports.transaction._
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.utx.UtxPool.PackStrategy

trait UtxPool extends AutoCloseable {
  def putIfNew(tx: Transaction, forceValidate: Boolean = false): TracedResult[ValidationError, Boolean]
  def removeAll(txs: Iterable[Transaction]): Unit
  def all: Seq[Transaction]
  def size: Int
  def transactionById(transactionId: ByteStr): Option[Transaction]
  def packUnconfirmed(
      rest: MultiDimensionalMiningConstraint,
      strategy: PackStrategy = PackStrategy.Unlimited,
      cancelled: () => Boolean = () => false
  ): (Option[Seq[Transaction]], MultiDimensionalMiningConstraint)
}

object UtxPool {
  sealed trait PackStrategy
  object PackStrategy {
    case class Limit(time: FiniteDuration)    extends PackStrategy
    case class Estimate(time: FiniteDuration) extends PackStrategy
    case object Unlimited                     extends PackStrategy
  }
}
