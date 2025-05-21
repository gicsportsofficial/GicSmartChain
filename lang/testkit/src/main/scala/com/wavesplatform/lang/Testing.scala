package com.gicsports.lang
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.v1.compiler.Terms.*

object Testing {

  def evaluated(i: Any): Either[ExecutionError, EVALUATED] = i match {
    case s: String  => CONST_STRING(s)
    case s: Long    => Right(CONST_LONG(s))
    case s: Int     => Right(CONST_LONG(s))
    case s: ByteStr => CONST_BYTESTR(s)
    case s: CaseObj => Right(s)
    case s: Boolean => Right(CONST_BOOLEAN(s))
    case a: Seq[?]  => ARR(a.map(x => evaluated(x).explicitGet()).toIndexedSeq, false)
    case _          => Left("Bad Assert: unexpected type")
  }
}
