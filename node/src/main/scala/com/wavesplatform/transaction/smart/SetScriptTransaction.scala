package com.gicsports.transaction.smart

import com.gicsports.account.*
import com.gicsports.crypto
import com.gicsports.lang.ValidationError
import com.gicsports.lang.script.Script
import com.gicsports.transaction.*
import com.gicsports.transaction.serialization.impl.SetScriptTxSerializer
import com.gicsports.transaction.validation.TxValidator
import com.gicsports.transaction.validation.impl.SetScriptTxValidator
import monix.eval.Coeval
import play.api.libs.json.JsObject

import scala.util.Try

case class SetScriptTransaction(
    version: TxVersion,
    sender: PublicKey,
    script: Option[Script],
    fee: TxPositiveAmount,
    timestamp: TxTimestamp,
    proofs: Proofs,
    chainId: Byte
) extends Transaction(TransactionType.SetScript) with ProvenTransaction
    with VersionedTransaction
    with TxWithFee.InWaves
    with FastHashId
    with PBSince.V2 {

  val bodyBytes: Coeval[Array[Byte]]      = Coeval.evalOnce(SetScriptTxSerializer.bodyBytes(this))
  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(SetScriptTxSerializer.toBytes(this))
  override val json: Coeval[JsObject]     = Coeval.evalOnce(SetScriptTxSerializer.toJson(this))
}

object SetScriptTransaction extends TransactionParser {
  type TransactionT = SetScriptTransaction

  override val typeId: TxType                    = 13: Byte
  override val supportedVersions: Set[TxVersion] = Set(1, 2)

  implicit val validator: TxValidator[SetScriptTransaction] = SetScriptTxValidator

  implicit def sign(tx: SetScriptTransaction, privateKey: PrivateKey): SetScriptTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  override def parseBytes(bytes: Array[TxVersion]): Try[SetScriptTransaction] =
    SetScriptTxSerializer.parseBytes(bytes)

  def create(
      version: TxVersion,
      sender: PublicKey,
      script: Option[Script],
      fee: Long,
      timestamp: TxTimestamp,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, SetScriptTransaction] =
    for {
      fee <- TxPositiveAmount(fee)(TxValidationError.InsufficientFee)
      tx  <- SetScriptTransaction(version, sender, script, fee, timestamp, proofs, chainId).validatedEither
    } yield tx

  def signed(
      version: TxVersion,
      sender: PublicKey,
      script: Option[Script],
      fee: Long,
      timestamp: TxTimestamp,
      signer: PrivateKey,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, SetScriptTransaction] =
    create(version, sender, script, fee, timestamp, Proofs.empty, chainId).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      script: Option[Script],
      fee: Long,
      timestamp: TxTimestamp,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, SetScriptTransaction] =
    signed(version, sender.publicKey, script, fee, timestamp, sender.privateKey, chainId)
}
