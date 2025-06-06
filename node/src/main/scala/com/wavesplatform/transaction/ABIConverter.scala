package com.gicsports.transaction

import cats.instances.either.*
import cats.instances.vector.*
import cats.syntax.either.*
import cats.syntax.traverse.*
import com.esaulpaugh.headlong.abi.{Function, Tuple}
import com.esaulpaugh.headlong.util.FastHex
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.FunctionHeader
import com.gicsports.lang.v1.compiler.Terms.{EVALUATED, FUNCTION_CALL}
import com.gicsports.lang.v1.compiler.Types.TypeExt
import com.gicsports.lang.v1.compiler.{Terms, Types}
import com.gicsports.lang.{Global, ValidationError}
import com.gicsports.transaction.ABIConverter.WavesByteRepr
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.smart.InvokeScriptTransaction
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Type
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

import scala.jdk.CollectionConverters.*

object ABIConverter {
  val WavesByteRepr: ByteStr      = ByteStr(new Array[Byte](32))
  val PaymentListType: Types.LIST = Types.LIST(Types.TUPLE(List(Types.BYTESTR, Types.LONG)))
  val PaymentArgSignature: String = "(bytes32,int64)[]"
  val PaymentArgJson: JsObject = Json.obj(
    "name" -> "payments",
    "type" -> "tuple[]",
    "components" -> Json.arr(
      Json.obj("name" -> "assetId", "type" -> "bytes32"),
      Json.obj("name" -> "amount", "type"  -> "int64")
    )
  )

  private def buildMethodId(str: String): String = {
    val cls    = Class.forName("org.web3j.abi.FunctionEncoder")
    val method = cls.getDeclaredMethod("buildMethodId", classOf[String])
    method.setAccessible(true)
    method.invoke(null, str).asInstanceOf[String]
  }

  def ethType(argType: Types.FINAL): String =
    (ethTypeObj(argType) \ "type").as[String]

  def ethTypeObj(argType: Types.FINAL): JsObject = {
    def t(s: String) = Json.obj("type" -> s)

    argType match {
      case Types.BOOLEAN => t("bool")
      case Types.LONG    => t("int64")
      case Types.BYTESTR => t("bytes")
      case Types.STRING  => t("string")
      case Types.LIST(innerType) =>
        val base = ethTypeObj(innerType)
        if (base.value("type").asInstanceOf[JsString].value == "tuple") {
          Json.obj(
            "type"       -> "tuple[]",
            "components" -> base.value("components")
          )
        } else {
          Json.obj("type" -> (base.value("type").as[String] + "[]"))
        }
      case Types.UNION(tpe :: Nil, _) => ethTypeObj(tpe)
      // only for payments
      case Types.TUPLE(types) =>
        t("tuple") ++ Json.obj(
          "components" -> types.map(t => Json.obj("name" -> t.name) ++ ethTypeObj(t))
        )

      case other => throw new IllegalArgumentException(s"ethTypeObj: Unexpected type: $other")
    }
  }

  def ethFuncSignatureTypeName(argType: Types.FINAL): String = argType match {
    case Types.BOOLEAN              => "bool"
    case Types.LONG                 => "int64"
    case Types.BYTESTR              => "bytes"
    case Types.STRING               => "string"
    case Types.LIST(innerType)      => s"${ethFuncSignatureTypeName(innerType)}[]"
    case Types.UNION(tpe :: Nil, _) => ethFuncSignatureTypeName(tpe)
    case Types.TUPLE(types)         => s"(${types.map(ethFuncSignatureTypeName).mkString(",")})"
    case other                      => throw new IllegalArgumentException(s"ethFuncSignatureTypeName: Unexpected type: $other")
  }

  def toRideValue(ethArg: Any, rideType: Types.FINAL): Either[ValidationError, EVALUATED] = ethArg match {
    case bool: Boolean        => Terms.CONST_BOOLEAN(bool).asRight
    case i: Int               => Terms.CONST_LONG(i).asRight
    case l: Long              => Terms.CONST_LONG(l).asRight
    case byteArr: Array[Byte] => Terms.CONST_BYTESTR(ByteStr(byteArr)).leftMap(ce => GenericError(ce.message))
    case str: String          => Terms.CONST_STRING(str).leftMap(ce => GenericError(ce.message))

    case arr: Array[?] =>
      val innerType = rideType match {
        case list: Types.LIST =>
          list.innerType
        case _ =>
          Types.ANY
      }
      arr.toVector
        .traverse(el => toRideValue(el, innerType))
        .flatMap[ValidationError, EVALUATED] { validArgs =>
          Terms.ARR(validArgs, limited = true).leftMap(ee => GenericError(ee.message))
        }

    case t: Tuple =>
      t.asScala.toVector
        .traverse(el => toRideValue(el, Types.ANY))
        .flatMap[ValidationError, EVALUATED] { validArgs =>
          Terms.ARR(validArgs, limited = true).leftMap(ee => GenericError(ee.message))
        }

    case _ => throw new UnsupportedOperationException(s"Type not supported: $ethArg")
  }
}

final case class ABIConverter(script: Script) {
  case class FunctionArg(name: String, rideType: Types.FINAL) {
    lazy val ethType: String = ABIConverter.ethType(rideType)

    def ethTypeRef: TypeReference[Type[?]] = TypeReference.makeTypeReference(ethType).asInstanceOf[TypeReference[Type[?]]]
  }

  case class FunctionRef(name: String, args: Seq[FunctionArg]) {

    def decodeArgs(data: String): Either[ValidationError, (List[EVALUATED], Seq[InvokeScriptTransaction.Payment])] =
      new Function(ethSignature)
        .decodeCall(FastHex.decode(data))
        .asScala
        .toList
        .zip(args.map(_.rideType) :+ ABIConverter.PaymentListType)
        .traverse { case (ethArg, rideT) => ABIConverter.toRideValue(ethArg, rideT) }
        .flatMap { alldecodedArgs =>
          (alldecodedArgs.last match {
            case Terms.ARR(xs) =>
              xs.toVector.traverse {
                case Terms.ARR(fields) =>
                  fields match {
                    case Seq(Terms.CONST_BYTESTR(assetId), Terms.CONST_LONG(amount)) =>
                      Right(
                        InvokeScriptTransaction.Payment(
                          amount,
                          assetId match {
                            case WavesByteRepr => Asset.Waves
                            case assetId       => Asset.IssuedAsset(assetId)
                          }
                        )
                      )

                    case other => Left(GenericError(s"decodeArgs: unexpected term in payment: $other"))
                  }
                case other => Left(GenericError(s"decodeArgs: unexpected term in payment: $other"))
              }

            case _ => Right(Nil)
          }).map(ps => (alldecodedArgs.init, ps))

        }

    lazy val ethSignature: String = {
      val argTypes = args.map(_.rideType).map(ABIConverter.ethFuncSignatureTypeName) :+ ABIConverter.PaymentArgSignature
      s"$name(${argTypes.mkString(",")})"
    }

    lazy val ethMethodId: String = ABIConverter.buildMethodId(ethSignature)
  }

  private[this] lazy val funcsWithTypes =
    Global
      .dAppFuncTypes(script)
      .map { signatures =>
        val filtered = signatures.argsWithFuncName.filter { case (_, args) =>
          !args.exists { case (_, tpe) => tpe.containsUnion }
        }
        signatures.copy(argsWithFuncName = filtered)
      }

  private[this] def functionsWithArgs: Seq[(String, List[(String, Types.FINAL)])] = {
    funcsWithTypes match {
      case Right(signatures) => signatures.argsWithFuncName.toSeq
      case Left(_)           => Nil
    }
  }

  lazy val funcByMethodId: Map[String, FunctionRef] =
    functionsWithArgs
      .map { case (funcName, args) =>
        FunctionRef(funcName, args.map { case (name, argType) => FunctionArg(name, argType) })
      }
      .map(func => func.ethMethodId -> func)
      .toMap

  def jsonABI: JsArray =
    JsArray(functionsWithArgs.map { case (funcName, args) =>
      val inputs = args.map { case (argName, argType) =>
        Json.obj("name" -> argName) ++ ABIConverter.ethTypeObj(argType)
      } :+ ABIConverter.PaymentArgJson

      Json.obj(
        "name"            -> funcName,
        "type"            -> "function",
        "constant"        -> false,
        "payable"         -> false,
        "stateMutability" -> "nonpayable",
        "inputs"          -> inputs,
        "outputs"         -> JsArray.empty
      )
    })

  def decodeFunctionCall(data: String): Either[ValidationError, (FUNCTION_CALL, Seq[InvokeScriptTransaction.Payment])] = {
    val methodId = data.substring(0, 8)
    for {
      function        <- funcByMethodId.get("0x" + methodId).toRight[ValidationError](GenericError(s"Function not defined: $methodId"))
      argsAndPayments <- function.decodeArgs(data)
    } yield (FUNCTION_CALL(FunctionHeader.User(function.name), argsAndPayments._1), argsAndPayments._2)
  }
}
