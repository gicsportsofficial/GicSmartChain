package com.gicsports.lang.v1
import java.util.concurrent.TimeUnit

import com.gicsports.common.state.ByteStr
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.{Account, Expression, V6}
import com.gicsports.lang.utils.lazyContexts
import com.gicsports.lang.v1.EnvironmentFunctionsBenchmark.curve25519
import com.gicsports.lang.v1.compiler.Terms.EXPR
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.utils.EthHelpers
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
class AddressFromPublicKeyBenchmark {
  @Benchmark
  def addressFromPublicKeyWaves(s: PkSt, bh: Blackhole): Unit = bh.consume(eval(s.ctx, s.exprWaves, V6))

  @Benchmark
  def addressFromPublicKeyEth(s: PkSt, bh: Blackhole): Unit = bh.consume(eval(s.ctx, s.exprEth, V6))
}

@State(Scope.Benchmark)
class PkSt extends EthHelpers {
  val ds  = DirectiveSet(V6, Account, Expression).fold(null, identity)
  val ctx = lazyContexts((ds, true, true)).value().evaluationContext(EnvironmentFunctionsBenchmark.environment)

  val wavesPk   = ByteStr(curve25519.generateKeypair._2)
  val exprWaves = TestCompiler(V6).compileExpression(s"addressFromPublicKey(base58'$wavesPk')").expr.asInstanceOf[EXPR]
  val exprEth   = TestCompiler(V6).compileExpression(s"addressFromPublicKey(base58'$TestEthOrdersPublicKey')").expr.asInstanceOf[EXPR]
}
