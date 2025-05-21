package com.gicsports.transaction

import com.gicsports.common.state.ByteStr
import com.gicsports.crypto
import monix.eval.Coeval

trait FastHashId extends Proven {
  val id: Coeval[ByteStr] = Coeval.evalOnce(ByteStr(crypto.fastHash(bodyBytes())))
}
