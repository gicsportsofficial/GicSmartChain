package com.gicsports.transaction.smart.script

import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.ContractScript.ContractScriptImpl
import com.gicsports.lang.script.Script
import com.gicsports.lang.script.v1.ExprScript.ExprScriptImpl
import com.gicsports.lang.v1.estimator.ScriptEstimator
import com.gicsports.lang.{API, CompileResult}

object ScriptCompiler {
  @Deprecated
  def apply(
      scriptText: String,
      isAssetScript: Boolean,
      estimator: ScriptEstimator,
      fixEstimateOfVerifier: Boolean = true
  ): Either[String, (Script, Long)] = {
    val script = if (!isAssetScript || scriptText.contains("SCRIPT_TYPE")) scriptText else s"{-# SCRIPT_TYPE ASSET #-}\n$scriptText"
    compile(script, estimator, fixEstimateOfVerifier = fixEstimateOfVerifier)
  }

  def compile(
      scriptText: String,
      estimator: ScriptEstimator,
      libraries: Map[String, String] = Map(),
      defaultStdLib: => StdLibVersion = StdLibVersion.VersionDic.default,
      fixEstimateOfVerifier: Boolean = true
  ): Either[String, (Script, Long)] =
    API.compile(scriptText, estimator, libraries = libraries, defaultStdLib = defaultStdLib).map {
      case CompileResult.Expression(v, _, complexity, expr, _, isFreeCall) => (ExprScriptImpl(v, isFreeCall, expr), complexity)
      case CompileResult.Library(v, _, complexity, expr)                   => (ExprScriptImpl(v, isFreeCall = false, expr), complexity)
      case CompileResult.DApp(v, r, _, _)                                  => (ContractScriptImpl(v, r.dApp), r.verifierComplexity)
    }
}
