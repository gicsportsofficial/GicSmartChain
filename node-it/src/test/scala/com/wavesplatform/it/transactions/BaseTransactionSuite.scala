package com.gicsports.it.transactions

import com.gicsports.it.*
import org.scalatest.*

trait BaseTransactionSuiteLike extends WaitForHeight2 with IntegrationSuiteWithThreeAddresses with BeforeAndAfterAll with NodesFromDocker {
  this: TestSuite & Nodes =>
}

abstract class BaseTransactionSuite extends funsuite.AnyFunSuite with BaseTransactionSuiteLike
