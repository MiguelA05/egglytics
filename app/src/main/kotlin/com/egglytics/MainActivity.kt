package com.egglytics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egglytics.ui.MainScreen
import com.egglytics.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onSignIn = viewModel::signIn,
                    onSignOut = viewModel::signOut,
                    onHarvestQtyChange = viewModel::onHarvestQtyChange,
                    onDiscardedChange = viewModel::onDiscardedChange,
                    onHarvestDateChange = viewModel::onHarvestDateChange,
                    onHarvestNoteChange = viewModel::onHarvestNoteChange,
                    onSubmitHarvest = { viewModel.submitHarvest() },
                    onSaleClassificationChange = viewModel::onSaleClassificationChange,
                    onSaleBucketsChange = viewModel::onSaleBucketsChange,
                    onSaleDateChange = viewModel::onSaleDateChange,
                    onSaleNoteChange = viewModel::onSaleNoteChange,
                    onSubmitSale = { viewModel.submitSale() },
                    onMarkSaleDelivered = viewModel::markSaleDelivered,
                )
            }
        }
    }
}
