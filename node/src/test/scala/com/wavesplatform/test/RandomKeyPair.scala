package com.gicsports.test

import java.util.concurrent.ThreadLocalRandom

import com.google.common.primitives.Longs
import com.gicsports.account.KeyPair

object RandomKeyPair {
  def apply(): KeyPair =
    KeyPair(Longs.toByteArray(ThreadLocalRandom.current().nextLong()))
}
