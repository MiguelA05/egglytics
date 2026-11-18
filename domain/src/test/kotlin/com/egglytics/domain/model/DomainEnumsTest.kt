package com.egglytics.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainEnumsTest {

    @Test
    fun `egg classifications round-trip exact Firestore codes`() {
        val expected = listOf(
            "B" to EggClassification.B,
            "A" to EggClassification.A,
            "AA" to EggClassification.AA,
            "AAA" to EggClassification.AAA,
            "JUMBO" to EggClassification.JUMBO,
        )
        assertEquals(expected.size, EggClassification.entries.size)
        expected.forEach { (code, value) ->
            assertEquals(code, value.code)
            assertEquals(value, EggClassification.fromCode(code))
        }
    }

    @Test
    fun `feed ingredients round-trip exact Firestore codes`() {
        val expected = listOf(
            "concentrate" to FeedIngredient.CONCENTRATE,
            "corn_bran" to FeedIngredient.CORN_BRAN,
        )
        assertEquals(expected.size, FeedIngredient.entries.size)
        expected.forEach { (code, value) ->
            assertEquals(code, value.code)
            assertEquals(value, FeedIngredient.fromCode(code))
        }
    }

    @Test
    fun `member roles round-trip exact Firestore codes`() {
        val expected = listOf(
            "admin" to MemberRole.ADMIN,
            "operator" to MemberRole.OPERATOR,
        )
        assertEquals(expected.size, MemberRole.entries.size)
        expected.forEach { (code, value) ->
            assertEquals(code, value.code)
            assertEquals(value, MemberRole.fromCode(code))
        }
    }

    @Test
    fun `payment types round-trip exact Firestore codes`() {
        val expected = listOf(
            "payment" to PaymentType.PAYMENT,
            "refund" to PaymentType.REFUND,
            "overpayment_refund" to PaymentType.OVERPAYMENT_REFUND,
        )
        assertEquals(expected.size, PaymentType.entries.size)
        expected.forEach { (code, value) ->
            assertEquals(code, value.code)
            assertEquals(value, PaymentType.fromCode(code))
        }
    }

    @Test
    fun `unknown codes throw clear IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { EggClassification.fromCode("EXTRA") }
        assertThrows(IllegalArgumentException::class.java) { FeedIngredient.fromCode("wheat") }
        assertThrows(IllegalArgumentException::class.java) { MemberRole.fromCode("viewer") }
        assertThrows(IllegalArgumentException::class.java) { PaymentType.fromCode("credit") }
    }
}
