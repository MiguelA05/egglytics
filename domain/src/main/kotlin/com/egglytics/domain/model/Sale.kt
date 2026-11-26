package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

enum class DeliveryStatus(val firestoreValue: String) {
    PENDING("pending"),
    DELIVERED("delivered"),
    ;

    companion object {
        fun fromFirestore(value: String): DeliveryStatus? =
            entries.firstOrNull { it.firestoreValue == value }
    }
}

data class SaleLine(
    val classification: EggClassification,
    val buckets: Long,
    val eggs: Long,
    val listPrice: Long,
    val unitPrice: Long,
    val subtotal: Long,
) {
    init {
        require(buckets > 0L) { "La cantidad de cubetas debe ser mayor que 0 ($buckets)" }
        require(eggs == buckets * EGGS_PER_BUCKET) {
            "Los huevos ($eggs) deben equivaler a cubetas * $EGGS_PER_BUCKET"
        }
        require(listPrice > 0L) { "El precio de lista debe ser mayor que 0 ($listPrice)" }
        require(unitPrice > 0L) { "El precio unitario debe ser mayor que 0 ($unitPrice)" }
        require(subtotal == buckets * unitPrice) {
            "El subtotal ($subtotal) debe ser cubetas * precio unitario (${buckets * unitPrice})"
        }
    }

    companion object {
        const val EGGS_PER_BUCKET: Long = 30L

        fun create(
            classification: EggClassification,
            buckets: Long,
            listPrice: Long,
            unitPrice: Long = listPrice,
        ): SaleLine {
            require(buckets > 0L) { "La cantidad de cubetas debe ser mayor que 0 ($buckets)" }
            require(listPrice > 0L) { "El precio de lista debe ser mayor que 0 ($listPrice)" }
            require(unitPrice > 0L) { "El precio unitario debe ser mayor que 0 ($unitPrice)" }
            return SaleLine(
                classification = classification,
                buckets = buckets,
                eggs = Math.multiplyExact(buckets, EGGS_PER_BUCKET),
                listPrice = listPrice,
                unitPrice = unitPrice,
                subtotal = Math.multiplyExact(buckets, unitPrice),
            )
        }
    }
}

data class SingleLineSaleRequest(
    val id: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val classification: EggClassification,
    val buckets: Long,
    val activePrice: Price,
    val note: String? = null,
)

data class Sale(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val lines: List<SaleLine>,
    val total: Long,
    val deliveryStatus: DeliveryStatus,
    val deliveredAt: Instant? = null,
    val deliveredBy: String? = null,
    val note: String? = null,
    val customerName: String? = null,
) {
    init {
        require(lines.isNotEmpty()) { "La venta debe tener al menos una línea" }
        val expectedTotal = lines.fold(0L) { sum, line -> Math.addExact(sum, line.subtotal) }
        require(total > 0L && total == expectedTotal) {
            "El total ($total) debe coincidir con la suma de subtotales ($expectedTotal)"
        }
        when (deliveryStatus) {
            DeliveryStatus.PENDING -> {
                require(deliveredAt == null && deliveredBy == null) {
                    "Una venta pendiente no debe tener deliveredAt ni deliveredBy"
                }
            }

            DeliveryStatus.DELIVERED -> {
                require(deliveredAt != null && !deliveredBy.isNullOrBlank()) {
                    "Una venta entregada requiere deliveredAt y deliveredBy"
                }
            }
        }
    }

    companion object {
        fun createSingleLinePending(request: SingleLineSaleRequest): Sale {
            Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
            require(request.activePrice.classification == request.classification) {
                "El precio vigente (${request.activePrice.classification}) no coincide con ${request.classification}"
            }
            val line = SaleLine.create(
                classification = request.classification,
                buckets = request.buckets,
                listPrice = request.activePrice.pricePerBucket,
            )
            return Sale(
                id = request.id,
                fechaOperativa = request.fechaOperativa,
                createdAt = request.createdAt,
                createdBy = request.createdBy,
                lines = listOf(line),
                total = line.subtotal,
                deliveryStatus = DeliveryStatus.PENDING,
                note = request.note?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
