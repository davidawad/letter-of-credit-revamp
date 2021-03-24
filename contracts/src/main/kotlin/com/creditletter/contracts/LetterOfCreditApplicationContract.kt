package com.creditletter.contracts

import com.creditletter.states.LetterOfCreditApplicationState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * Letter of Credit Application
 */
class LetterOfCreditApplicationContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "com.creditletter.contracts.LetterOfCreditApplicationContract"
    }

    interface Commands : CommandData {
        class Apply : TypeOnlyCommandData(), Commands
        class Approve : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Apply -> requireThat {
                val output = tx.outputsOfType<LetterOfCreditApplicationState>().single()
                "there is one input state" using (tx.inputStates.size == 1)
                // TODO: Additional checks around the input purchase order.
                // TODO: Additional checks around total number of inputs/outputs.
            }
            is Commands.Approve -> {
                // TODO: Add approval logic.
            }
        }
    }
}
