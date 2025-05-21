package com.gicsports.api.http

import com.gicsports.account.Address
import com.gicsports.lang.contract.meta.FunctionSignatures
import com.gicsports.transaction.Transaction
import com.gicsports.transaction.smart.script.trace.TraceStep
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.*

trait JsonFormats {
  implicit lazy val wavesAddressWrites: Writes[Address] = Writes(w => JsString(w.toString))

  implicit lazy val TransactionJsonWrites: OWrites[Transaction] = OWrites(_.json())

  implicit lazy val logWrites: Writes[TraceStep] = Writes(_.json)

  implicit lazy val functionSignaturesWrites: Writes[FunctionSignatures] =
    (o: FunctionSignatures) =>
      Json.obj(
        "version"          -> o.version.toString,
        "callableFuncTypes" -> Json.obj(
          o.argsWithFuncName.map {
            case (functionName, args) =>
              val functionArgs: JsValueWrapper =
                args.map {
                  case (argName, argType) =>
                    Json.obj(
                      "name" -> argName,
                      "type" -> argType.name
                    )
                }
              functionName -> functionArgs
          }.toSeq*
        )
      )

}
