package com.creditletter.contracts


import com.creditletter.states.LetterOfCreditApplicationState
import com.creditletter.states.PurchaseOrderState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant
import java.time.ZoneOffset

/**
 * An purchase order is a document that describes a trade between a buyer and a seller. It is issued on a particular
 * date, it lists goods being sold by the seller, the cost of each good and the total amount owed by the buyer and when
 * the seller expects to be paid by.
 *
 * In the trade finance world, purchase orders are used to create other contracts (for example AccountsReceivable),
 * newly created purchase orders start off with a status of "unassigned", once they're used to create other contracts
 * the status is changed to "assigned". This ensures that an purchase order is used only once when creating a financial
 * product like AccountsReceivable.
 */
class PurchaseOrderContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "com.creditletter.contracts.PurchaseOrderContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class LockPurchaseOrder : TypeOnlyCommandData(), Commands
        class Extinguish : TypeOnlyCommandData(), Commands
    }

    /** The Purchase Order contract needs to handle one command
     * 1: Issue -- the creation of the Purchase Order contract. We need to confirm that the correct
     *             party signed the contract and that the relevant fields are populated with valid data.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val time = Instant.now()

        when (command.value) {

            is Commands.Issue -> {
                if (tx.outputs.size != 1) {
                    throw IllegalArgumentException("Failed requirement: during issuance of the purchase order, only " +
                            "one output purchase order state should be include in the transaction. " +
                            "Number of output states included was " + tx.outputs.size)
                }
                val issueOutput = tx.outputsOfType<PurchaseOrderState>().single()

                requireThat {
                    "there is no input state" using tx.inputsOfType<PurchaseOrderState>().isEmpty()
                    "the transaction is signed by the purchase order owner" using (command.signers.contains(issueOutput.owner.owningKey))
                    "the seller and seller must be different" using (issueOutput.props.buyer.name != issueOutput.props.seller.name)
                    "the purchase order ID must not be blank" using (issueOutput.props.purchaseOrderID.isNotEmpty())
                    "the term must be a positive number" using (issueOutput.props.term > 0)
                    "the loc date must be in the future" using (issueOutput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                            > time)
                    "there must be goods associated with the purchase order" using (issueOutput.props.goods.isNotEmpty())
                }
            }

            is Commands.LockPurchaseOrder -> {
                if (tx.outputs.size != 2) {
                    throw IllegalArgumentException("Failed requirement: during Loc Request")
                }
                val purchaseOrderInput = tx.inputsOfType<PurchaseOrderState>().single()
                val purchaseOrderOutput = tx.outputsOfType<PurchaseOrderState>().single()

                requireThat {
                    "input purchase order must be marked as consumable" using (purchaseOrderInput.consumable)
                    "purchase order in the output should be marked as non-consumable" using (!purchaseOrderOutput.consumable)

                }
            }

            is Commands.Extinguish -> {
                val locAppInput: LetterOfCreditApplicationState = tx.inputsOfType<LetterOfCreditApplicationState>().single()
                requireThat {
                    "Issuing bank should terminate the purchase order State" using (command.signers.contains(locAppInput.issuer.owningKey))
                }

            }
        }
    }
}
