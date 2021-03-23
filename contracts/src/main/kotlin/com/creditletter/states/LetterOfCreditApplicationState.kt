package com.creditletter.states

import com.creditletter.LetterOfCreditDataStructures.CreditType
import com.creditletter.LetterOfCreditDataStructures.Location
import com.creditletter.LetterOfCreditDataStructures.Port
import com.creditletter.LetterOfCreditDataStructures.PricedGood

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.time.Period
import java.util.*

data class LetterOfCreditApplicationState(
        val applicant: Party,
        val issuer: Party,
        val beneficiary: Party,
        val advisingBank: Party,
        val props: LetterOfCreditApplicationProperties) : LinearState {
    override val participants = listOf(applicant, issuer)
    override val linearId = UniqueIdentifier(props.letterOfCreditApplicationID)
}

@CordaSerializable
data class LetterOfCreditApplicationProperties(
        val letterOfCreditApplicationID: String,
        val applicationDate: LocalDate,
        val typeCredit: CreditType,
        val expiryDate: LocalDate,
        val portLoading: Port,
        val portDischarge: Port,
        val placePresentation: Location,
        val lastShipmentDate: LocalDate,
        val periodPresentation: Period,
        val descriptionGoods: List<PricedGood> = ArrayList(),
        val documentsRequired: List<String> = ArrayList(),
        val amount: Amount<Currency>)
