package com.gicsports.test

import java.util.concurrent.ThreadLocalRandom

import com.gicsports.account.{Address, KeyPair}
import com.gicsports.crypto.KeyLength

package object node {
  def randomKeyPair(): KeyPair = {
    val seed = new Array[Byte](KeyLength)
    ThreadLocalRandom.current().nextBytes(seed)
    KeyPair(seed)
  }

  def randomAddress(): Address = {
    val seed = new Array[Byte](Address.HashLength)
    ThreadLocalRandom.current().nextBytes(seed)
    Address(seed)
  }
}
