package com.creditletter.flows

import co.paralleluniverse.fibers.Suspendable
import com.creditletter.contracts.LetterOfCreditApplicationContract
import com.creditletter.contracts.PurchaseOrderContract
import com.creditletter.states.LetterOfCreditApplicationProperties
import com.creditletter.states.LetterOfCreditApplicationState
import com.creditletter.states.PurchaseOrderState
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class ApplyForLoCFlow(val beneficiaryName: String, val issuingBankName: String, val advisingBankName: String,
                      val applicationProperties: LetterOfCreditApplicationProperties) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker(GETTING_NOTARY, GETTING_COUNTERPARTIES, GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION, SIGNING_TRANSACTION, FINALISING_TRANSACTION)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GETTING_COUNTERPARTIES
        val beneficiary = serviceHub.identityService.partiesFromName(beneficiaryName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for beneficiary name $beneficiaryName.")
        val issuingBank = serviceHub.identityService.partiesFromName(issuingBankName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for issuing bank name $issuingBankName.")
        val advisingBank = serviceHub.identityService.partiesFromName(advisingBankName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for advising bank name $advisingBankName.")

        progressTracker.currentStep = GENERATING_TRANSACTION
        val application = LetterOfCreditApplicationState(
                applicant = ourIdentity,
                beneficiary = beneficiary,
                issuer = issuingBank,
                advisingBank = advisingBank,
                props = applicationProperties)

        val builder = TransactionBuilder(notary)
        builder.setTimeWindow(Instant.now(), Duration.ofSeconds(60))

        val issueCommand = Command(LetterOfCreditApplicationContract.Commands.Apply(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))
        val purchaseOrderCommand = Command(PurchaseOrderContract.Commands.LockPurchaseOrder(), listOf(serviceHub.myInfo.legalIdentities.first().owningKey))

        // TODO: Can change this to querying using a schema.

        // val purchaseOrders = serviceHub.vaultService.queryBy<PurchaseOrderState>().states

        val purchaseOrders = serviceHub.vaultService.queryBy(
                contractStateType = PurchaseOrderState::class.java
        ).states

        val purchaseOrderStateAndRef = purchaseOrders.find { stateAndRef -> stateAndRef.state.data.props.purchaseOrderID == application.props.letterOfCreditApplicationID }
                ?: throw IllegalArgumentException("No purchase order with ID ${application.props.letterOfCreditApplicationID} found.")
        builder.addInputState(purchaseOrderStateAndRef)

        val outputPurchaseOrderState = purchaseOrderStateAndRef.state.data.copy(participants = purchaseOrderStateAndRef.state.data.participants + application.issuer, consumable = false)
        builder.addOutputState(outputPurchaseOrderState, PurchaseOrderContract.CONTRACT_ID)
        builder.addCommand(purchaseOrderCommand)

        val state = LetterOfCreditApplicationState(application.applicant, application.issuer, application.beneficiary, application.advisingBank, application.props)
        builder.addOutputState(state, LetterOfCreditApplicationContract.CONTRACT_ID)
        builder.addCommand(issueCommand)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val stx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = FINALISING_TRANSACTION

        // assume we can simply approve it ourselves
        val targetSession = initiateFlow(application.applicant)

//        return subFlow(FinalityFlow(stx, targetSession))
        return subFlow(FinalityFlow(stx))
    }
}
