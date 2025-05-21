package com.gicsports.state.diffs

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.settings.TestFunctionalitySettings
import com.gicsports.test.*
import com.gicsports.transaction.{GenesisTransaction, TxHelpers, TxVersion}
import com.gicsports.transaction.lease.LeaseTransaction
import com.gicsports.transaction.transfer.*

class BalanceDiffValidationTest extends PropSpec with WithState {
  val ownLessThatLeaseOut: (GenesisTransaction, TransferTransaction, LeaseTransaction, LeaseTransaction, TransferTransaction) = {
    val master = TxHelpers.signer(1)
    val alice  = TxHelpers.signer(2)
    val bob    = TxHelpers.signer(3)
    val cooper = TxHelpers.signer(4)

    val fee                      = 400000
    val masterTransferAmount     = 1000.waves
    val aliceLeaseToBobAmount    = 500.waves
    val masterLeaseToAliceAmount = 750.waves

    val genesis                = TxHelpers.genesis(master.toAddress)
    val masterTransfersToAlice = TxHelpers.transfer(master, alice.toAddress, masterTransferAmount, fee = fee, version = TxVersion.V1)
    val aliceLeasesToBob       = TxHelpers.lease(alice, bob.toAddress, aliceLeaseToBobAmount)
    val masterLeasesToAlice    = TxHelpers.lease(master, alice.toAddress, masterLeaseToAliceAmount)
    val aliceTransfersMoreThanOwnsMinusLeaseOut =
      TxHelpers.transfer(alice, cooper.toAddress, masterTransferAmount - fee - aliceLeaseToBobAmount, fee = fee, version = TxVersion.V1)

    (genesis, masterTransfersToAlice, aliceLeasesToBob, masterLeasesToAlice, aliceTransfersMoreThanOwnsMinusLeaseOut)
  }

  property("can transfer more than own-leaseOut before allow-leased-balance-transfer-until") {
    val settings = TestFunctionalitySettings.Enabled.copy(blockVersion3AfterHeight = 4)

    val (genesis, masterTransfersToAlice, aliceLeasesToBob, masterLeasesToAlice, aliceTransfersMoreThanOwnsMinusLeaseOut) = ownLessThatLeaseOut
    assertDiffEi(
      Seq(TestBlock.create(Seq(genesis, masterTransfersToAlice, aliceLeasesToBob, masterLeasesToAlice))),
      TestBlock.create(Seq(aliceTransfersMoreThanOwnsMinusLeaseOut)),
      settings
    ) { totalDiffEi =>
      totalDiffEi.explicitGet()
    }
  }

  property("cannot transfer more than own-leaseOut after allow-leased-balance-transfer-until") {
    val settings = TestFunctionalitySettings.Enabled.copy(blockVersion3AfterHeight = 4)

    val (genesis, masterTransfersToAlice, aliceLeasesToBob, masterLeasesToAlice, aliceTransfersMoreThanOwnsMinusLeaseOut) = ownLessThatLeaseOut
    assertDiffEi(
      Seq(
        TestBlock.create(Seq(genesis)),
        TestBlock.create(Seq()),
        TestBlock.create(Seq()),
        TestBlock.create(Seq()),
        TestBlock.create(Seq(masterTransfersToAlice, aliceLeasesToBob, masterLeasesToAlice))
      ),
      TestBlock.create(Seq(aliceTransfersMoreThanOwnsMinusLeaseOut)),
      settings
    ) { totalDiffEi =>
      totalDiffEi should produce("trying to spend leased money")
    }
  }
}
