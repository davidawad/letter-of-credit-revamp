package com.creditletter.contracts

import com.creditletter.states.LetterOfCreditApplicationState
import com.creditletter.states.LetterOfCreditState
import com.creditletter.states.LetterOfCreditStatus
import com.creditletter.states.PurchaseOrderState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash

open class LetterOfCreditContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_ID = "eloc.contract.LetterOfCreditContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class AddBillOfLading : TypeOnlyCommandData(), Commands
        class Ship : TypeOnlyCommandData(), Commands
        class PaySeller : TypeOnlyCommandData(), Commands
        class PayAdvisingBank : TypeOnlyCommandData(), Commands
        class PayIssuer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Issue -> {
                requireThat {
                    val purchaseOrder = tx.inputsOfType<PurchaseOrderState>().single()
                    val output = tx.outputsOfType<LetterOfCreditState>().single()
                    // confirms the LetterOfCreditApplication is included in the transaction
                    tx.inputsOfType<LetterOfCreditApplicationState>().single()
                    "input purchaseOrder should not be consumable" using (!purchaseOrder.consumable)
                    //"the transaction is not signed by the advising bank" by (command.signers.contains(output.props.advisingBank.owningKey))
                    "the LOC must be Issued" using (output.status == LetterOfCreditStatus.ISSUED)
                    "the period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddBillOfLading -> requireThat {
                // TODO: Add checking here.
            }

            is Commands.Ship -> requireThat {
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                "The transaction is signed by the seller" using (command.signers.contains(output.beneficiary.owningKey))
                "The LOC must be Issued" using (output.status == LetterOfCreditStatus.SHIPPED)
            }

            is Commands.PaySeller -> requireThat {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                "Cash is part of the output state." using (tx.outputsOfType<Cash.State>().any())
                "Seller must not already have been paid." using (input.status == LetterOfCreditStatus.SHIPPED)
                "Transaction must update ledger to mark seller as paid." using (output.status == LetterOfCreditStatus.BENEFICIARY_PAID)
                "The period of presentation must be a positive number." using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
            }

            is Commands.PayAdvisingBank -> requireThat {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                "Cash is part of the output state." using (tx.outputsOfType<Cash.State>().any())
                "Advising bank must not already have been paid." using (input.status == LetterOfCreditStatus.BENEFICIARY_PAID)
                "Advising bank is marked as being paid in output state." using (output.status == LetterOfCreditStatus.ADVISORY_PAID)
                "The period of presentation must be a positive number." using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
            }

            is Commands.PayIssuer -> requireThat {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                "Issuing bank must not already have been paid" using (input.status == LetterOfCreditStatus.ADVISORY_PAID)
                "Issuing bank is marked as being paid in output state" using (output.status == LetterOfCreditStatus.ISSUER_PAID)
                "The period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
            }
        }
    }
}
