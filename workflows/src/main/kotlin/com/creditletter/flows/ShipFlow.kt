package com.creditletter.flows

import co.paralleluniverse.fibers.Suspendable
import com.creditletter.contracts.LetterOfCreditContract
import com.creditletter.states.BillOfLadingState
import com.creditletter.states.LetterOfCreditState
import com.creditletter.states.LetterOfCreditStatus
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*


import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class ShipFlow(val locId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker(GETTING_NOTARY, GENERATING_TRANSACTION, VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION, FINALISING_TRANSACTION)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION
        val locStateAndRefs = serviceHub.vaultService.queryBy<LetterOfCreditState>().states.filter { it.state.data.props.letterOfCreditID == locId }
        if (locStateAndRefs.isEmpty()) throw Exception("Order could not be shipped. Letter of credit state with ID $locId not found.")
        if (locStateAndRefs.size > 1) throw Exception("Several unshipped letter of credit states with ID $locId found.")
        val locStateAndRef = locStateAndRefs.single()
        if (locStateAndRef.state.data.status != LetterOfCreditStatus.LADED) throw Exception("Order could not be shipped. It has already been shipped or terminated.")

        val bolStateCount = serviceHub.vaultService.queryBy<BillOfLadingState>().states.count { it.state.data.props.billOfLadingID == locId }
        if (bolStateCount == 0) throw Exception("Order could not be shipped. Bill of lading has not been created.")
        if (bolStateCount > 1) throw Exception("Several bill of lading states with ID $locId found.")

        val outputState = locStateAndRef.state.data.shipped()

        val builder = TransactionBuilder(notary = notary)
        builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

        builder.addInputState(locStateAndRef)
        builder.addOutputState(outputState, LetterOfCreditContract.CONTRACT_ID)
        builder.addCommand(LetterOfCreditContract.Commands.Ship(), listOf(ourIdentity.owningKey))

        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val stx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(stx))
    }
}

@InitiatedBy(ShipFlow::class)
class ShipFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {}
        }
        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
    }
}
