package com.gicsports.api.http.utils

import cats.Id
import cats.syntax.either.*
import com.gicsports.account.{Address, AddressOrAlias, AddressScheme, PublicKey}
import com.gicsports.common.state.ByteStr
import com.gicsports.features.EstimatorProvider.*
import com.gicsports.features.EvaluatorFixProvider.*
import com.gicsports.lang.contract.DApp
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.{DApp as DAppType, *}
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.Terms.{EVALUATED, EXPR}
import com.gicsports.lang.v1.compiler.{ContractScriptCompactor, ExpressionCompiler, Terms}
import com.gicsports.lang.v1.evaluator.ContractEvaluator.{Invocation, LogExtraInfo}
import com.gicsports.lang.v1.evaluator.{ContractEvaluator, EvaluatorV2, Log}
import com.gicsports.lang.v1.traits.Environment.Tthis
import com.gicsports.lang.v1.traits.domain.Recipient
import com.gicsports.lang.v1.{ContractLimits, FunctionHeader}
import com.gicsports.lang.{ValidationError, utils}
import com.gicsports.state.diffs.invoke.InvokeScriptTransactionLike
import com.gicsports.state.{Blockchain, Diff}
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TransactionType.TransactionType
import com.gicsports.transaction.TxValidationError.{GenericError, InvokeRejectError}
import com.gicsports.transaction.smart.*
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.{Asset, TransactionType}
import monix.eval.Coeval
import shapeless.Coproduct

object UtilsEvaluator {
  def compile(version: StdLibVersion)(str: String): Either[GenericError, EXPR] =
    ExpressionCompiler
      .compileUntyped(str, utils.compilerContext(version, Expression, isAssetScript = false).copy(arbitraryDeclarations = true))
      .leftMap(GenericError(_))

  def toExpr(script: Script, invocation: Invocation): Either[ValidationError, EXPR] =
    ContractEvaluator
      .buildExprFromInvocation(script.expr.asInstanceOf[DApp], invocation, script.stdLibVersion)
      .bimap(e => GenericError(e.message), _.expr)

  def executeExpression(blockchain: Blockchain, script: Script, address: Address, pk: PublicKey, limit: Int)(
      expr: EXPR
  ): Either[ValidationError, (EVALUATED, Int, Log[Id])] =
    for {
      ds <- DirectiveSet(script.stdLibVersion, Account, DAppType).leftMap(GenericError(_))
      invoke = new InvokeScriptTransactionLike {
        override def dApp: AddressOrAlias          = address
        override def funcCall: Terms.FUNCTION_CALL = Terms.FUNCTION_CALL(FunctionHeader.User(""), Nil)
        // Payments, that are mapped to RIDE structure, is taken from Invocation,
        // while this field used for validation inside InvokeScriptTransactionDiff,
        // that unused in the current implementation.
        override def payments: Seq[Payment]            = Seq.empty
        override def root: InvokeScriptTransactionLike = this
        override val sender: PublicKey                 = PublicKey(ByteStr(new Array[Byte](32)))
        override def assetFee: (Asset, Long)           = Asset.Waves -> 0L
        override def timestamp: Long                   = System.currentTimeMillis()
        override def chainId: Byte                     = AddressScheme.current.chainId
        override def id: Coeval[ByteStr]               = Coeval.evalOnce(ByteStr.empty)
        override def checkedAssets: Seq[IssuedAsset]   = Seq.empty
        override val tpe: TransactionType              = TransactionType.InvokeScript
      }
      environment = new DAppEnvironment(
        AddressScheme.current.chainId,
        Coeval.raiseError(new IllegalStateException("No input entity available")),
        Coeval.evalOnce(blockchain.height),
        blockchain,
        Coproduct[Tthis](Recipient.Address(ByteStr(address.bytes))),
        ds,
        invoke,
        address,
        pk,
        Set.empty[Address],
        limitedExecution = false,
        limit,
        remainingCalls = ContractLimits.MaxSyncDAppCalls(script.stdLibVersion),
        availableActions = ContractLimits.MaxCallableActionsAmountBeforeV6(script.stdLibVersion),
        availableBalanceActions = ContractLimits.MaxBalanceScriptActionsAmountV6,
        availableAssetActions = ContractLimits.MaxAssetScriptActionsAmountV6,
        availablePayments = ContractLimits.MaxTotalPaymentAmountRideV6,
        availableData = ContractLimits.MaxWriteSetSize,
        availableDataSize = ContractLimits.MaxTotalWriteSetSizeInBytes,
        currentDiff = Diff.empty,
        invocationRoot = DAppEnvironment.InvocationTreeTracker(DAppEnvironment.DAppInvocation(address, null, Nil))
      )
      ctx  = BlockchainContext.build(ds, environment, fixUnicodeFunctions = true, useNewPowPrecision = true, fixBigScriptField = true)
      call = ContractEvaluator.buildSyntheticCall(ContractScriptCompactor.decompact(script.expr.asInstanceOf[DApp]), expr)
      limitedResult <- EvaluatorV2
        .applyLimitedCoeval(
          call,
          LogExtraInfo(),
          limit,
          ctx,
          script.stdLibVersion,
          correctFunctionCallScope = blockchain.checkEstimatorSumOverflow,
          newMode = blockchain.newEvaluatorMode,
          checkConstructorArgsTypes = true
        )
        .value()
        .leftMap { case (err, _, log) => InvokeRejectError(err.message, log) }
      result <- limitedResult match {
        case (eval: EVALUATED, unusedComplexity, log) => Right((eval, limit - unusedComplexity, log))
        case (_: EXPR, _, log)                        => Left(InvokeRejectError(s"Calculation complexity limit exceeded", log))
      }
    } yield result
}
