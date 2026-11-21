package com.egglytics.di

import android.content.Context
import com.egglytics.BuildConfig
import com.egglytics.data.repository.FirebaseHarvestRepository
import com.egglytics.data.repository.FirebasePaymentMethodRepository
import com.egglytics.data.repository.FirebasePriceRepository
import com.egglytics.data.repository.FirebaseSaleRepository
import com.egglytics.data.repository.FirebaseSessionRepository
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionRepository
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.GetActivePriceUseCase
import com.egglytics.domain.usecase.GetDailySummaryUseCase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private fun ensureFirebaseApp(context: Context): FirebaseApp {
        val existing = FirebaseApp.getApps(context).firstOrNull()
        if (existing != null) return existing
        check(BuildConfig.USE_FIREBASE_EMULATOR) {
            "FirebaseApp no inicializado en release: falta google-services.json real."
        }
        val fallbackOptions = FirebaseOptions.Builder()
            .setProjectId("demo-egglytics-test")
            .setApplicationId("1:000000000000:android:0000000000000000000000")
            .setApiKey("demo-api-key")
            .build()
        return FirebaseApp.initializeApp(context, fallbackOptions)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
        val app = ensureFirebaseApp(context)
        return FirebaseAuth.getInstance(app).apply {
            if (BuildConfig.USE_FIREBASE_EMULATOR) {
                useEmulator(
                    BuildConfig.FIREBASE_EMULATOR_HOST,
                    BuildConfig.FIREBASE_AUTH_EMULATOR_PORT,
                )
            }
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(@ApplicationContext context: Context): FirebaseFirestore {
        val app = ensureFirebaseApp(context)
        return FirebaseFirestore.getInstance(app).apply {
            if (BuildConfig.USE_FIREBASE_EMULATOR) {
                useEmulator(
                    BuildConfig.FIREBASE_EMULATOR_HOST,
                    BuildConfig.FIRESTORE_EMULATOR_PORT,
                )
            }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build(),
                )
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideOperationalClock(): Clock = Clock.system(OperationalDate.BOGOTA_ZONE)

    @Provides
    fun provideGetActivePriceUseCase(): GetActivePriceUseCase = GetActivePriceUseCase()

    @Provides
    fun provideGetDailySummaryUseCase(): GetDailySummaryUseCase = GetDailySummaryUseCase()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: FirebaseSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindPriceRepository(impl: FirebasePriceRepository): PriceRepository

    @Binds
    @Singleton
    abstract fun bindHarvestRepository(impl: FirebaseHarvestRepository): HarvestRepository

    @Binds
    @Singleton
    abstract fun bindSaleRepository(impl: FirebaseSaleRepository): SaleRepository

    @Binds
    @Singleton
    abstract fun bindPaymentMethodRepository(impl: FirebasePaymentMethodRepository): PaymentMethodRepository
}
