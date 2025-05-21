package com.gicsports.network

import com.gicsports.common.state.ByteStr
import com.gicsports.crypto.*
import com.gicsports.test.FreeSpec
import org.scalacheck.Gen

class MicroBlockInvSpecSpec extends FreeSpec {

  private val microBlockInvGen: Gen[MicroBlockInv] = for {
    acc          <- accountGen
    totalSig     <- byteArrayGen(SignatureLength)
    prevBlockSig <- byteArrayGen(SignatureLength)
  } yield MicroBlockInv(acc, ByteStr(totalSig), ByteStr(prevBlockSig))

  "MicroBlockInvMessageSpec" - {
    import MicroBlockInvSpec.*

    "deserializeData(serializedData(data)) == data" in forAll(microBlockInvGen) { inv =>
      inv.signaturesValid() should beRight
      val restoredInv = deserializeData(serializeData(inv)).get
      restoredInv.signaturesValid() should beRight

      restoredInv shouldBe inv
    }
  }

}
