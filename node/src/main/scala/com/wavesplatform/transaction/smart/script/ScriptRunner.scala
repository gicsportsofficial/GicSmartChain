package com.gicsports.transaction.smart.script

import cats.Id
import cats.syntax.either.*
import com.gicsports.account.AddressScheme
import com.gicsports.common.state.ByteStr
import com.gicsports.features.BlockchainFeatures.*
import com.gicsports.features.EstimatorProvider.*
import com.gicsports.features.EvaluatorFixProvider.*
import com.gicsports.lang.*
import com.gicsports.lang.contract.DApp
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.script.{ContractScript, Script}
import com.gicsports.lang.v1.ContractLimits
import com.gicsports.lang.v1.compiler.Terms.{EVALUATED, EXPR, TRUE}
import com.gicsports.lang.v1.evaluator.*
import com.gicsports.lang.v1.evaluator.ContractEvaluator.LogExtraInfo
import com.gicsports.lang.v1.evaluator.ctx.EvaluationContext
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.Bindings
import com.gicsports.lang.v1.traits.Environment
import com.gicsports.state.*
import com.gicsports.transaction.smart.{DApp as DAppTarget, *}
import com.gicsports.transaction.{Authorized, Proven}
import monix.eval.Coeval

object ScriptRunner {
  type TxOrd = BlockchainContext.In

  def apply(
      in: TxOrd,
      blockchain: Blockchain,
      script: Script,
      isAssetScript: Boolean,
      scriptContainerAddress: Environment.Tthis,
      complexityLimit: Int = Int.MaxValue,
      default: EVALUATED = TRUE
  ): (Log[Id], Int, Either[ExecutionError, EVALUATED]) =
    applyGeneric(
      in,
      blockchain,
      script,
      isAssetScript,
      scriptContainerAddress,
      complexityLimit,
      default,
      blockchain.isFeatureActivated(SynchronousCalls),
      blockchain.isFeatureActivated(SynchronousCalls),
      blockchain.isFeatureActivated(SynchronousCalls) &&
        blockchain.height > blockchain.settings.functionalitySettings.enforceTransferValidationAfter,
      blockchain.checkEstimatorSumOverflow,
      blockchain.newEvaluatorMode,
      blockchain.isFeatureActivated(RideV6),
      blockchain.isFeatureActivated(ConsensusImprovements)
    )

  def applyGeneric(
      in: TxOrd,
      blockchain: Blockchain,
      script: Script,
      isAssetScript: Boolean,
      scriptContainerAddress: Environment.Tthis,
      defaultLimit: Int,
      default: EVALUATED,
      useCorrectScriptVersion: Boolean,
      fixUnicodeFunctions: Boolean,
      useNewPowPrecision: Boolean,
      checkEstimatorSumOverflow: Boolean,
      newEvaluatorMode: Boolean,
      checkWeakPk: Boolean,
      fixBigScriptField: Boolean
  ): (Log[Id], Int, Either[ExecutionError, EVALUATED]) = {

    def evalVerifier(
        isContract: Boolean,
        partialEvaluate: (DirectiveSet, EvaluationContext[Environment, Id]) => (Log[Id], Int, Either[ExecutionError, EVALUATED])
    ): (Log[Id], Int, Either[ExecutionError, EVALUATED]) = {
      val txId = in.eliminate(_.id(), _ => ByteStr.empty)
      val ctxE =
        for {
          ds <- DirectiveSet(script.stdLibVersion, if (isAssetScript) Asset else Account, Expression)
          mi <- buildThisValue(in, blockchain, ds, scriptContainerAddress)
          ctx <- BlockchainContext
            .build(
              script.stdLibVersion,
              AddressScheme.current.chainId,
              Coeval.evalOnce(mi),
              Coeval.evalOnce(blockchain.height),
              blockchain,
              isAssetScript,
              isContract,
              scriptContainerAddress,
              txId,
              fixUnicodeFunctions,
              useNewPowPrecision,
              fixBigScriptField
            )
        } yield (ds, ctx)

      ctxE.fold(e => (Nil, 0, Left(e)), { case (ds, ctx) => partialEvaluate(ds, ctx) })
    }

    def evaluate(
        ctx: EvaluationContext[Environment, Id],
        expr: EXPR,
        logExtraInfo: LogExtraInfo,
        version: StdLibVersion
    ): (Log[Id], Int, Either[ExecutionError, EVALUATED]) = {
      val correctedLimit =
        if (isAssetScript)
          ContractLimits.MaxComplexityByVersion(version)
        else
          ContractLimits.MaxAccountVerifierComplexityByVersion(version)

      val (limit, onExceed) =
        if (defaultLimit == Int.MaxValue)
          if (checkEstimatorSumOverflow)
            (correctedLimit, (_: EXPR) => Left(CommonError(s"Verifier complexity limit = $correctedLimit is exceeded")))
          else
            (defaultLimit, (_: EXPR) => Left(CommonError(s"Verifier complexity limit = $defaultLimit is exceeded")))
        else
          (defaultLimit, (_: EXPR) => Right(default))

      val (log, unusedComplexity, result) =
        EvaluatorV2.applyOrDefault(
          ctx,
          expr,
          logExtraInfo,
          script.stdLibVersion,
          limit,
          correctFunctionCallScope = checkEstimatorSumOverflow,
          newMode = newEvaluatorMode,
          onExceed
        )

      (log, limit - unusedComplexity, result)
    }

    script match {
      case s: ExprScript =>
        evalVerifier(isContract = false, (_, ctx) => evaluate(ctx, s.expr, LogExtraInfo(), s.stdLibVersion))

      case ContractScript.ContractScriptImpl(v, DApp(_, decls, _, Some(vf))) =>
        val partialEvaluate: (DirectiveSet, EvaluationContext[Environment, Id]) => (Log[Id], Int, Either[ExecutionError, EVALUATED]) = {
          (directives, ctx) =>
            val verify          = ContractEvaluator.verify(decls, vf, evaluate(ctx, _, _, v), _)
            val bindingsVersion = if (useCorrectScriptVersion) directives.stdLibVersion else V3
            in.eliminate(
              t =>
                RealTransactionWrapper(t, blockchain, directives.stdLibVersion, DAppTarget)
                  .fold(
                    e => (Nil, 0, Left(e)),
                    tx => verify(Bindings.transactionObject(tx, proofsEnabled = true, bindingsVersion, fixBigScriptField))
                  ),
              _.eliminate(
                t => verify(Bindings.orderObject(RealTransactionWrapper.ord(t), proofsEnabled = true)),
                _ => ???
              )
            )
        }
        evalVerifier(isContract = true, partialEvaluate)

      case ContractScript.ContractScriptImpl(_, DApp(_, _, _, None)) =>
        val proven: Proven & Authorized =
          in.eliminate(
            _.asInstanceOf[Proven & Authorized],
            _.eliminate(
              _.asInstanceOf[Proven & Authorized],
              _ => ???
            )
          )
        (Nil, 0, Verifier.verifyAsEllipticCurveSignature(proven, checkWeakPk).bimap(_.err, _ => TRUE))

      case other =>
        (Nil, 0, Left(s"$other: Unsupported script version"))
    }
  }
}
