package com.creditletter.flows


import net.corda.core.flows.FinalityFlow
import net.corda.core.utilities.ProgressTracker.Step

object GETTING_NOTARY : Step("Retrieving notary identity.")
object GETTING_COUNTERPARTIES : Step("Retrieving counterparty identities.")
object GENERATING_TRANSACTION : Step("Generating transaction.")
object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
    override fun childProgressTracker() = FinalityFlow.tracker()
}
