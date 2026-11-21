package com.egglytics.domain.usecase

import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GetDailySummaryUseCaseTest {

    private val useCase = GetDailySummaryUseCase()
    private val today = LocalDate.of(2026, 11, 21)
    private val yesterday = LocalDate.of(2026, 11, 20)
    private val now = Instant.parse("2026-11-21T15:00:00Z")

    @Test
    fun `summarizes today harvests, sales and payments while filtering out other days`() {
        val todayHarvest = Harvest(
            id = "h_today",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            graded = mapOf(EggClassification.AA to 90L, EggClassification.A to 30L),
            discarded = 5L,
        )
        val yesterdayHarvest = Harvest(
            id = "h_prev",
            fechaOperativa = yesterday,
            createdAt = now.minusSeconds(86400),
            createdBy = "op_1",
            graded = mapOf(EggClassification.AA to 60L),
            discarded = 3L,
        )
        val todaySale = Sale(
            id = "s_today",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 2L, listPrice = 18500L)),
            total = 37000L,
            deliveryStatus = DeliveryStatus.PENDING,
        )
        val todayPayment = Payment(
            id = "p_today",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            saleId = "s_today",
            amount = 37000L,
            methodId = "cash",
            type = PaymentType.PAYMENT,
        )
        val yesterdayPayment = Payment(
            id = "p_prev",
            fechaOperativa = yesterday,
            createdAt = now.minusSeconds(86400),
            createdBy = "op_1",
            saleId = "s_prev",
            amount = 18500L,
            methodId = "cash",
            type = PaymentType.PAYMENT,
        )

        val summary = useCase(
            operationalDate = today,
            harvests = listOf(todayHarvest, yesterdayHarvest),
            sales = listOf(todaySale),
            payments = listOf(todayPayment, yesterdayPayment),
        )

        assertEquals(120L, summary.gradedEggs)
        assertEquals(5L, summary.discardedEggs)
        assertEquals(37000L, summary.totalSold)
        assertEquals(37000L, summary.totalCollected)
        assertEquals(2L, summary.pendingDeliveryBuckets)
    }

    @Test
    fun `previous day pending sale adds to accumulated pending buckets but not today sold or collected`() {
        val yesterdayPendingSale = Sale(
            id = "s_yesterday_pending",
            fechaOperativa = yesterday,
            createdAt = now.minusSeconds(86400),
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 3L, listPrice = 18500L)),
            total = 55500L,
            deliveryStatus = DeliveryStatus.PENDING,
        )
        val yesterdayPayment = Payment(
            id = "p_yesterday",
            fechaOperativa = yesterday,
            createdAt = now.minusSeconds(86400),
            createdBy = "op_1",
            saleId = "s_yesterday_pending",
            amount = 55500L,
            methodId = "cash",
            type = PaymentType.PAYMENT,
        )

        val summary = useCase(
            operationalDate = today,
            harvests = emptyList(),
            sales = listOf(yesterdayPendingSale),
            payments = listOf(yesterdayPayment),
        )

        assertEquals(0L, summary.totalSold)
        assertEquals(0L, summary.totalCollected)
        assertEquals(3L, summary.pendingDeliveryBuckets)
    }

    @Test
    fun `sale without payment increases totalSold while totalCollected stays zero`() {
        val unpaidSale = Sale(
            id = "s_unpaid",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 2L, listPrice = 18500L)),
            total = 37000L,
            deliveryStatus = DeliveryStatus.PENDING,
        )

        val summary = useCase(
            operationalDate = today,
            harvests = emptyList(),
            sales = listOf(unpaidSale),
            payments = emptyList(),
        )

        assertEquals(37000L, summary.totalSold)
        assertEquals(0L, summary.totalCollected)
        assertEquals(2L, summary.pendingDeliveryBuckets)
    }

    @Test
    fun `marking sale delivered reduces pending buckets while preserving sold and collected totals`() {
        val deliveredSale = Sale(
            id = "s_delivered",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 2L, listPrice = 18500L)),
            total = 37000L,
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveredAt = now.plusSeconds(60),
            deliveredBy = "op_1",
        )
        val payment = Payment(
            id = "p_delivered",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            saleId = "s_delivered",
            amount = 37000L,
            methodId = "cash",
            type = PaymentType.PAYMENT,
        )

        val summary = useCase(
            operationalDate = today,
            harvests = emptyList(),
            sales = listOf(deliveredSale),
            payments = listOf(payment),
        )

        assertEquals(37000L, summary.totalSold)
        assertEquals(37000L, summary.totalCollected)
        assertEquals(0L, summary.pendingDeliveryBuckets)
    }
}
