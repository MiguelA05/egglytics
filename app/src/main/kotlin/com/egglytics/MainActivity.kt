package com.egglytics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.data.repository.SessionState
import com.egglytics.ui.AppDestination
import com.egglytics.ui.CashScreen
import com.egglytics.ui.CashViewModel
import com.egglytics.ui.CountsAndOpeningScreen
import com.egglytics.ui.CountsAndOpeningViewModel
import com.egglytics.ui.ExpensesScreen
import com.egglytics.ui.ExpensesViewModel
import com.egglytics.ui.FeedScreen
import com.egglytics.ui.FeedViewModel
import com.egglytics.ui.HarvestScreen
import com.egglytics.ui.HarvestViewModel
import com.egglytics.ui.HomeScreen
import com.egglytics.ui.HomeViewModel
import com.egglytics.ui.LoginScreen
import com.egglytics.ui.SaleScreen
import com.egglytics.ui.SaleViewModel
import com.egglytics.ui.SessionViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val harvestViewModel: HarvestViewModel by viewModels()
    private val saleViewModel: SaleViewModel by viewModels()
    private val feedViewModel: FeedViewModel by viewModels()
    private val expensesViewModel: ExpensesViewModel by viewModels()
    private val cashViewModel: CashViewModel by viewModels()
    private val countsAndOpeningViewModel: CountsAndOpeningViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val sessionUi by sessionViewModel.uiState.collectAsStateWithLifecycle()
                val session = sessionUi.sessionState

                LaunchedEffect(session) {
                    homeViewModel.syncSession(session)
                    harvestViewModel.syncSession(session)
                    saleViewModel.syncSession(session)
                    feedViewModel.syncSession(session)
                    expensesViewModel.syncSession(session)
                    cashViewModel.syncSession(session)
                    countsAndOpeningViewModel.syncSession(session)
                }

                when (session) {
                    is SessionState.Unauthenticated,
                    is SessionState.Rejected,
                    -> {
                        LoginScreen(
                            email = sessionUi.email,
                            password = sessionUi.password,
                            isLoading = sessionUi.isLoading,
                            rejectionMessage = (session as? SessionState.Rejected)?.message,
                            onEmailChange = sessionViewModel::onEmailChange,
                            onPasswordChange = sessionViewModel::onPasswordChange,
                            onSignIn = sessionViewModel::signIn,
                        )
                    }

                    is SessionState.Authenticated -> {
                        when (sessionUi.destination) {
                            AppDestination.HOME -> {
                                val homeUi by homeViewModel.uiState.collectAsStateWithLifecycle()
                                HomeScreen(
                                    session = session,
                                    state = sessionUi,
                                    homeState = homeUi,
                                    onOpenHarvests = { sessionViewModel.navigateTo(AppDestination.HARVESTS) },
                                    onOpenSales = { sessionViewModel.navigateTo(AppDestination.SALES) },
                                    onOpenFeed = { sessionViewModel.navigateTo(AppDestination.FEED) },
                                    onOpenExpenses = { sessionViewModel.navigateTo(AppDestination.EXPENSES) },
                                    onOpenCash = { sessionViewModel.navigateTo(AppDestination.CASH) },
                                    onOpenCounts = { sessionViewModel.navigateTo(AppDestination.COUNTS) },
                                    onRetryReconcile = homeViewModel::retryReconcile,
                                    onDismissSyncNotice = homeViewModel::dismissSyncNotice,
                                    onSignOut = sessionViewModel::signOut,
                                )
                            }

                            AppDestination.HARVESTS -> {
                                val harvestUi by harvestViewModel.uiState.collectAsStateWithLifecycle()
                                HarvestScreen(
                                    state = harvestUi,
                                    onBack = sessionViewModel::navigateHome,
                                    onHarvestQtyChange = harvestViewModel::onHarvestQtyChange,
                                    onDiscardedChange = harvestViewModel::onDiscardedChange,
                                    onHarvestDateChange = harvestViewModel::onHarvestDateChange,
                                    onHarvestNoteChange = harvestViewModel::onHarvestNoteChange,
                                    onSubmitHarvest = { harvestViewModel.submitHarvest(session) },
                                    onVoidHarvest = { item, reason ->
                                        harvestViewModel.voidHarvest(session, item, reason)
                                    },
                                )
                            }

                            AppDestination.SALES -> {
                                val saleUi by saleViewModel.uiState.collectAsStateWithLifecycle()
                                SaleScreen(
                                    currency = session.business.currency,
                                    activePrices = sessionUi.activePrices,
                                    state = saleUi,
                                    onBack = sessionViewModel::navigateHome,
                                    onSaleClassificationChange = saleViewModel::onSaleClassificationChange,
                                    onPaymentMethodSelect = saleViewModel::onPaymentMethodSelect,
                                    onSaleBucketsChange = saleViewModel::onSaleBucketsChange,
                                    onSaleDateChange = saleViewModel::onSaleDateChange,
                                    onSaleNoteChange = saleViewModel::onSaleNoteChange,
                                    onSubmitSale = {
                                        saleViewModel.submitSale(session, sessionUi.activePrices)
                                    },
                                    onMarkSaleDelivered = saleViewModel::markSaleDelivered,
                                    onVoidSale = { item, reason ->
                                        saleViewModel.voidSale(session, item, reason)
                                    },
                                )
                            }

                            AppDestination.FEED -> {
                                val feedUi by feedViewModel.uiState.collectAsStateWithLifecycle()
                                FeedScreen(
                                    currency = session.business.currency,
                                    state = feedUi,
                                    onBack = sessionViewModel::navigateHome,
                                    onModeChange = feedViewModel::onModeChange,
                                    onIngredientChange = feedViewModel::onIngredientChange,
                                    onBagsChange = feedViewModel::onBagsChange,
                                    onTotalCostChange = feedViewModel::onTotalCostChange,
                                    onMethodSelect = feedViewModel::onMethodSelect,
                                    onSupplierChange = feedViewModel::onSupplierChange,
                                    onLossKgChange = feedViewModel::onLossKgChange,
                                    onLossTypeChange = feedViewModel::onLossTypeChange,
                                    onLossReasonChange = feedViewModel::onLossReasonChange,
                                    onDateChange = feedViewModel::onDateChange,
                                    onNoteChange = feedViewModel::onNoteChange,
                                    onPeriodStartChange = feedViewModel::onPeriodStartChange,
                                    onPeriodEndChange = feedViewModel::onPeriodEndChange,
                                    onSubmit = { feedViewModel.submit(session) },
                                    onVoidMovement = { col, id, reason ->
                                        feedViewModel.voidFeedMovement(session, col, id, reason)
                                    },
                                )
                            }

                            AppDestination.EXPENSES -> {
                                val expensesUi by expensesViewModel.uiState.collectAsStateWithLifecycle()
                                ExpensesScreen(
                                    currency = session.business.currency,
                                    state = expensesUi,
                                    onBack = sessionViewModel::navigateHome,
                                    onModeChange = expensesViewModel::onModeChange,
                                    onAmountChange = expensesViewModel::onAmountChange,
                                    onCategorySelect = expensesViewModel::onCategorySelect,
                                    onMethodSelect = expensesViewModel::onMethodSelect,
                                    onDateChange = expensesViewModel::onDateChange,
                                    onDescriptionChange = expensesViewModel::onDescriptionChange,
                                    onNoteChange = expensesViewModel::onNoteChange,
                                    onSubmit = { expensesViewModel.submit(session) },
                                    onVoidMovement = { col, id, reason ->
                                        expensesViewModel.voidMovement(session, col, id, reason)
                                    },
                                )
                            }

                            AppDestination.CASH -> {
                                val cashUi by cashViewModel.uiState.collectAsStateWithLifecycle()
                                CashScreen(
                                    currency = session.business.currency,
                                    state = cashUi,
                                    onBack = sessionViewModel::navigateHome,
                                    onPeriodStartChange = cashViewModel::onPeriodStartChange,
                                    onPeriodEndChange = cashViewModel::onPeriodEndChange,
                                )
                            }

                            AppDestination.COUNTS -> {
                                val countsUi by countsAndOpeningViewModel.uiState.collectAsStateWithLifecycle()
                                CountsAndOpeningScreen(
                                    currency = session.business.currency,
                                    state = countsUi,
                                    onBack = sessionViewModel::navigateHome,
                                    onModeChange = countsAndOpeningViewModel::onModeChange,
                                    onDateChange = countsAndOpeningViewModel::onDateChange,
                                    onFeedPhysicalChange = countsAndOpeningViewModel::onFeedPhysicalChange,
                                    onFeedReasonChange = countsAndOpeningViewModel::onFeedReasonChange,
                                    onCashPhysicalChange = countsAndOpeningViewModel::onCashPhysicalChange,
                                    onCashReasonChange = countsAndOpeningViewModel::onCashReasonChange,
                                    onIsClosingChange = countsAndOpeningViewModel::onIsClosingChange,
                                    onNoteChange = countsAndOpeningViewModel::onNoteChange,
                                    onSubmit = { countsAndOpeningViewModel.submit(session) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
