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

data class LetterOfCreditState(
        val beneficiary: Party,
        val advisingBank: Party,
        val issuingBank: Party,
        val applicant: Party,
        val status: LetterOfCreditStatus,
        val props: LetterOfCreditProperties) : LinearState {
    override val linearId = UniqueIdentifier(props.letterOfCreditID)
    override val participants = listOf(beneficiary, advisingBank, issuingBank, applicant)

    fun laded() = copy(status = LetterOfCreditStatus.LADED)
    fun shipped() = copy(status = LetterOfCreditStatus.SHIPPED)
    fun beneficiaryPaid() = copy(status = LetterOfCreditStatus.BENEFICIARY_PAID)
    fun advisoryPaid() = copy(status = LetterOfCreditStatus.ADVISORY_PAID)
    fun issuerPaid() = copy(status = LetterOfCreditStatus.ISSUER_PAID)
}

@CordaSerializable
data class LetterOfCreditProperties(
        val letterOfCreditID: String,
        val applicationDate: LocalDate,
        val issueDate: LocalDate,
        val typeCredit: CreditType,
        val amount: Amount<Currency>,
        val expiryDate: LocalDate,
        val portLoading: Port,
        val portDischarge: Port,
        val descriptionGoods: List<PricedGood>,
        val placePresentation: Location,
        val latestShip: LocalDate,
        val periodPresentation: Period) {

    constructor(applicationProps: LetterOfCreditApplicationProperties, issueDate: LocalDate) : this(
            letterOfCreditID = applicationProps.letterOfCreditApplicationID,
            applicationDate = applicationProps.applicationDate,
            issueDate = issueDate,
            typeCredit = applicationProps.typeCredit,
            amount = applicationProps.amount,
            expiryDate = applicationProps.expiryDate,
            portLoading = applicationProps.portLoading,
            portDischarge = applicationProps.portDischarge,
            descriptionGoods = applicationProps.descriptionGoods,
            placePresentation = applicationProps.placePresentation,
            latestShip = applicationProps.lastShipmentDate,
            periodPresentation = applicationProps.periodPresentation
    )
}

@CordaSerializable
enum class LetterOfCreditStatus {
    ISSUED,
    LADED,
    SHIPPED,
    BENEFICIARY_PAID,
    ADVISORY_PAID,
    ISSUER_PAID,
    TERMINATED
}
