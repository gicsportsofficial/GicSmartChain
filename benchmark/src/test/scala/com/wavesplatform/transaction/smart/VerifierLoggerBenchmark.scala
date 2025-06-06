package com.gicsports.transaction.smart

import java.io.BufferedWriter
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit

import cats.Id
import com.gicsports.account.KeyPair
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils._
import com.gicsports.lang.directives.values.V4
import com.gicsports.lang.v1.compiler.Terms
import com.gicsports.lang.v1.compiler.Terms.{CONST_BOOLEAN, EVALUATED}
import com.gicsports.lang.v1.evaluator.Log
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.Bindings
import com.gicsports.state.BinaryDataEntry
import com.gicsports.transaction.DataTransaction
import com.gicsports.transaction.smart.VerifierLoggerBenchmark.BigLog
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class VerifierLoggerBenchmark {

  @Benchmark
  def verifierLogged(bh: Blackhole, log: BigLog): Unit = {
    val logs = Verifier.buildLogs("id", log.value._1, log.value._2)
    bh.consume(log.writer.write(logs))
  }
}

object VerifierLoggerBenchmark {

  @State(Scope.Benchmark)
  class BigLog {

    val resultFile: Path       = Paths.get("log.txt")
    val writer: BufferedWriter = Files.newBufferedWriter(resultFile)

    private val dataTx: DataTransaction = DataTransaction
      .selfSigned(1.toByte, KeyPair(Array[Byte]()), (1 to 4).map(i => BinaryDataEntry(s"data$i", ByteStr(Array.fill(1024 * 30)(1)))).toList, 100000000, 0)
      .explicitGet()

    private val dataTxObj: Terms.CaseObj = Bindings.transactionObject(
      RealTransactionWrapper(dataTx, ???, ???, ???).explicitGet(),
      proofsEnabled = true,
      V4,
      fixBigScriptField = true
    )

    val value: (Log[Id], Either[String, EVALUATED]) =
      (
        List.fill(500)("txVal" -> Right(dataTxObj)),
        Right(CONST_BOOLEAN(true))
      )

    @TearDown
    def deleteFile(): Unit = {
      Files.delete(resultFile)
      writer.close()
    }
  }
}
