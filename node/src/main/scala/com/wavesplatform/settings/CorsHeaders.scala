package com.gicsports.settings

case class CorsHeaders(
    accessControlAllowHeaders: Seq[String],
    accessControlAllowOrigin: String,
    accessControlAllowMethods: Seq[String],
    accessControlAllowCredentials: Boolean
)
