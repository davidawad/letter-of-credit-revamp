package com.creditletter.flows

import com.creditletter.LetterOfCreditDataStructures
import com.creditletter.LetterOfCreditDataStructures.Company
import com.creditletter.LetterOfCreditDataStructures.Good
import com.creditletter.LetterOfCreditDataStructures.Location
import com.creditletter.LetterOfCreditDataStructures.Person
import com.creditletter.LetterOfCreditDataStructures.Port
import com.creditletter.LetterOfCreditDataStructures.PricedGood
import com.creditletter.LetterOfCreditDataStructures.Weight
import com.creditletter.states.BillOfLadingProperties
import com.creditletter.states.LetterOfCreditApplicationProperties
import com.creditletter.states.PurchaseOrderProperties

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Period

class GoldenPath {
    private lateinit var network: MockNetwork
    private lateinit var seller: StartedMockNode
    private lateinit var buyer: StartedMockNode
    private lateinit var issuingBank: StartedMockNode
    private lateinit var advisingBank: StartedMockNode

    @Before
    fun setup() {

        network = MockNetwork(listOf("com.creditletter.contracts",
                "net.corda.finance.contracts.asset"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))))

        seller = network.createPartyNode(CordaX500Name.parse("O=Lok Ma Exporters,L=Shenzhen,C=CN"))
        buyer = network.createPartyNode(CordaX500Name.parse("O=Analog Importers,L=Liverpool,C=GB"))
        issuingBank = network.createPartyNode(CordaX500Name.parse("O=First Bank of London,L=London,C=GB"))
        advisingBank = network.createPartyNode(CordaX500Name.parse("O=Shenzhen State Bank,L=Shenzhen,C=CN"))
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    private val StartedMockNode.org: String
        get() = info.legalIdentities.first().name.organisation

    private fun StartedMockNode.runFlow(flow: FlowLogic<SignedTransaction>): SignedTransaction {
        val future = startFlow(flow)
        network.runNetwork()
        return future.getOrThrow()
    }

    private val purchaseOrderProperties = PurchaseOrderProperties(
            purchaseOrderID = "123",
            seller = Company("Lok Ma Exporters", "123 Main St. Shenzhen, China", ""),
            buyer = Company("Analog Importers", "3 Smithdown Road. Liverpool, L2 6RE", ""),
            purchaseOrderDate = LocalDate.now(),
            term = 5,
            goods = listOf(
                    PricedGood(
                            "OLED 6\" Screens",
                            "Mock1",
                            10000,
                            3.DOLLARS,
                            Weight(30.0, LetterOfCreditDataStructures.WeightUnit.KG)
                    )
            )
    )

    private val letterOfCreditApplicationProperties = LetterOfCreditApplicationProperties(
            letterOfCreditApplicationID = purchaseOrderProperties.purchaseOrderID,
            applicationDate = LocalDate.now(),
            typeCredit = LetterOfCreditDataStructures.CreditType.SIGHT,
            expiryDate = LocalDate.MAX,
            portLoading = Port("China", "Shenzhen", "Dong Men Street", null, null),
            portDischarge = Port("UK", "Liverpool", "Maritime Centre", null, null),
            placePresentation = Location("UK", null, "Liverpool"),
            lastShipmentDate = LocalDate.MAX,
            periodPresentation = Period.ofDays(1),
            descriptionGoods = listOf(
                    PricedGood(
                            "OLED 6\" Screens",
                            purchaseOrderProperties.purchaseOrderID,
                            10000,
                            400.DOLLARS,
                            Weight(30.0, LetterOfCreditDataStructures.WeightUnit.KG)
                    )
            ),
            documentsRequired = listOf(),
            amount = 30000.DOLLARS
    )

    private val billOfLadingProperties = BillOfLadingProperties(
            billOfLadingID = purchaseOrderProperties.purchaseOrderID,
            issueDate = LocalDate.now(),
            carrierOwner = "Alice Shipping",
            nameOfVessel = "SurfRider",
            descriptionOfGoods = listOf(
                    Good(
                            "OLED 6\" Screens",
                            10000,
                            Weight(30.0, LetterOfCreditDataStructures.WeightUnit.KG)
                    )
            ),
            portOfLoading = Port("China", "Shenzhen", "Dong Men Street", null, null),
            portOfDischarge = Port("UK", "Liverpool", "Maritime Centre", null, null),
            grossWeight = Weight(1000.0, LetterOfCreditDataStructures.WeightUnit.KG),
            dateOfShipment = LocalDate.now(),
            shipper = Company("Lok Ma Exporters", "123 Main St. Shenzhen, China", ""),
            notify = Person("Analog Importers", "3 Smithdown Road. Liverpool, L2 6RE", "+447590043622"),
            consignee = Company("Analog Importers", "3 Smithdown Road. Liverpool, L2 6RE", "+447590043622"),
            placeOfReceipt = Location("UK", null, "Liverpool")
    )

    @Test
    fun `travel golden path`() {
        // Creating the purchase order.
        val flow = CreatePurchaseOrderFlow(buyer.org, purchaseOrderProperties)
        seller.runFlow(flow)

        // Applying for the letter of credit.
        val flow2 = ApplyForLoCFlow(seller.org, issuingBank.org, advisingBank.org, letterOfCreditApplicationProperties)
        buyer.runFlow(flow2)

        // Approving the letter of credit.
        val flow3 = ApproveLoCFlow(letterOfCreditApplicationProperties.letterOfCreditApplicationID)
        issuingBank.runFlow(flow3)

        // Adding the bill of lading.
        val flow4 = CreateBoLFlow(buyer.org, advisingBank.org, issuingBank.org, billOfLadingProperties)
        seller.runFlow(flow4)

        // note that these were not complete in the original app

        // Shipping the order.
        // TODO()

        // Paying the seller.
        // TODO()

        // Paying the advising bank.
        // TODO()

        // Paying the issuing bank.
        // TODO()
    }
}
