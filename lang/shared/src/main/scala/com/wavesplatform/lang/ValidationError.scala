package com.gicsports.lang

trait ValidationError

object ValidationError {
  type Validation[T] = Either[ValidationError, T]

  case class ScriptParseError(m: String)     extends ValidationError
  case class ScriptRunsLimitError(m: String) extends ValidationError

}
