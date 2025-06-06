package com.gicsports.lang.compiler

import cats.implicits.toBifunctorOps
import com.gicsports.lang.contract.DApp
import com.gicsports.lang.directives.{Directive, DirectiveParser}
import com.gicsports.lang.utils
import com.gicsports.lang.v1.compiler.{CompilationError, ContractCompiler}
import com.gicsports.lang.v1.parser.Expressions
import com.gicsports.test.PropSpec

class ContractCompilerWithParserV2Test extends PropSpec {

  def compile(script: String, saveExprContext: Boolean = false): Either[String, (Option[DApp], Expressions.DAPP, Iterable[CompilationError])] = {

    val result = for {
      directives <- DirectiveParser(script)
      ds         <- Directive.extractDirectives(directives)
      ctx = utils.compilerContext(ds)
      compResult <- ContractCompiler.compileWithParseResult(script, 0, ctx, ds.stdLibVersion, saveExprContext).leftMap(_._1)
    } yield compResult

    result
  }

  property("simple test 2") {
    val script = """
                   |{-# STDLIB_VERSION 3 #-}
                   |{-# SCRIPT_TYPE ACCOUNT #-}
                   |{-# CONTENT_TYPE DAPP #-}
                   |
                   |@Callable(inv)
                   |func default() = {
                   |  [ IntegerEntry("x", inv.payment.extract().amount) ]
                   |}
                   |
                   |""".stripMargin

    val result = compile(script)

    result shouldBe Symbol("right")
  }
}
