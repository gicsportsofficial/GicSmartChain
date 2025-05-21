package com.gicsports.api.http

case class ApiException(apiError: ApiError) extends Exception(apiError.message)
