package com.nextplace.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nextplace.data.TokenRepository
import com.nextplace.ui.auth.LoginScreen
import com.nextplace.ui.home.HomeScreen
import com.nextplace.ui.theme.NextplaceTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenRepository: TokenRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NextplaceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NextplaceApp(tokenRepository)
                }
            }
        }
    }
}

@Composable
fun NextplaceApp(tokenRepository: TokenRepository) {
    var isLoading by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val token = tokenRepository.jwtToken.first()
        isAuthenticated = !token.isNullOrBlank()
        isLoading = false
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        isAuthenticated -> {
            HomeScreen()
        }
        else -> {
            LoginScreen(
                onLoginSuccess = { token ->
                    kotlinx.coroutines.GlobalScope.launch {
                        tokenRepository.saveToken(token)
                        isAuthenticated = true
                    }
                }
            )
        }
    }
}
