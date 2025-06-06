package com.gicsports.lang

sealed trait ExecutionError {
  def message: String
}
case class CommonError(details: String, cause: Option[ValidationError] = None) extends ExecutionError {
  override def toString: String = s"CommonError($message)"
  override def message: String  = cause.map(_.toString).getOrElse(details)
}
case class FailOrRejectError(message: String, skipInvokeComplexity: Boolean = true) extends ExecutionError with ValidationError
