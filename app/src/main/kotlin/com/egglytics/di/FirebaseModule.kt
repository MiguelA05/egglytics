package com.egglytics.di

import android.content.Context
import com.egglytics.BuildConfig
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.FirebaseFeedRepository
import com.egglytics.data.repository.FirebaseHarvestRepository
import com.egglytics.data.repository.FirebasePaymentMethodRepository
import com.egglytics.data.repository.FirebasePriceRepository
import com.egglytics.data.repository.FirebaseRemoteSubmissionVerifier
import com.egglytics.data.repository.FirebaseReversalRepository
import com.egglytics.data.repository.FirebaseSaleRepository
import com.egglytics.data.repository.FirebaseSessionRepository
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.RemoteSubmissionVerifier
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionRepository
import com.egglytics.data.repository.SharedPreferencesSubmissionReceiptStore
import com.egglytics.data.repository.SubmissionReceiptStore
import com.egglytics.data.repository.SubmissionReconciler
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.GetActivePriceUseCase
import com.egglytics.domain.usecase.GetDailySummaryUseCase
import com.egglytics.domain.usecase.GetFeedBalanceUseCase
import com.egglytics.domain.usecase.ValidateSaleReversalUseCase
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
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            val apps = FirebaseApp.getApps(context)
            val emulatorApp = apps.firstOrNull { it.options.projectId == "demo-egglytics-test" }
            if (emulatorApp != null) return emulatorApp
            val fallbackOptions = FirebaseOptions.Builder()
                .setProjectId("demo-egglytics-test")
                .setApplicationId("1:000000000000:android:0000000000000000000000")
                .setApiKey("demo-api-key")
                .build()
            val hasDefault = apps.any { it.name == FirebaseApp.DEFAULT_APP_NAME }
            return if (hasDefault) {
                FirebaseApp.initializeApp(context, fallbackOptions, "emulator")
            } else {
                FirebaseApp.initializeApp(context, fallbackOptions)
            }
        }
        return checkNotNull(FirebaseApp.getApps(context).firstOrNull()) {
            "FirebaseApp no inicializado en release: falta google-services.json real."
        }
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

    @Provides
    fun provideGetFeedBalanceUseCase(): GetFeedBalanceUseCase = GetFeedBalanceUseCase()

    @Provides
    fun provideValidateSaleReversalUseCase(): ValidateSaleReversalUseCase = ValidateSaleReversalUseCase()

    @Provides
    @Singleton
    fun provideSubmissionReconciler(
        store: SubmissionReceiptStore,
        verifier: RemoteSubmissionVerifier,
    ): SubmissionReconciler = SubmissionReconciler(store, verifier)
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
    abstract fun bindFeedRepository(impl: FirebaseFeedRepository): FeedRepository

    @Binds
    @Singleton
    abstract fun bindSaleRepository(impl: FirebaseSaleRepository): SaleRepository

    @Binds
    @Singleton
    abstract fun bindPaymentMethodRepository(impl: FirebasePaymentMethodRepository): PaymentMethodRepository

    @Binds
    @Singleton
    abstract fun bindReversalRepository(impl: FirebaseReversalRepository): ReversalRepository

    @Binds
    @Singleton
    abstract fun bindSubmissionReceiptStore(
        impl: SharedPreferencesSubmissionReceiptStore,
    ): SubmissionReceiptStore

    @Binds
    @Singleton
    abstract fun bindRemoteSubmissionVerifier(
        impl: FirebaseRemoteSubmissionVerifier,
    ): RemoteSubmissionVerifier
}
