package com.gicsports.extensions

import akka.actor.ActorSystem
import com.gicsports.account.Address
import com.gicsports.api.common._
import com.gicsports.common.state.ByteStr
import com.gicsports.events.UtxEvent
import com.gicsports.lang.ValidationError
import com.gicsports.settings.WavesSettings
import com.gicsports.state.Blockchain
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.transaction.{Asset, DiscardedBlocks, Transaction}
import com.gicsports.utils.Time
import com.gicsports.utx.UtxPool
import com.gicsports.wallet.Wallet
import monix.eval.Task
import monix.reactive.Observable

trait Context {
  def settings: WavesSettings
  def blockchain: Blockchain
  def rollbackTo(blockId: ByteStr): Task[Either[ValidationError, DiscardedBlocks]]
  def time: Time
  def wallet: Wallet
  def utx: UtxPool

  def transactionsApi: CommonTransactionsApi
  def blocksApi: CommonBlocksApi
  def accountsApi: CommonAccountsApi
  def assetsApi: CommonAssetsApi

  def broadcastTransaction(tx: Transaction): TracedResult[ValidationError, Boolean]
  def spendableBalanceChanged: Observable[(Address, Asset)]
  def utxEvents: Observable[UtxEvent]
  def actorSystem: ActorSystem
}
