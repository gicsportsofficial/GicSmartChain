package com.gicsports.transaction

trait SigProofsSwitch extends ProvenTransaction { self: Transaction with VersionedTransaction =>
  def usesLegacySignature: Boolean =
    self.version == Transaction.V1
}
