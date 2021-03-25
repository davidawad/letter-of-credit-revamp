package com.creditletter.webserver


import com.google.gson.Gson
import com.google.gson.JsonObject
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier.Companion.fromString
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.toX500Name
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.DOLLARS
import net.corda.finance.workflows.getCashBalances
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.PublicKey
import java.util.*


/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    private val proxy = rpc.proxy
    private val me = proxy.nodeInfo().legalIdentities.first().name

    private val SERVICE_NODE_NAME = CordaX500Name("Notary Pool", "London", "GB")
    private val CENTRAL_BANK_NAME = CordaX500Name("Central Bank", "New York", "US")

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    fun X500Name.toDisplayString(): String = BCStyle.INSTANCE.toString(this)

    /** Helpers for filtering the network map cache. */
    private fun isNotary(nodeInfo: NodeInfo) = proxy.notaryIdentities().any { nodeInfo.isLegalIdentity(it) }
    private fun isMe(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == me
    private fun isNetworkMap(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.single().name.organisation == "Network Map Service"

    private fun getPartyFromNodeInfo(nodeInfo: NodeInfo): Party {
        return nodeInfo.legalIdentities[0]
    }

    /**
     * Returns the node's name.
     */
    @GetMapping(value = ["me"], produces = [APPLICATION_JSON_VALUE])
    fun whoami() = mapOf("me" to me.toString())


//    @RequestMapping(value = ["me"], method = [RequestMethod.GET])
//    fun _whoami() = mapOf("me" to myLegalName.organisation)


    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GetMapping(value = ["peers"], produces = [APPLICATION_JSON_VALUE])
    fun getPeers(): Map<String, List<String>> {
        return mapOf("peers" to proxy.networkMapSnapshot()
                .filter { isNotary(it).not() && isMe(it).not() && isNetworkMap(it).not() }
                .map { it.legalIdentities.first().name.toX500Name().toDisplayString() })
    }


    @RequestMapping(value = ["/node"], method = [RequestMethod.GET])
    private fun returnName(): ResponseEntity<String> {
        val resp = com.google.gson.JsonObject()
        resp.addProperty("name", me.toString())
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(resp.toString())
    }


    @RequestMapping(value = ["/loc/cash-balances"], method = [RequestMethod.POST])
    fun getCashBalances(@RequestBody payload: String?): ResponseEntity<String?>? {

        val req: JsonObject = Gson().fromJson(payload, JsonObject::class.java)

        val ret = JsonObject()

        val balance = proxy.getCashBalances()

        if (balance.isEmpty()) {
            mapOf(Pair(Currency.getInstance("USD"), 0.DOLLARS))

        } else {
            ret.addProperty("balance", balance.t)
        }


        ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(ret.toString())
    }

    @RequestMapping(value = ["/games/check"], method = [RequestMethod.POST])
    fun checkGame(@RequestBody payload: String?): ResponseEntity<String?>? {
        println(payload)
        val convertedObject: JsonObject = Gson().fromJson(payload, JsonObject::class.java)
        val gameId = fromString(convertedObject["gameId"].getAsString())
        // NOTE lowercase the name for easy retrieve
        val playerName = "\"" + convertedObject["name"].getAsString().toLowerCase().trim().toString() + "\""

        // optional param
        val sendEmail: Boolean = convertedObject["sendEmail"].getAsBoolean()
        val resp = JsonObject()


        return try {

            val output: SantaSessionState = proxy.startTrackedFlowDynamic(CheckAssignedSantaFlow::class.java, gameId).returnValue.get()
            if (output.getAssignments().get(playerName) == null) {
                resp.addProperty("target", "target not found in this game")
                return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(resp.toString())
            }
            val playerNames: List<String> = output.getPlayerNames()
            val playerEmails: List<String> = output.getPlayerEmails()
            val playerEmail = playerEmails[playerNames.indexOf(playerName)]
            val targetName: String = output.getAssignments().get(playerName).replace("\"", "")
            resp.addProperty("target", targetName)


            ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(resp.toString())

        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.message)
        }
    }


    /**
     * Displays all states of the given type that exist in the node's vault, along with the hashes and signatures of
     * the transaction that generated them.
     */
    private inline fun <reified T : ContractState> getAllStatesOfTypeWithHashesAndSigs(): Response {
        val states = proxy.vaultQueryBy<T>().states
        return mapStatesToHashesAndSigs(states)
    }

    /**
     * Displays all states of the given type that meet the filter condition and exist in the node's vault, along with
     * the hashes and signatures of the transaction that generated them.
     */
    private inline fun <reified T : ContractState> getFilteredStatesOfTypeWithHashesAndSigs(filter: (StateAndRef<T>) -> Boolean): Response {
        val states = proxy.vaultQueryBy<T>().states.filter(filter)
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

}
