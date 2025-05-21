package com.gicsports.transaction

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.gicsports.account.Address
import com.gicsports.common.state.ByteStr
import com.gicsports.crypto
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.serialization.impl.GenesisTxSerializer
import com.gicsports.transaction.validation.{TxConstraints, TxValidator}
import monix.eval.Coeval
import play.api.libs.json.JsObject

import scala.util.Try

case class GenesisTransaction(recipient: Address, amount: TxNonNegativeAmount, timestamp: TxTimestamp, signature: ByteStr, chainId: Byte)
    extends Transaction(TransactionType.Genesis) {
  override val assetFee: (Asset, Long) = (Waves, 0)
  override val id: Coeval[ByteStr]     = Coeval.evalOnce(signature)

  val bodyBytes: Coeval[Array[Byte]]      = Coeval.evalOnce(GenesisTxSerializer.toBytes(this))
  override val bytes: Coeval[Array[Byte]] = bodyBytes
  override val json: Coeval[JsObject]     = Coeval.evalOnce(GenesisTxSerializer.toJson(this))
}

object GenesisTransaction extends TransactionParser {
  type TransactionT = GenesisTransaction

  override val typeId: TxType                    = 1: Byte
  override val supportedVersions: Set[TxVersion] = Set(1)

  override def parseBytes(bytes: Array[TxVersion]): Try[GenesisTransaction] =
    GenesisTxSerializer.parseBytes(bytes)

  implicit val validator: TxValidator[GenesisTransaction] =
    tx =>
      TxConstraints.seq(tx)(
        TxConstraints.addressChainId(tx.recipient, tx.chainId)
      )

  def generateSignature(recipient: Address, amount: Long, timestamp: Long): Array[Byte] = {
    val payload = Bytes.concat(Ints.toByteArray(typeId), Longs.toByteArray(timestamp), recipient.bytes, Longs.toByteArray(amount))
    val hash    = crypto.fastHash(payload)
    Bytes.concat(hash, hash)
  }

  def create(recipient: Address, amount: Long, timestamp: Long): Either[ValidationError, GenesisTransaction] = {
    val signature = ByteStr(GenesisTransaction.generateSignature(recipient, amount, timestamp))

    for {
      amount <- TxNonNegativeAmount(amount)(TxValidationError.NegativeAmount(amount, "GIC"))
      tx     <- GenesisTransaction(recipient, amount, timestamp, signature, recipient.chainId).validatedEither
    } yield tx
  }
}
