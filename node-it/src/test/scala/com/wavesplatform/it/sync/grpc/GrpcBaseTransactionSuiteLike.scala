package com.gicsports.it.sync.grpc

import com.gicsports.it.{GrpcIntegrationSuiteWithThreeAddress, GrpcWaitForHeight, Nodes}
import org.scalatest._

trait GrpcBaseTransactionSuiteLike extends GrpcWaitForHeight with GrpcIntegrationSuiteWithThreeAddress { this: TestSuite with Nodes =>
}

abstract class GrpcBaseTransactionSuite extends funsuite.AnyFunSuite with GrpcBaseTransactionSuiteLike
