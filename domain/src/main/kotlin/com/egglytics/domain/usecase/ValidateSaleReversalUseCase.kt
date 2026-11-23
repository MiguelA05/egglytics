package com.egglytics.domain.usecase

import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale

data class SaleReversalValidationRequest(
    val sale: Sale,
    val saleIsPendingWrite: Boolean,
    val salePayments: List<Payment>,
    val pendingPaymentIds: Set<String>,
    val hasActiveReturns: Boolean,
    val reversals: List<Reversal>,
)

sealed interface SaleReversalEligibility {
    data class Allowed(val payment: Payment) : SaleReversalEligibility
    data class Blocked(val reason: String) : SaleReversalEligibility
}

class ValidateSaleReversalUseCase {
    operator fun invoke(request: SaleReversalValidationRequest): SaleReversalEligibility {
        val reversedSaleIds = request.reversals
            .asSequence()
            .filter { it.targetCollection == ReversalTargetCollection.SALES }
            .map { it.targetId }
            .toSet()
        val reversedPaymentIds = request.reversals
            .asSequence()
            .filter { it.targetCollection == ReversalTargetCollection.PAYMENTS }
            .map { it.targetId }
            .toSet()
        val activePayments = request.salePayments.filter { it.id !in reversedPaymentIds }

        val blockedReason = when {
            request.sale.id in reversedSaleIds -> "La venta ya se encuentra anulada."
            request.saleIsPendingWrite -> "Espera a que la venta termine de sincronizarse."
            request.sale.deliveryStatus != DeliveryStatus.PENDING ->
                "No se puede anular una venta ya entregada."
            request.hasActiveReturns -> "No se puede anular: la venta tiene devoluciones registradas."
            activePayments.any { it.type != PaymentType.PAYMENT } ->
                "No se puede anular: la venta tiene reembolsos registrados."
            activePayments.isEmpty() ->
                "Este flujo solo permite anular ventas con un único cobro completo."
            activePayments.size > 1 ->
                "No se puede anular: la venta tiene varios pagos registrados."
            activePayments.single().id in request.pendingPaymentIds ->
                "Espera a que el pago termine de sincronizarse."
            activePayments.single().amount != request.sale.total ->
                "No se puede anular: el cobro (${activePayments.single().amount}) " +
                    "no coincide con el total (${request.sale.total})."
            else -> null
        }

        return if (blockedReason != null) {
            SaleReversalEligibility.Blocked(blockedReason)
        } else {
            SaleReversalEligibility.Allowed(activePayments.single())
        }
    }
}
