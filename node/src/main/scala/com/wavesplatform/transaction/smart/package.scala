package com.gicsports.transaction

import cats.syntax.either._
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.{Account, Expression, Asset => AssetType, DApp => DAppType}
import com.gicsports.lang.v1.traits.Environment.{InputEntity, Tthis}
import com.gicsports.state.Blockchain
import com.gicsports.transaction.smart.script.ScriptRunner.TxOrd
import com.gicsports.transaction.smart.{DApp => DAppTarget}
import shapeless._

package object smart {
  def buildThisValue(
      in: TxOrd,
      blockchain: Blockchain,
      ds: DirectiveSet,
      scriptContainerAddress: Tthis
  ): Either[String, InputEntity] =
    in.eliminate(
      tx =>
        RealTransactionWrapper(tx, blockchain, ds.stdLibVersion, paymentTarget(ds, scriptContainerAddress))
          .map(Coproduct[InputEntity](_)),
      _.eliminate(
        order => Coproduct[InputEntity](RealTransactionWrapper.ord(order)).asRight[String],
        _.eliminate(
          scriptTransfer => Coproduct[InputEntity](scriptTransfer).asRight[String],
          _ => ???
        )
      )
    )

  def paymentTarget(
      ds: DirectiveSet,
      scriptContainerAddress: Tthis
  ): AttachedPaymentTarget =
    (ds.scriptType, ds.contentType) match {
      case (Account, DAppType)   => DAppTarget
      case (Account, Expression) => InvokerScript
      case (AssetType, Expression) =>
        scriptContainerAddress.eliminate(
          _ => throw new Exception("Not a AssetId"),
          _.eliminate(a => AssetScript(ByteStr(a.id)), v => throw new Exception(s"Fail processing tthis value $v"))
        )
      case _ => ???
    }
}
