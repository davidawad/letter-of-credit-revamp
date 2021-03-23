package com.creditletter.flows


import co.paralleluniverse.fibers.Suspendable
import com.creditletter.states.BillOfLadingState
import com.creditletter.states.LetterOfCreditApplicationState
import com.creditletter.states.LetterOfCreditState
import com.creditletter.states.PurchaseOrderState

//import com.wildfire.state.PledgeState

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.contracts.asset.Cash

/**
 * Gets a summary of all the transactions on the node.
 */
@CordaSerializable
@StartableByRPC
class GetTransactionsFlow : FlowLogic<List<TransactionSummary>>() {
    @Suspendable
    override fun call(): List<TransactionSummary> {
        val signedTransactions = serviceHub.validatedTransactions.track().snapshot
        val ledgerTransactions = signedTransactions.map { signedTx -> signedTx.toLedgerTransaction(serviceHub) }
        return ledgerTransactions.map { ledgerTx ->
            val inputStateTypes = ledgerTx.inputStates.map { inputState -> mapToStateSubclass(inputState) }
            val outputStateTypes = ledgerTx.outputStates.map { outputState -> mapToStateSubclass(outputState) }

            // deprecated
            // val signers = ledgerTx.commands.flatMap { it.signingParties }
            val signers = ledgerTx.commands.flatMap { it.signers }.map { serviceHub.identityService.partyFromKey(it) }
            val signersAndNotary = signers + ledgerTx.notary!!
            val signerNames = signersAndNotary.map { it?.name?.organisation }.toSet()

            TransactionSummary(ledgerTx.id, inputStateTypes, outputStateTypes, signerNames as Set<String>)
        }
    }

    private fun mapToStateSubclass(state: ContractState) = when (state) {
        // is PledgeState -> "Collateral Pledge"

        is PurchaseOrderState -> {
            val status = if (state.consumable) "UNLOCKED" else "LOCKED"
            "Purchase Order ($status)"
        }

        is LetterOfCreditApplicationState -> "Letter Of Credit Application"
        is LetterOfCreditState -> "Letter Of Credit (${state.status})"
        is BillOfLadingState -> "Bill Of Lading"
        is Cash.State -> "Cash"
        else -> "ContractState"
    }
}

@CordaSerializable
data class TransactionSummary(val hash: SecureHash, val inputs: List<String>, val outputs: List<String>, val signers: Set<String>)
