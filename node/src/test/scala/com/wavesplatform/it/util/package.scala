package com.gicsports.it

import com.gicsports.account.{Address, AddressOrAlias, Alias}
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.v1.traits.domain.Recipient

package object util {
  implicit class AddressOrAliasExt(val a: AddressOrAlias) extends AnyVal {
    def toRide: Recipient =
      a match {
        case address: Address => Recipient.Address(ByteStr(address.bytes))
        case alias: Alias     => Recipient.Alias(alias.name)
        case _                => ???
      }
  }
}
