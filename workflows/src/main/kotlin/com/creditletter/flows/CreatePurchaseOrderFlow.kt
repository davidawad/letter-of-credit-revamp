package com.creditletter.flows


import co.paralleluniverse.fibers.Suspendable
import com.creditletter.contracts.PurchaseOrderContract
import com.creditletter.states.PurchaseOrderProperties
import com.creditletter.states.PurchaseOrderState

import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class CreatePurchaseOrderFlow(val sellerName: String, val purchaseOrderProperties: PurchaseOrderProperties) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker = ProgressTracker(GETTING_NOTARY, GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION, SIGNING_TRANSACTION, FINALISING_TRANSACTION)

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = GENERATING_TRANSACTION
        val seller = serviceHub.identityService.partiesFromName(sellerName, false).singleOrNull()
                ?: throw IllegalArgumentException("No exact match found for seller name $sellerName.")
        val purchaseOrder = PurchaseOrderState(ourIdentity, seller, true, purchaseOrderProperties)
        val issueCommand = Command(PurchaseOrderContract.Commands.Issue(), listOf(ourIdentity.owningKey))

        val builder = TransactionBuilder(notary)
                .addOutputState(purchaseOrder, PurchaseOrderContract.CONTRACT_ID)
                .addCommand(issueCommand)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        builder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val stx = serviceHub.signInitialTransaction(builder)

        progressTracker.currentStep = FINALISING_TRANSACTION


        val targetSession = initiateFlow(seller)

//        return subFlow(FinalityFlow(stx, targetSession))

        return subFlow(FinalityFlow(stx))
    }
}
