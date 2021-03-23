package com.creditletter.api

import com.creditletter.LetterOfCreditDataStructures.Company
import com.creditletter.LetterOfCreditDataStructures.CreditType
import com.creditletter.LetterOfCreditDataStructures.Good
import com.creditletter.LetterOfCreditDataStructures.Location
import com.creditletter.LetterOfCreditDataStructures.Person
import com.creditletter.LetterOfCreditDataStructures.Port
import com.creditletter.LetterOfCreditDataStructures.PricedGood
import com.creditletter.LetterOfCreditDataStructures.Weight
import com.creditletter.LetterOfCreditDataStructures.WeightUnit
import com.creditletter.states.BillOfLadingProperties
import com.creditletter.states.LetterOfCreditApplicationProperties
import com.creditletter.states.PurchaseOrderProperties
import net.corda.core.contracts.Amount
import net.corda.finance.DOLLARS
import java.time.LocalDate
import java.time.Period
import java.util.*

/** These classes capture the JSON form data passed from the front-end. */

/**
 * The purchase order form data that is submitted by the front-end to create a
 * purchase order state.
 */
data class PurchaseOrderData(
        val purchaseOrderID: String,
        val sellerName: String,
        val sellerAddress: String,
        val buyerName: String,
        val buyerAddress: String,
        val purchaseOrderDate: String,
        val term: Int,
        val goodsDescription: String,
        val goodsPurchaseOrderRef: String,
        val goodsQuantity: Int,
        val goodsUnitPrice: Int,
        val goodsGrossWeight: Int) {

    /**
     * Converts the [PurchaseOrderData] submitted from the front-end into the
     * properties for an [PurchaseOrderState].
     */
    fun toPurchaseOrderProperties() = PurchaseOrderProperties(
            purchaseOrderID = purchaseOrderID,
            seller = Company(sellerName, sellerAddress, ""),
            buyer = Company(buyerName, buyerAddress, ""),
            purchaseOrderDate = LocalDate.parse(purchaseOrderDate.substringBefore('T')),
            term = term.toLong(),
            goods = listOf(PricedGood(goodsDescription, goodsPurchaseOrderRef, goodsQuantity,
                    goodsUnitPrice.DOLLARS, Weight(goodsGrossWeight.toDouble(),
                    WeightUnit.KG))))
}

/**
 * The letter-of-credit application form data that is submitted by the
 * front-end to create a letter-of-credit application state.
 */
data class LocApplicationData(
        val applicationId: String,
        val applicationDate: String,
        val typeCredit: String,
        val amount: String,
        val expiryDate: String,
        val portLoadingCountry: String,
        val portLoadingCity: String,
        val portLoadingAddress: String,
        val portDischargeCountry: String,
        val portDischargeCity: String,
        val portDischargeAddress: String,
        val goodsDescription: String,
        val goodsQuantity: Int,
        val goodsWeight: Int,
        val goodsWeightUnit: String,
        val goodsUnitPrice: Int,
        val goodsPurchaseOrderRef: String,
        val placePresentationCountry: String,
        val placePresentationState: String,
        val placePresentationCity: String,
        val lastShipmentDate: String,
        val periodPresentation: Int,
        val beneficiary: String,
        val issuer: String,
        val applicant: String,
        val advisingBank: String) {

    /**
     * Converts the [LocApplicationData] submitted from the front-end into the
     * properties for a [LetterOfCreditApplicationState].
     */
    fun toLocApplicationProperties(): LetterOfCreditApplicationProperties {
        return LetterOfCreditApplicationProperties(
                letterOfCreditApplicationID = applicationId,
                applicationDate = LocalDate.parse(applicationDate.substringBefore('T')),
                typeCredit = CreditType.valueOf(typeCredit),
                expiryDate = LocalDate.parse(expiryDate),
                portLoading = Port(portLoadingCountry, portLoadingCity, portLoadingAddress, null, null),
                portDischarge = Port(portDischargeCountry, portDischargeCity, portDischargeAddress, null, null),
                placePresentation = Location(placePresentationCountry, placePresentationState, placePresentationCity),
                lastShipmentDate = LocalDate.parse(lastShipmentDate.substringBefore('T')), // TODO does it make sense to include shipment date?
                periodPresentation = Period.ofDays(periodPresentation),
                descriptionGoods = listOf(PricedGood(
                        goodsDescription,
                        goodsPurchaseOrderRef,
                        goodsQuantity,
                        Amount(goodsUnitPrice.toLong(), Amount.parseCurrency(amount).token),
                        Weight(goodsWeight.toDouble(), WeightUnit.valueOf(goodsWeightUnit)))),
                documentsRequired = ArrayList(),
                amount = Amount.parseCurrency(amount))
    }
}

/**
 * The bill-of-lading form data that is submitted by the front-end to create a
 * bill-of-lading state.
 */
data class BillOfLadingData(
        val billOfLadingId: String,
        val issueDate: String,
        val carrierOwner: String,

        val nameOfVessel: String,
        val goodsDescription: String,
        val goodsQuantity: Int,
        val dateOfShipment: String,

        val portOfLoadingCountry: String,
        val portOfLoadingCity: String,
        val portOfLoadingAddress: String,

        val portOfDischargeCountry: String,
        val portOfDischargeCity: String,
        val portOfDischargeAddress: String,

        val shipper: String,
        val notifyName: String,
        val notifyAddress: String,
        val notifyPhone: String,

        val consigneeName: String,
        val consigneeAddress: String,
        val consigneePhone: String,

        val grossWeight: Int,
        val grossWeightUnit: String,

        val placeOfReceiptCountry: String,
        val placeOfReceiptCity: String,
        val buyer: String,
        val advisingBank: String,
        val issuingBank: String) {

    /**
     * Converts the [BillOfLadingData] submitted from the front-end into the
     * properties for a [BillOfLadingState].
     */
    fun toBillOfLadingProperties(): BillOfLadingProperties {
        return BillOfLadingProperties(
                billOfLadingId,
                LocalDate.parse(issueDate.substringBefore('T')),
                carrierOwner,
                nameOfVessel,
                listOf(Good(description = goodsDescription, quantity = goodsQuantity, grossWeight = null)),
                Port(country = portOfLoadingCountry, city = portOfLoadingCity, address = portOfLoadingAddress, state = null, name = null),
                Port(country = portOfDischargeCountry, city = portOfDischargeCity, address = portOfDischargeAddress, state = null, name = null),
                Weight(
                        grossWeight.toDouble(),
                        WeightUnit.valueOf(grossWeightUnit)),
                LocalDate.parse(dateOfShipment.substringBefore('T')),
                null,
                Person(
                        notifyName,
                        notifyAddress,
                        notifyPhone),
                Company(
                        consigneeName,
                        consigneeAddress,
                        consigneePhone),
                Location(
                        placeOfReceiptCountry,
                        null,
                        placeOfReceiptCity))
    }
}
