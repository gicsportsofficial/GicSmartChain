package com.gicsports.generator.utils

import com.gicsports.generator.Preconditions.CreatedAccount
import com.gicsports.transaction.assets.IssueTransaction
import com.gicsports.transaction.lease.LeaseTransaction

object Universe {
  @volatile var Accounts: List[CreatedAccount]       = Nil
  @volatile var IssuedAssets: List[IssueTransaction] = Nil
  @volatile var Leases: List[LeaseTransaction]       = Nil
}
