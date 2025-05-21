package com.gicsports.account

import com.gicsports.common.state.ByteStr
import com.gicsports.crypto.KeyLength
import play.api.libs.json.{Format, Writes}
import supertagged._
import supertagged.postfix._

object PrivateKey extends TaggedType[ByteStr] {
  def apply(privateKey: ByteStr): PrivateKey = {
    require(privateKey.arr.length == KeyLength, s"invalid private key length: ${privateKey.arr.length}")
    privateKey @@ PrivateKey
  }

  def apply(privateKey: Array[Byte]): PrivateKey =
    apply(ByteStr(privateKey))

  def unapply(arg: Array[Byte]): Option[PrivateKey] =
    Some(apply(arg))

  implicit lazy val jsonFormat: Format[PrivateKey] = Format[PrivateKey](
    com.gicsports.utils.byteStrFormat.map(this.apply),
    Writes(pk => com.gicsports.utils.byteStrFormat.writes(pk))
  )
}
