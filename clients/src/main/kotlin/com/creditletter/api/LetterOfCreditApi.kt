package com.creditletter.api


// import net.corda.finance.contracts.getCashBalances

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.DOLLARS
import java.security.PublicKey
import java.text.SimpleDateFormat
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST

//@Path("loc")
//class LetterOfCreditApi(val rpcOps: CordaRPCOps) {
//    private val me = rpcOps.nodeInfo().legalIdentities.first()
//    private val myLegalName = me.name
//    private val SERVICE_NODE_NAME = CordaX500Name("Notary Pool", "London", "GB")
//    private val CENTRAL_BANK_NAME = CordaX500Name("Central Bank", "New York", "US")
//
//    /**
//     * Returns the node's name.
//     */
//    @GET
//    @Path("me")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun whoami() = mapOf("me" to myLegalName.organisation)
//
//    /**
//     * Returns all parties registered with the network map.
//     */
//    @GET
//    @Path("peers")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun getPeers(): Map<String, List<CordaX500Name>> {
//        val nodeInfo = rpcOps.networkMapSnapshot()
//        return mapOf("peers" to nodeInfo
//                .map { it.legalIdentities.first().name }
//                .filter { it !in listOf(myLegalName, SERVICE_NODE_NAME, CENTRAL_BANK_NAME) })
//    }

/**
 * Get the node's current cash balances.
 */
@GET
@Path("cash-balances")
@Produces(MediaType.APPLICATION_JSON)
fun getCashBalances(): Map<Currency, Amount<Currency>> {
    val balance = rpcOps.getCashBalances()
    return if (balance.isEmpty()) {
        mapOf(Pair(Currency.getInstance("USD"), 0.DOLLARS))
    } else {
        balance
    }
}

/**
 * Get all states from the node's vault.
 */
@GET
@Path("vault")
@Produces(MediaType.APPLICATION_JSON)
fun getVault(): Pair<List<StateAndRef<ContractState>>, List<StateAndRef<ContractState>>> {
    val unconsumedStates = rpcOps.vaultQueryBy<ContractState>(QueryCriteria.VaultQueryCriteria()).states
    val consumedStates = rpcOps.vaultQueryBy<ContractState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)).states
    return Pair(unconsumedStates, consumedStates)
}

/**
 * Displays all purchase order states that exist in the node's vault.
 */
@GET
@Path("purchase-orders")
@Produces(MediaType.APPLICATION_JSON)
fun getPurchaseOrders() = getAllStatesOfTypeWithHashesAndSigs<PurchaseOrderState>()

/**
 * Displays all Letter-of-credit application states that exist in the node's vault.
 */
@GET
@Path("all-app")
@Produces(MediaType.APPLICATION_JSON)
fun getAllLocApplications() = getAllStatesOfTypeWithHashesAndSigs<LetterOfCreditApplicationState>()

/**
 * Displays all letter-of-credit states that exist in the node's vault.
 */
@GET
@Path("all")
@Produces(MediaType.APPLICATION_JSON)
fun getAllLettersOfCredit() = getAllStatesOfTypeWithHashesAndSigs<LetterOfCreditState>()

// TODO: Remove this.
/**
 * Displays all letter-of-credit application states awaiting confirmation that exist in the node's vault.
 */
@GET
@Path("awaiting-approval")
@Produces(MediaType.APPLICATION_JSON)
fun getAwaitingApprovalLettersOfCredit() = getAllStatesOfTypeWithHashesAndSigs<LetterOfCreditApplicationState>()

/**
 * Fetches the purchase order state that matches ref from the node's vault.
 */
@GET
@Path("get-purchase-order")
@Produces(MediaType.APPLICATION_JSON)
fun getPurchaseOrder(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
        { stateAndRef: StateAndRef<PurchaseOrderState> -> stateAndRef.state.data.props.purchaseOrderID == ref }
)

/**
 * Fetches LoC application state that matches ref from the node's vault.
 */
@GET
@Path("get-loc-app")
@Produces(MediaType.APPLICATION_JSON)
fun getLocApp(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
        { stateAndRef: StateAndRef<LetterOfCreditApplicationState> -> stateAndRef.state.data.props.letterOfCreditApplicationID == ref }
)

/**
 * Fetches letter-of-credit state that matches ref from the node's vault.
 */
@GET
@Path("get-loc")
@Produces(MediaType.APPLICATION_JSON)
fun getLetterOfCredit(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
        { stateAndRef: StateAndRef<LetterOfCreditState> -> stateAndRef.state.data.props.letterOfCreditID == ref }
)

/**
 * Fetches bill of lading state that matches ref from the node's vault.
 */
@GET
@Path("get-bol")
@Produces(MediaType.APPLICATION_JSON)
fun getBillOfLading(@QueryParam(value = "ref") ref: String) = getStateOfTypeWithHashAndSigs(ref,
        { stateAndRef: StateAndRef<BillOfLadingState> -> stateAndRef.state.data.props.billOfLadingID == ref }
)

/**
 * Fetches events concerning bill of lading state that matches ref.
 */
@GET
@Path("get-bol-events")
@Produces(MediaType.APPLICATION_JSON)
fun getBolEvents(@QueryParam(value = "ref") ref: String): List<Pair<Party, String>> {
    val formatter = SimpleDateFormat("dd MM yyyy HH:mm:ss")

    val priorStates = rpcOps.vaultQueryBy<BillOfLadingState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED)).states.filter { it.state.data.props.billOfLadingID == ref }.map {
        Pair(it.state.data.owner, formatter.format(Date.from(it.state.data.timestamp)))
    }
    val currentState = rpcOps.vaultQueryBy<BillOfLadingState>(QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)).states.filter { it.state.data.props.billOfLadingID == ref }.map {
        Pair(it.state.data.owner, formatter.format(Date.from(it.state.data.timestamp)))
    }
    return priorStates.union(currentState).toList()
}

@POST
@Path("create-trade")
fun createTrade(purchaseOrder: PurchaseOrderData): Response {
    val flowFuture = rpcOps.startFlow(::CreatePurchaseOrderFlow, purchaseOrder.sellerName, purchaseOrder.toPurchaseOrderProperties()).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@POST
@Path("apply-for-loc")
fun applyForLoc(loc: LocApplicationData): Response {
    val flowFuture = rpcOps.startFlow(::ApplyForLoCFlow, loc.beneficiary, loc.issuer, loc.advisingBank, loc.toLocApplicationProperties()).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@GET
@Path("approve-loc")
fun approveLetterOfCreditApplication(@QueryParam(value = "ref") ref: String): Response {
    val flowFuture = rpcOps.startFlow(::ApproveLoCFlow, ref).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@POST
@Path("submit-bol")
fun submitBol(billOfLading: BillOfLadingData): Response {
    val flowFuture = rpcOps.startFlow(::CreateBoLFlow, billOfLading.buyer, billOfLading.advisingBank, billOfLading.issuingBank, billOfLading.toBillOfLadingProperties()).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@GET
@Path("ship")
fun ship(@QueryParam(value = "ref") ref: String): Response {
    val flowFuture = rpcOps.startFlow(::ShipFlow, ref).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@GET
@Path("pay-seller")
fun paySeller(@QueryParam(value = "locId") locId: String): Response {
    val flowFuture = rpcOps.startFlow(::SellerPaymentFlow, locId).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@GET
@Path("pay-adviser")
fun payAdviser(@QueryParam(value = "locId") locId: String): Response {
    val flowFuture = rpcOps.startFlow(::AdvisoryPaymentFlow, locId).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

@GET
@Path("pay-issuer")
fun payIssuer(@QueryParam(value = "locId") locId: String): Response {
    val flowFuture = rpcOps.startFlow(::IssuerPaymentFlow, locId).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.accepted().entity("Transaction id ${result.tx.id} committed to ledger.").build()
}

/**
 * Fetches all transactions and returns them as a list of <ID, input types, output types> triples.
 */
@GET
@Path("transactions")
fun transactions(): Response {
    val flowFuture = rpcOps.startFlow(::GetTransactionsFlow).returnValue
    val result = try {
        flowFuture.getOrThrow()
    } catch (e: Exception) {
        return Response.status(BAD_REQUEST).entity(e.message).build()
    }

    return Response.ok(result, MediaType.APPLICATION_JSON).build()
}

/**
 * Displays all states of the given type that exist in the node's vault, along with the hashes and signatures of
 * the transaction that generated them.
 */
private inline fun <reified T : ContractState> getAllStatesOfTypeWithHashesAndSigs(): Response {
    val states = rpcOps.vaultQueryBy<T>().states
    return mapStatesToHashesAndSigs(states)
}

/**
 * Displays all states of the given type that meet the filter condition and exist in the node's vault, along with
 * the hashes and signatures of the transaction that generated them.
 */
private inline fun <reified T : ContractState> getFilteredStatesOfTypeWithHashesAndSigs(filter: (StateAndRef<T>) -> Boolean): Response {
    val states = rpcOps.vaultQueryBy<T>().states.filter(filter)
    return mapStatesToHashesAndSigs(states)
}

/**
 * Maps the states to the hashes and signatures of the transaction that generated them.
 */
private fun mapStatesToHashesAndSigs(stateAndRefs: List<StateAndRef<ContractState>>): Response {
    val transactions = rpcOps.internalVerifiedTransactionsSnapshot()
    val transactionMap = transactions.map { tx -> tx.id to tx }.toMap()
    val parties = rpcOps.networkMapSnapshot().map { nodeInfo -> nodeInfo.legalIdentities.first() }
    val partyMap = parties.map { party -> party.owningKey to party }.toMap()

    val response = try {
        stateAndRefs.map { stateAndRef -> processTransaction(stateAndRef, transactionMap, partyMap) }
    } catch (e: IllegalArgumentException) {
        return Response.status(BAD_REQUEST).entity("State in vault has no corresponding transaction.").build()
    }

    return Response.ok(response, MediaType.APPLICATION_JSON).build()
}

/**
 * Fetches the state of the given type that meets the filter condition from the node's vault, along with the hash
 * and signatures of the transaction that generated it.
 */
private inline fun <reified T : ContractState> getStateOfTypeWithHashAndSigs(ref: String, filter: (StateAndRef<T>) -> Boolean): Response {
    val states = rpcOps.vaultQueryBy<T>().states
    val transactions = rpcOps.internalVerifiedTransactionsSnapshot()
    val transactionMap = transactions.map { tx -> tx.id to tx }.toMap()
    val parties = rpcOps.networkMapSnapshot().map { nodeInfo -> nodeInfo.legalIdentities.first() }
    val partyMap = parties.map { party -> party.owningKey to party }.toMap()

    val stateAndRef = states.find(filter)
            ?: return Response.status(BAD_REQUEST).entity("State with ID $ref not found.").build()

    val response = try {
        processTransaction(stateAndRef, transactionMap, partyMap)
    } catch (e: IllegalArgumentException) {
        return Response.status(BAD_REQUEST).entity("State in vault has no corresponding transaction.").build()
    }

    return Response.ok(response, MediaType.APPLICATION_JSON).build()
}

private fun processTransaction(stateAndRef: StateAndRef<*>, transactionMap: Map<SecureHash, SignedTransaction>, partyMap: Map<PublicKey, Party>): TxSummary {
    val state = stateAndRef.state.data

    val txId = stateAndRef.ref.txhash.toString()
    val tx = transactionMap.getOrDefault(stateAndRef.ref.txhash, null)

    // A race-condition could have meant the transaction was not found.
    return if (tx != null) {
        // We fail gracefully if the party could not be mapped.
        val sigsAndSigners = tx.sigs.map { sig -> sig.bytes to partyMap.getOrDefault(sig.by, null) }
        val signatures = sigsAndSigners.map { it.first }
        val signers = sigsAndSigners.map { it.second }
        TxSummary(txId, signatures, state, signers)
    } else {
        TxSummary("", listOf(), null, listOf())
    }
}
}

data class TxSummary(val first: String, val second: List<ByteArray>, val third: ContractState?, val fourth: List<Party?>)
