package com.gicsports.transaction

import cats.data.ValidatedNel
import com.gicsports.lang.ValidationError

package object validation {
  type ValidatedV[A] = ValidatedNel[ValidationError, A]
  type ValidatedNV   = ValidatedV[Unit]
}
