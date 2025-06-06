package com.gicsports.lang.v1.compiler

import cats.syntax.semigroup.*
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.Global
import com.gicsports.lang.contract.DApp
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.{Account, Asset, Expression, StdLibVersion, DApp as DAppType}
import com.gicsports.lang.script.ContractScript.ContractScriptImpl
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.script.{ContractScript, Script}
import com.gicsports.lang.v1.CTX
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.gicsports.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.gicsports.lang.v1.traits.Environment

import scala.collection.mutable

class TestCompiler(version: StdLibVersion) {
  private lazy val baseCompilerContext =
    PureContext.build(version, useNewPowPrecision = true).withEnvironment[Environment] |+|
      CryptoContext.build(Global, version).withEnvironment[Environment]

  private lazy val compilerContext =
    (baseCompilerContext |+|
      WavesContext.build(Global, DirectiveSet(version, Account, DAppType).explicitGet(), fixBigScriptField = true)).compilerContext

  lazy val expressionContext: CTX[Environment] =
    WavesContext.build(Global, DirectiveSet(version, Account, Expression).explicitGet(), fixBigScriptField = true)

  private lazy val expressionCompilerContext =
    (baseCompilerContext |+|
      expressionContext).compilerContext

  private lazy val assetCompilerContext =
    (baseCompilerContext |+|
      WavesContext.build(Global, DirectiveSet(version, Asset, Expression).explicitGet(), fixBigScriptField = true)).compilerContext

  def compile(
      script: String,
      allowIllFormedStrings: Boolean = false,
      compact: Boolean = false,
      removeUnused: Boolean = false
  ): Either[String, DApp] =
    ContractCompiler.compile(
      script,
      compilerContext,
      version,
      allowIllFormedStrings = allowIllFormedStrings,
      needCompaction = compact,
      removeUnusedCode = removeUnused
    )

  def compileContract(script: String, allowIllFormedStrings: Boolean = false, compact: Boolean = false): ContractScriptImpl =
    ContractScript(version, compile(script, allowIllFormedStrings, compact).explicitGet()).explicitGet()

  def compileExpression(script: String, allowIllFormedStrings: Boolean = false, checkSize: Boolean = true): ExprScript =
    ExprScript(
      version,
      ExpressionCompiler.compile(script, expressionCompilerContext, allowIllFormedStrings).explicitGet()._1,
      checkSize = checkSize
    ).explicitGet()

  def compileExpressionE(script: String, allowIllFormedStrings: Boolean = false, checkSize: Boolean = true): Either[String, ExprScript] =
    ExpressionCompiler
      .compile(script, expressionCompilerContext, allowIllFormedStrings)
      .map(s => ExprScript(version, s._1, checkSize = checkSize).explicitGet())

  def compileAsset(script: String): Script =
    ExprScript(version, ExpressionCompiler.compile(script, assetCompilerContext).explicitGet()._1).explicitGet()

  def compileFreeCall(script: String): ExprScript = {
    val expr = ContractCompiler.compileFreeCall(script, compilerContext, version).explicitGet()
    ExprScript(version, expr, isFreeCall = true).explicitGet()
  }
}

object TestCompiler {
  private val compilerByVersion = mutable.HashMap.empty[StdLibVersion, TestCompiler]
  def apply(version: StdLibVersion): TestCompiler =
    compilerByVersion.getOrElse(
      version,
      compilerByVersion.synchronized {
        compilerByVersion.getOrElseUpdate(version, new TestCompiler(version))
      }
    )
}
