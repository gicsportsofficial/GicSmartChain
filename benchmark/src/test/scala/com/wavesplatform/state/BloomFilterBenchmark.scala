package com.gicsports.state

import com.gicsports.common.state.ByteStr
import com.gicsports.database.{Keys, LevelDBWriter}
import com.gicsports.transaction.assets.exchange.ExchangeTransaction
import com.gicsports.transaction.smart.Verifier
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit
import scala.util.Random

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 100)
@Measurement(iterations = 100)
class BloomFilterBenchmark {
  import BloomFilterBenchmark._

  @Benchmark
  def volumeAndFeeWithBloom(bh: Blackhole, st: St): Unit = {
    bh.consume(st.levelDBWriterWithBloomFilter.filledVolumeAndFee(ByteStr(Random.nextBytes(32))))
  }

  @Benchmark
  def volumeAndFeeWithoutBloom(bh: Blackhole, st: St): Unit = {
    bh.consume(st.levelDBWriterWithoutBloomFilter.filledVolumeAndFee(ByteStr(Random.nextBytes(32))))
  }

  @Benchmark
  def verifyExchangeTxSign(bh: Blackhole, st: St): Unit = {
    bh.consume(Verifier.verifyAsEllipticCurveSignature(st.exchangeTransactions(Random.nextInt(1000)), checkWeakPk = false))
  }
}

object BloomFilterBenchmark {
  class St extends DBState {

    lazy val exchangeTransactions: List[ExchangeTransaction] = {
      val txCountAtHeight =
        Map.empty[Int, Int].withDefault(h => db.get(Keys.blockMetaAt(Height(h))).fold(0)(_.transactionCount))

      val txs = LazyList.from(levelDBWriter.height, -1).flatMap { h =>
        val txCount = txCountAtHeight(h)
        if (txCount == 0)
          Seq.empty[ExchangeTransaction]
        else
          (0 until txCount).flatMap(
            txNum =>
              db.get(Keys.transactionAt(Height(h), TxNum(txNum.toShort)))
                .collect { case (m, tx: ExchangeTransaction) if m.succeeded => tx }
          )
      }

      txs.take(1000).toList
    }

    lazy val levelDBWriterWithBloomFilter: LevelDBWriter =
      LevelDBWriter.readOnly(
        db,
        settings.copy(dbSettings = settings.dbSettings.copy(maxCacheSize = 1, useBloomFilter = true))
      )

    lazy val levelDBWriterWithoutBloomFilter: LevelDBWriter =
      LevelDBWriter.readOnly(
        db,
        settings.copy(dbSettings = settings.dbSettings.copy(maxCacheSize = 1, useBloomFilter = false))
      )
  }
}
