package com.gicsports

sealed trait PublicKey

package object account {

  type PublicKey  = PublicKey.Type
  type PrivateKey = PrivateKey.Type
}
