package com.gicsports.account

import com.google.common.collect.Interners
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.Base58
import com.gicsports.crypto._
import com.gicsports.transaction.TxValidationError.InvalidAddress
import com.gicsports.utils.base58Length
import org.web3j.crypto.Keys
import play.api.libs.json.{Format, Writes}
import supertagged._
import supertagged.postfix._

object PublicKey extends TaggedType[ByteStr] {
  private[this] val interner = Interners.newWeakInterner[PublicKey]()

  val KeyStringLength: Int = base58Length(KeyLength)

  def isValidSize(length: Int): Boolean = length == KeyLength || length == EthereumKeyLength

  def apply(publicKey: ByteStr): PublicKey = {
    require(isValidSize(publicKey.size), s"invalid public key length: ${publicKey.arr.length}")
    interner.intern(publicKey @@ this)
  }

  def apply(publicKey: Array[Byte]): PublicKey =
    apply(ByteStr(publicKey))

  def fromBase58String(base58: String): Either[InvalidAddress, PublicKey] =
    (for {
      _     <- Either.cond(base58.length <= KeyStringLength, (), "Bad public key string length")
      bytes <- Base58.tryDecodeWithLimit(base58).toEither.left.map(ex => s"Unable to decode base58: ${ex.getMessage}")
    } yield PublicKey(bytes)).left.map(err => InvalidAddress(s"Invalid sender: $err"))

  def unapply(arg: Array[Byte]): Option[PublicKey] =
    Some(apply(arg))

  implicit class PublicKeyImplicitOps(private val pk: PublicKey) extends AnyVal {
    def toAddress: Address = toAddress(AddressScheme.current.chainId)
    def toAddress(chainId: Byte): Address = pk.size match {
      case KeyLength         => Address.fromPublicKey(pk, chainId)
      case EthereumKeyLength => Address(Keys.getAddress(pk.arr), chainId)
      case other             => throw new IllegalArgumentException(s"Unexpected public key length: $other")
    }
  }

  implicit lazy val jsonFormat: Format[PublicKey] = Format[PublicKey](
    com.gicsports.utils.byteStrFormat.map(this.apply),
    Writes(pk => com.gicsports.utils.byteStrFormat.writes(pk))
  )
}
