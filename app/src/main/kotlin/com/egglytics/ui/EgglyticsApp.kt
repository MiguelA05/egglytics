package com.egglytics.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egglytics.data.repository.SessionState

@Composable
internal fun EgglyticsApp(
    sessionViewModel: SessionViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    harvestViewModel: HarvestViewModel = viewModel(),
    saleViewModel: SaleViewModel = viewModel(),
    feedViewModel: FeedViewModel = viewModel(),
    expensesViewModel: ExpensesViewModel = viewModel(),
    cashViewModel: CashViewModel = viewModel(),
    countsAndOpeningViewModel: CountsAndOpeningViewModel = viewModel(),
) {
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
            LaunchedEffect(session.uid) { sessionViewModel.trackPriceDate() }
            when (sessionUi.destination) {
                AppDestination.HOME -> HomeRoute(
                    session,
                    homeViewModel,
                    sessionUi,
                    sessionViewModel::navigateTo,
                    sessionViewModel::signOut,
                    sessionViewModel::editPrice,
                    sessionViewModel::savePrice,
                    sessionViewModel::dismissPriceEditor,
                )

                AppDestination.HARVESTS -> HarvestRoute(
                    session,
                    harvestViewModel,
                    sessionViewModel::navigateHome,
                )

                AppDestination.SALES -> SaleRoute(
                    session,
                    saleViewModel,
                    sessionUi.catalog,
                    sessionViewModel::navigateHome,
                )

                AppDestination.FEED -> FeedRoute(
                    session,
                    feedViewModel,
                    sessionViewModel::navigateHome,
                )

                AppDestination.EXPENSES -> ExpensesRoute(
                    session,
                    expensesViewModel,
                    sessionViewModel::navigateHome,
                )

                AppDestination.CASH -> CashRoute(
                    session,
                    cashViewModel,
                    sessionViewModel::navigateHome,
                )

                AppDestination.COUNTS -> CountsAndOpeningRoute(
                    session,
                    countsAndOpeningViewModel,
                    sessionViewModel::navigateHome,
                )
            }
        }
    }
}
