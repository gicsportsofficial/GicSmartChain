package com.gicsports.generator

import com.gicsports.transaction.Transaction

trait TransactionGenerator extends Iterator[Iterator[Transaction]] {
  override val hasNext          = true
  def initial: Seq[Transaction] = Seq.empty
  def tailInitial: Seq[Transaction] = Seq.empty
}
