package com.gicsports.lang.contract.meta

import com.gicsports.lang.v1.compiler.Types.FINAL
import com.gicsports.protobuf.dapp.DAppMeta

private[meta] trait MetaMapperStrategy[V <: MetaVersion] {
  def toProto(data: List[List[FINAL]], nameMap: Map[String, String] = Map.empty): Either[String, DAppMeta]
  def fromProto(meta: DAppMeta): Either[String, List[List[FINAL]]]
}
