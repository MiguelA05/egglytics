package com.egglytics.domain.usecase

import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Price
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class GetActivePriceUseCaseTest {

    private val useCase = GetActivePriceUseCase()

    @Test
    fun `returns price when validFrom matches operationalDate exactly`() {
        val date = LocalDate.of(2026, 11, 18)
        val price = price(id = "p1", validFrom = date, amount = 18_000L)

        assertEquals(
            ActivePriceResult.Found(price),
            useCase(listOf(price), EggClassification.AA, date),
        )
    }

    @Test
    fun `returns most recent prior price when validFrom is before operationalDate`() {
        val older = price(id = "p1", validFrom = LocalDate.of(2026, 11, 1), amount = 16_000L)
        val newer = price(id = "p2", validFrom = LocalDate.of(2026, 11, 10), amount = 17_500L)

        assertEquals(
            ActivePriceResult.Found(newer),
            useCase(listOf(older, newer), EggClassification.AA, LocalDate.of(2026, 11, 18)),
        )
    }

    @Test
    fun `excludes price whose validFrom is one day after operationalDate`() {
        val saleDate = LocalDate.of(2026, 11, 18)
        val prior = price(id = "p1", validFrom = LocalDate.of(2026, 11, 10), amount = 17_000L)
        val nextDay = price(id = "p2", validFrom = saleDate.plusDays(1), amount = 19_000L)

        assertEquals(
            ActivePriceResult.Found(prior),
            useCase(listOf(prior, nextDay), EggClassification.AA, saleDate),
        )
        assertEquals(
            ActivePriceResult.NotFound,
            useCase(listOf(nextDay), EggClassification.AA, saleDate),
        )
    }

    @Test
    fun `uses passed operationalDate instead of system clock even for far future date`() {
        val futureSaleDate = LocalDate.of(2030, 6, 15)
        val futurePrice = price(id = "p_2030", validFrom = LocalDate.of(2030, 6, 1), amount = 25_000L)

        assertEquals(
            ActivePriceResult.Found(futurePrice),
            useCase(listOf(futurePrice), EggClassification.AA, futureSaleDate),
        )
    }

    @Test
    fun `historical sale keeps price valid on its operationalDate after catalog change (AC-04)`() {
        val day10 = price(id = "p10", validFrom = LocalDate.of(2026, 11, 10), amount = 16_000L)
        val day20 = price(id = "p20", validFrom = LocalDate.of(2026, 11, 20), amount = 18_000L)
        val catalog = listOf(day10, day20)

        assertEquals(
            ActivePriceResult.Found(day10),
            useCase(catalog, EggClassification.AA, LocalDate.of(2026, 11, 15)),
        )
        assertEquals(
            ActivePriceResult.Found(day20),
            useCase(catalog, EggClassification.AA, LocalDate.of(2026, 11, 25)),
        )
    }

    @Test
    fun `breaks validFrom tie by latest createdAt (AC-39)`() {
        val validDate = LocalDate.of(2026, 11, 10)
        val first = price(
            id = "p_old",
            validFrom = validDate,
            createdAt = Instant.parse("2026-11-10T08:00:00Z"),
            amount = 17_000L,
        )
        val second = price(
            id = "p_new",
            validFrom = validDate,
            createdAt = Instant.parse("2026-11-10T14:30:00Z"),
            amount = 18_500L,
        )

        assertEquals(
            ActivePriceResult.Found(second),
            useCase(listOf(second, first), EggClassification.AA, LocalDate.of(2026, 11, 18)),
        )
    }

    @Test
    fun `breaks double tie of validFrom and createdAt deterministically by id`() {
        val validDate = LocalDate.of(2026, 11, 10)
        val sameInstant = Instant.parse("2026-11-10T09:00:00Z")
        val pA = price(id = "price_a", validFrom = validDate, createdAt = sameInstant, amount = 17_000L)
        val pB = price(id = "price_b", validFrom = validDate, createdAt = sameInstant, amount = 18_000L)

        assertEquals(
            ActivePriceResult.Found(pB),
            useCase(listOf(pA, pB), EggClassification.AA, LocalDate.of(2026, 11, 18)),
        )
        assertEquals(
            ActivePriceResult.Found(pB),
            useCase(listOf(pB, pA), EggClassification.AA, LocalDate.of(2026, 11, 18)),
        )
    }

    @Test
    fun `result does not depend on input list order`() {
        val prices = listOf(
            price(id = "p1", validFrom = LocalDate.of(2026, 11, 1), amount = 15_000L),
            price(id = "p2", validFrom = LocalDate.of(2026, 11, 5), amount = 16_000L),
            price(
                id = "p3",
                validFrom = LocalDate.of(2026, 11, 10),
                createdAt = Instant.parse("2026-11-10T08:00:00Z"),
                amount = 17_000L,
            ),
            price(
                id = "p4",
                validFrom = LocalDate.of(2026, 11, 10),
                createdAt = Instant.parse("2026-11-10T12:00:00Z"),
                amount = 17_500L,
            ),
            price(id = "p5", validFrom = LocalDate.of(2026, 11, 25), amount = 20_000L),
        )
        val shuffled = prices.shuffled(Random(42))

        assertEquals(
            ActivePriceResult.Found(prices[3]),
            useCase(shuffled, EggClassification.AA, LocalDate.of(2026, 11, 18)),
        )
    }

    @Test
    fun `returns NotFound when catalog is empty, all prices are future or only other classifications exist (AC-39)`() {
        val saleDate = LocalDate.of(2026, 11, 18)
        assertEquals(ActivePriceResult.NotFound, useCase(emptyList(), EggClassification.AA, saleDate))

        val otherClass = price(
            id = "p_a",
            classification = EggClassification.A,
            validFrom = LocalDate.of(2026, 11, 1),
            amount = 15_000L,
        )
        assertEquals(ActivePriceResult.NotFound, useCase(listOf(otherClass), EggClassification.AA, saleDate))
    }

    @Test
    fun `isolates prices by classification`() {
        val saleDate = LocalDate.of(2026, 11, 18)
        val priceA = price(
            id = "p_a",
            classification = EggClassification.A,
            validFrom = LocalDate.of(2026, 11, 15),
            amount = 16_000L,
        )
        val priceAA = price(
            id = "p_aa",
            classification = EggClassification.AA,
            validFrom = LocalDate.of(2026, 11, 1),
            amount = 18_000L,
        )

        assertEquals(
            ActivePriceResult.Found(priceAA),
            useCase(listOf(priceA, priceAA), EggClassification.AA, saleDate),
        )
    }

    private fun price(
        id: String,
        classification: EggClassification = EggClassification.AA,
        validFrom: LocalDate,
        createdAt: Instant = Instant.parse("2026-11-01T10:00:00Z"),
        amount: Long,
    ): Price = Price(
        id = id,
        classification = classification,
        pricePerBucket = amount,
        validFrom = validFrom,
        createdAt = createdAt,
        createdBy = "admin_uid",
    )
}
