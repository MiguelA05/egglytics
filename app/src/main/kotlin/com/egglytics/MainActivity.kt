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
                        }
                    }
                }
            }
        }
    }
}
