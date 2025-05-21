package com.gicsports.block

import com.gicsports.account.{KeyPair, PublicKey}
import com.gicsports.block.Block.BlockId
import com.gicsports.block.serialization.MicroBlockSerializer
import com.gicsports.common.state.ByteStr
import com.gicsports.crypto
import com.gicsports.lang.ValidationError
import com.gicsports.state.*
import com.gicsports.transaction.*
import monix.eval.Coeval

import scala.util.Try

case class MicroBlock(
    version: Byte,
    sender: PublicKey,
    transactionData: Seq[Transaction],
    reference: BlockId,
    totalResBlockSig: BlockId,
    signature: ByteStr
) extends Signed {
  val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(MicroBlockSerializer.toBytes(this))

  private[block] val bytesWithoutSignature: Coeval[Array[Byte]] = Coeval.evalOnce(copy(signature = ByteStr.empty).bytes())

  override val signatureValid: Coeval[Boolean]        = Coeval.evalOnce(crypto.verify(signature, bytesWithoutSignature(), sender))
  override val signedDescendants: Coeval[Seq[Signed]] = Coeval.evalOnce(transactionData.flatMap(_.cast[Signed]))

  override def toString: String = s"MicroBlock(... -> ${reference.trim}, txs=${transactionData.size}"

  def stringRepr(totalBlockId: ByteStr): String = s"MicroBlock(${totalBlockId.trim} -> ${reference.trim}, txs=${transactionData.size})"
}

object MicroBlock {
  def buildAndSign(
      version: Byte,
      generator: KeyPair,
      transactionData: Seq[Transaction],
      reference: BlockId,
      totalResBlockSig: BlockId
  ): Either[ValidationError, MicroBlock] =
    MicroBlock(version, generator.publicKey, transactionData, reference, totalResBlockSig, ByteStr.empty).validate
      .map(_.sign(generator.privateKey))

  def parseBytes(bytes: Array[Byte]): Try[MicroBlock] =
    MicroBlockSerializer
      .parseBytes(bytes)
      .flatMap(_.validateToTry)

  def validateReferenceLength(version: Byte, length: Int): Boolean =
    length == Block.referenceLength(version)
}
