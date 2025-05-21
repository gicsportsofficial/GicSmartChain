package com.gicsports.lang.parser.error

import com.gicsports.lang.directives.values.V6
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.PropSpec
import org.scalatest.Assertion

abstract class ParseErrorTest extends PropSpec {
  protected def assert(
      script: String,
      error: String,
      start: Int,
      end: Int,
      highlighting: String,
      endExpr: Boolean = true,
      onlyDApp: Boolean = false
  ): Assertion = {
    val fullError = s"$error in $start-$end"
    val expr      = if (endExpr) script + "\ntrue" else script
    TestCompiler(V6).compile(script) shouldBe Left(fullError)
    if (!onlyDApp) TestCompiler(V6).compileExpressionE(expr) shouldBe Left(fullError)
    script.slice(start, end) shouldBe highlighting
  }
}
