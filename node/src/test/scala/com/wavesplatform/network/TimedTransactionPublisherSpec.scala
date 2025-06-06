package com.gicsports.network
import java.util.concurrent.CountDownLatch

import com.gicsports.account.PublicKey
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.ValidationError
import com.gicsports.test.FreeSpec
import com.gicsports.transaction.smart.script.trace.TracedResult
import com.gicsports.transaction.{GenesisTransaction, Transaction}
import com.gicsports.utils.Schedulers
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.HashedWheelTimer
import monix.execution.atomic.AtomicInt
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._

class TimedTransactionPublisherSpec extends FreeSpec with BeforeAndAfterAll {
  private[this] val timer     = new HashedWheelTimer
  private[this] val scheduler = Schedulers.timeBoundedFixedPool(timer, 1.second, 1, "test-utx-sync")

  "UtxPoolSynchronizer" - {
    val latch   = new CountDownLatch(5)
    val counter = AtomicInt(10)

    def countTransactions(tx: Transaction): TracedResult[ValidationError, Boolean] = {
      // the first 5 transactions will take too long to validate
      if (counter.getAndDecrement() > 5) {
        while (!Thread.currentThread().isInterrupted) {
          Thread.sleep(100)
        }
      }

      latch.countDown()

      TracedResult(Right(true))
    }

    "accepts only those transactions from network which can be validated quickly" in withUPS(countTransactions) { ups =>
      1 to 10 foreach { i =>
        ups.validateAndBroadcast(
          GenesisTransaction.create(PublicKey(new Array[Byte](32)).toAddress, i * 10L, 0L).explicitGet(),
          Some(new EmbeddedChannel)
        )
      }
      latch.await()               // 5 transactions have completed validation process
      counter.get() shouldEqual 0 // all 10 transactions have been processed
    }
  }

  private def withUPS(putIfNew: Transaction => TracedResult[ValidationError, Boolean])(f: TransactionPublisher => Unit): Unit =
    f(TransactionPublisher.timeBounded((tx, _) => putIfNew(tx), (_, _) => (), scheduler, allowRebroadcast = false, () => Right(())))

  override protected def afterAll(): Unit = {
    super.afterAll()
    scheduler.shutdown()
    timer.stop()
  }
}
