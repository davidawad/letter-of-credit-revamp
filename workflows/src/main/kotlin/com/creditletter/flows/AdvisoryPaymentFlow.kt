package com.creditletter.flows

import co.paralleluniverse.fibers.Suspendable
import com.creditletter.contracts.BillOfLadingContract
import com.creditletter.contracts.LetterOfCreditContract
import com.creditletter.states.BillOfLadingState
import com.creditletter.states.LetterOfCreditState
import com.creditletter.states.LetterOfCreditStatus
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.workflows.asset.CashUtils
import java.time.Duration
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class AdvisoryPaymentFlow(val locId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker(GETTING_NOTARY, GENERATING_TRANSACTION, VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION, FINALISING_TRANSACTION)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION
        val locStates = serviceHub.vaultService.queryBy<LetterOfCreditState>().states.filter {
            it.state.data.status != LetterOfCreditStatus.TERMINATED && it.state.data.props.letterOfCreditID == locId
        }
        if (locStates.isEmpty()) throw Exception("Advising bank could not be paid. Letter of credit state with ID $locId not found.")
        if (locStates.size > 1) throw Exception("Several letter of credit states with ID $locId found.")
        val locState = locStates.single()

        val bolStates = serviceHub.vaultService.queryBy<BillOfLadingState>().states.filter {
            it.state.data.props.billOfLadingID == locId
        }
        if (bolStates.isEmpty()) throw Exception("Advising bank could not be paid. Bill of lading has not been created.")
        if (bolStates.size > 1) throw Exception("Several bill of lading states with ID $locId found.")
        val bolState = bolStates.single()

        val payee = locState.state.data.advisingBank

        val outputState = locState.state.data.advisoryPaid()
        val outputStateBol = bolState.state.data.copy(owner = ourIdentity, timestamp = Instant.now())

        val builder = TransactionBuilder(notary = notary)
        builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

        // val (_, signingKeys) = Cash.generateSpend(serviceHub, builder, locState.state.data.props.amount, payee)
        val (_, signingKeys) = CashUtils.generateSpend(serviceHub, builder, locState.state.data.props.amount, ourIdentityAndCert, payee)

        builder.addInputState(locState)
        builder.addInputState(bolState)
        builder.addOutputState(outputState, LetterOfCreditContract.CONTRACT_ID)
        builder.addOutputState(outputStateBol, BillOfLadingContract.CONTRACT_ID)
        builder.addCommand(LetterOfCreditContract.Commands.PayAdvisingBank(), listOf(ourIdentity.owningKey))
        builder.addCommand(BillOfLadingContract.Commands.Transfer(), ourIdentity.owningKey)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val stx = serviceHub.signInitialTransaction(builder, signingKeys + ourIdentity.owningKey)

        progressTracker.currentStep = FINALISING_TRANSACTION

        return subFlow(FinalityFlow(stx))
    }
}
