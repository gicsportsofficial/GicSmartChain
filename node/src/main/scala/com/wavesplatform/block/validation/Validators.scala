package com.gicsports.block.validation

import cats.syntax.either._
import com.gicsports.block.Block.{GenerationSignatureLength, GenerationVRFSignatureLength, MaxFeaturesInBlock, ProtoBlockVersion}
import com.gicsports.block.{Block, MicroBlock}
import com.gicsports.crypto
import com.gicsports.crypto.KeyLength
import com.gicsports.mining.Miner.MaxTransactionsPerMicroblock
import com.gicsports.settings.GenesisSettings
import com.gicsports.transaction.GenesisTransaction
import com.gicsports.transaction.TxValidationError.GenericError

object Validators {
  type Validation[A] = Either[GenericError, A]

  def validateBlock(b: Block): Validation[Block] =
    (for {
      _ <- Either.cond(Block.validateReferenceLength(b.header.reference.arr.length), (), "Incorrect reference")
      genSigLength = if (b.header.version < ProtoBlockVersion) GenerationSignatureLength else GenerationVRFSignatureLength
      _ <- Either.cond(b.header.generationSignature.arr.length == genSigLength, (), "Incorrect generationSignature")
      _ <- Either.cond(b.header.generator.arr.length == KeyLength, (), "Incorrect signer")
      _ <- Either.cond(
        b.header.version > 2 || b.header.featureVotes.isEmpty,
        (),
        s"Block version ${b.header.version} could not contain feature votes"
      )
      _ <- Either.cond(b.header.featureVotes.distinct.size == b.header.featureVotes.size, (), s"Duplicates in feature votes")
      _ <- Either.cond(b.header.version < ProtoBlockVersion || b.header.featureVotes.sorted == b.header.featureVotes, (), s"Unsorted feature votes")
      _ <- Either.cond(b.header.featureVotes.size <= MaxFeaturesInBlock, (), s"Block could not contain more than $MaxFeaturesInBlock feature votes")
    } yield b).leftMap(GenericError(_))

  def validateGenesisBlock(block: Block, genesisSettings: GenesisSettings, rideV6Activated: Boolean): Validation[Block] =
    for {
      // Common validation
      _ <- validateBlock(block)
      // Verify signature
      _ <- Either.cond(
        crypto.verify(block.signature, block.bodyBytes(), block.header.generator, rideV6Activated),
        (),
        GenericError("Passed genesis signature is not valid")
      )
      // Verify initial balance
      txsSum = block.transactionData.collect { case tx: GenesisTransaction => tx.amount.value }.reduce(Math.addExact(_: Long, _: Long))
      _ <- Either.cond(
        txsSum == genesisSettings.initialBalance,
        (),
        GenericError(s"Initial balance ${genesisSettings.initialBalance} did not match the distributions sum $txsSum")
      )
    } yield block

  def validateMicroBlock(mb: MicroBlock): Validation[MicroBlock] =
    (for {
      _ <- Either.cond(
        MicroBlock.validateReferenceLength(mb.version, mb.reference.arr.length),
        (),
        s"Incorrect prevResBlockSig: ${mb.reference.arr.length}"
      )
      _ <- Either.cond(
        mb.totalResBlockSig.arr.length == crypto.SignatureLength,
        (),
        s"Incorrect totalResBlockSig: ${mb.totalResBlockSig.arr.length}"
      )
      _ <- Either.cond(mb.sender.arr.length == KeyLength, (), s"Incorrect generator.publicKey: ${mb.sender.arr.length}")
      _ <- Either.cond(mb.transactionData.nonEmpty, (), "cannot create empty MicroBlock")
      _ <- Either.cond(
        mb.transactionData.size <= MaxTransactionsPerMicroblock,
        (),
        s"too many txs in MicroBlock: allowed: $MaxTransactionsPerMicroblock, actual: ${mb.transactionData.size}"
      )
    } yield mb).leftMap(GenericError(_))
}
