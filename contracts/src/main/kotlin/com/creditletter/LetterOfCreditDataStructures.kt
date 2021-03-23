package com.creditletter

import net.corda.core.contracts.Amount
import net.corda.core.serialization.CordaSerializable
import java.util.*

object LetterOfCreditDataStructures {

    @CordaSerializable
    enum class WeightUnit { G, KG, LBS }

    @CordaSerializable
    data class Weight(val quantity: Double, val unit: WeightUnit)

    @CordaSerializable
    data class Company(val name: String, val address: String, val phone: String?)

    @CordaSerializable
    data class Person(val name: String, val address: String, val phone: String?)

    @CordaSerializable
    data class Port(val country: String, val city: String, val address: String?, val name: String?, val state: String?)

    @CordaSerializable
    data class Location(val country: String, val state: String?, val city: String)

    @CordaSerializable
    data class Good(val description: String, val quantity: Int, val grossWeight: Weight?) {
        init {
            require(quantity > 0) { "The good quantity must be a positive value." }
        }
    }

    @CordaSerializable
    data class PricedGood(
            val description: String,
            val purchaseOrderRef: String?,
            val quantity: Int,
            val unitPrice: Amount<Currency>,
            val grossWeight: Weight?) {
        init {
            require(quantity > 0) { "The good quantity must be a positive value." }
        }
    }

    @CordaSerializable
    enum class CreditType {
        SIGHT,
        DEFERRED_PAYMENT,
        ACCEPTANCE,
        NEGOTIABLE_CREDIT,
        TRANSFERABLE,
        STANDBY,
        REVOLVING,
        RED_CLAUSE,
        GREEN_CLAUSE
    }
}
