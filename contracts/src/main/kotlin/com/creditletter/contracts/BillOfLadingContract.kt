package com.creditletter.contracts

import com.creditletter.states.BillOfLadingState
import com.creditletter.states.LetterOfCreditState
import com.creditletter.states.LetterOfCreditStatus
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * A bill of lading is a standard-form document. It is transferable by endorsement (or by lawful transfer of possession)
 * and is a receipt from shipping company regarding the number of packages with a particular weight and markings and a
 * contract for the transportation of same to a port of destination mentioned therein.
 *
 * An order bill of lading is used when shipping merchandise prior to loc, requiring a carrier to deliver the
 * merchandise to the importer, and at the endorsement of the exporter the carrier may transfer title to the importer.
 * Endorsed order bills of lading can be traded as a security or serve as collateral against debt obligations.
 */
class BillOfLadingContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "com.creditletter.contracts.BillOfLadingContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val inputStates = tx.inputsOfType<ContractState>()
        val inputBillsOfLading = tx.inputsOfType<BillOfLadingState>()
        val inputLettersOfCredit = tx.inputsOfType<LetterOfCreditState>()
        val outputStates = tx.outputsOfType<ContractState>()
        val outputBillsOfLading = tx.outputsOfType<BillOfLadingState>()
        val outputLettersOfCredit = tx.outputsOfType<LetterOfCreditState>()

        when (command.value) {
            is Commands.Issue -> requireThat {
                "There are one input state." using (inputStates.size == 1)
                "The input state is a letter of credit" using (inputLettersOfCredit.size == 1)
                "There are two output states" using (outputStates.size == 2)
                "One output state is the bill of lading" using (outputBillsOfLading.size == 1)
                "The other output state is a letter of credit" using (outputLettersOfCredit.size == 1)

                val billOfLading = outputBillsOfLading.single()
                "The owner of the bill of lading is the beneficiary" using
                        (billOfLading.owner == billOfLading.seller)
                val inputLetterOfCredit = inputLettersOfCredit.single()
                "The input letter of credit has a status of ISSUED" using
                        (inputLetterOfCredit.status == LetterOfCreditStatus.ISSUED)
                val outputLetterOfCredit = outputLettersOfCredit.single()
                "The output letter of credit has a status of LADED" using
                        (outputLetterOfCredit.status == LetterOfCreditStatus.LADED)
                "The letter of credit is unchanged apart from the status field" using
                        (inputLetterOfCredit.copy(status = outputLetterOfCredit.status) == outputLetterOfCredit)

                "The owner of the bill of lading is a required signer" using
                        (billOfLading.owner.owningKey in command.signers)
                // TODO: Constraints on letter-of-credit signers.
            }

            is Commands.Transfer -> requireThat {
                "One input state is a bill of lading" using (inputBillsOfLading.size == 1)
                "One input state is a letter of credit" using (inputLettersOfCredit.size == 1)
                val inputBillOfLading = inputBillsOfLading.single()
                "One output state is a bill of lading" using (outputBillsOfLading.size == 1)
                "One output state is a letter of credit" using (outputLettersOfCredit.size == 1)
                val outputBillOfLading = outputBillsOfLading.single()

                "The owner of the bill of lading has changed" using
                        (inputBillOfLading.owner != outputBillOfLading.owner)
                "the bill of lading agreement properties are unchanged" using
                        (inputBillOfLading.props == outputBillOfLading.props)

                // TODO: Re-add once additional signing flows have been implemented.
                //  "The owner of the input bill of lading is a required signer" using
                //          (inputBillOfLading.owner.owningKey in command.signers)

                // TODO: Constants around the input/output letter-of-credit state.
                // TODO: Constraints around the included cash.
            }
        }
    }
}
