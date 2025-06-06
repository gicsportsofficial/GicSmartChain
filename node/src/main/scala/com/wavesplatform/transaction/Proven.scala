package com.gicsports.transaction
import com.gicsports.common.state.ByteStr
import monix.eval.Coeval

trait Proven extends Authorized {
  def proofs: Proofs
  val bodyBytes: Coeval[Array[Byte]]
}

object Proven {
  implicit class ProvenExt(private val p: Proven) extends AnyVal {
    def signature: ByteStr = p.proofs.toSignature
  }
}
