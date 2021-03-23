package com.creditletter.flows

import co.paralleluniverse.fibers.Suspendable
import com.creditletter.contracts.LetterOfCreditApplicationContract
import com.creditletter.contracts.LetterOfCreditContract
import com.creditletter.contracts.PurchaseOrderContract
import com.creditletter.states.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import java.time.LocalDate
import java.util.*

@InitiatingFlow
@StartableByRPC
class ApproveLoCFlow(val reference: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker(GETTING_NOTARY, GENERATING_TRANSACTION, VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION, FINALISING_TRANSACTION)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION

        val pocCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(reference)),
                status = Vault.StateStatus.UNCONSUMED)

        val locCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(reference)),
                status = Vault.StateStatus.UNCONSUMED)

//        val applicationStateAndRef = serviceHub.vaultService.queryBy<LetterOfCreditApplicationState>().states.find {
//            it.state.data.props.letterOfCreditApplicationID == reference
//        } ?: throw IllegalArgumentException("No letter-of-credit application with ID $reference found.")

        val applicationStateAndRef = serviceHub.vaultService.queryBy(
                contractStateType = LetterOfCreditApplicationState::class.java
        ).states.filter { it.state.data.props.letterOfCreditApplicationID == reference }


//        val purchaseOrderStateAndRef = serviceHub.vaultService.queryBy<P  urchaseOrderState>().states.find {
//            it.state.data.props.purchaseOrderID == reference
//        } ?: throw IllegalArgumentException("No purchase order with ID $reference found.")

        val purchaseOrderStateAndRef = serviceHub.vaultService.queryBy(
                contractStateType = PurchaseOrderState::class.java
        ).states.filter { it.state.data.props.purchaseOrderID == reference }


        val application = applicationStateAndRef.get(0).state.data


        val locProps = LetterOfCreditProperties(application.props, LocalDate.now())

        val loc = LetterOfCreditState(
                application.beneficiary,
                application.advisingBank,
                application.issuer,
                application.applicant,
                status = LetterOfCreditStatus.ISSUED,
                props = locProps)

        val builder = TransactionBuilder(notary = notary)
                .addInputState(applicationStateAndRef[0])
                .addInputState(purchaseOrderStateAndRef[0])
                .addOutputState(loc, LetterOfCreditContract.CONTRACT_ID)
                .addCommand(LetterOfCreditApplicationContract.Commands.Approve(), ourIdentity.owningKey)
                .addCommand(LetterOfCreditContract.Commands.Issue(), ourIdentity.owningKey)
                .addCommand(PurchaseOrderContract.Commands.Extinguish(), ourIdentity.owningKey)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val currentTime = serviceHub.clock.instant()
        builder.setTimeWindow(currentTime, 30.seconds)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val stx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = FINALISING_TRANSACTION

        // assume we can simply approve it ourselves
        val targetSession = initiateFlow(ourIdentity)

//        return subFlow(FinalityFlow(stx, targetSession))

        return subFlow(FinalityFlow(stx))
    }
}
