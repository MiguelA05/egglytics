package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.ExpenseCategory
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FirebaseExpenseCategoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ExpenseCategoryRepository {

    override fun observeActiveCategories(): Flow<List<ExpenseCategory>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("expenseCategories")
            .whereEqualTo("active", true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val categories = snapshot.documents
                        .mapNotNull { it.toExpenseCategoryOrNull() }
                        .filter { it.active }
                        .sortedBy { it.name }
                    trySend(categories)
                }
            }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toExpenseCategoryOrNull(): ExpenseCategory? {
    if (!exists()) return null
    val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
    val active = getBoolean("active") == true
    return ExpenseCategory(id = id, name = name, active = active)
}
