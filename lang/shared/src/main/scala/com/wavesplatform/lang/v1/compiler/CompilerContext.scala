package com.gicsports.lang.v1.compiler

import cats.Semigroup
import com.gicsports.lang.v1.FunctionHeader
import com.gicsports.lang.v1.compiler.CompilerContext.*
import com.gicsports.lang.v1.compiler.Types.*
import com.gicsports.lang.v1.evaluator.ctx.FunctionTypeSignature
import com.gicsports.lang.v1.parser.Expressions.Pos
import com.gicsports.lang.v1.parser.Expressions.Pos.AnyPos
import shapeless.*

case class CompilerContext(
    predefTypes: Map[String, FINAL],
    varDefs: VariableTypes,
    functionDefs: FunctionTypes,
    provideRuntimeTypeOnCastError: Boolean,
    arbitraryDeclarations: Boolean = false,
    tmpArgsIdx: Int = 0,
    foldIdx: Int = 0
) {
  private lazy val allFuncDefs: FunctionTypes =
    predefTypes
      .collect { case (_, CASETYPEREF(typeName, fields, false)) =>
        typeName ->
          FunctionInfo(AnyPos, List(FunctionTypeSignature(CASETYPEREF(typeName, fields), fields, FunctionHeader.User(typeName))))
      } ++ functionDefs

  private def resolveFunction(name: String, args: Int): FunctionInfo =
    if (arbitraryDeclarations) {
      val signature = FunctionTypeSignature(ANY, Seq.fill(args)(("arg", ANY)), FunctionHeader.User(name))
      allFuncDefs.withDefaultValue(FunctionInfo(AnyPos, List(signature))).apply(name)
    } else {
      allFuncDefs.getOrElse(name, FunctionInfo(AnyPos, List.empty))
    }

  def functionTypeSignaturesByName(name: String, args: Int): List[FunctionTypeSignature] =
    resolveFunction(name, args).fSigList

  def resolveVar(name: String): Option[VariableInfo] =
    if (arbitraryDeclarations) {
      Some(varDefs.getOrElse(name, VariableInfo(AnyPos, NOTHING)))
    } else {
      varDefs.get(name)
    }

  def getSimpleContext(): Map[String, Pos] = {
    (varDefs.map(el => el._1 -> el._2.pos) ++ functionDefs.map(el => el._1 -> el._2.pos))
      .filter(_._2.start != -1)
  }
}

object CompilerContext {
  case class VariableInfo(pos: Pos, vType: FINAL)
  case class FunctionInfo(pos: Pos, fSigList: List[FunctionTypeSignature])

  type VariableTypes = Map[String, VariableInfo]
  type FunctionTypes = Map[String, FunctionInfo]

  implicit val semigroup: Semigroup[CompilerContext] = (x: CompilerContext, y: CompilerContext) =>
    CompilerContext(
      predefTypes = x.predefTypes ++ y.predefTypes,
      varDefs = x.varDefs ++ y.varDefs,
      functionDefs = x.functionDefs ++ y.functionDefs,
      y.provideRuntimeTypeOnCastError
    )

  val types: Lens[CompilerContext, Map[String, FINAL]] = lens[CompilerContext] >> Symbol("predefTypes")
  val vars: Lens[CompilerContext, VariableTypes]       = lens[CompilerContext] >> Symbol("varDefs")
  val functions: Lens[CompilerContext, FunctionTypes]  = lens[CompilerContext] >> Symbol("functionDefs")
}
