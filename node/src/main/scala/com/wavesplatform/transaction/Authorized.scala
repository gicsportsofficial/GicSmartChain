package com.gicsports.transaction
import com.gicsports.account.PublicKey

trait Authorized {
  val sender: PublicKey
}
