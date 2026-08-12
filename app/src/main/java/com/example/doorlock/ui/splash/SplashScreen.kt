package com.example.doorlock.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doorlock.R

/**
 * 스플래시 화면.
 *
 * 기기에 이미 등록된 학번이 있는지 확인하고(DataStore),
 * - 있으면: 학번 등록 화면을 건너뛰고 자동으로 Home으로 이동
 * - 없으면: 학번 등록 화면으로 이동할 수 있는 버튼을 표시
 */
@Composable
fun SplashScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: SplashViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val destination by viewModel.destination.collectAsState()

    // 등록된 학번이 확인되면(=Home), 사용자 조작 없이 바로 이동합니다.
    LaunchedEffect(destination) {
        if (destination is SplashDestination.Home) {
            onNavigateToHome()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .clip(MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.khlug_logo),
                        contentDescription = "동아리 로고",
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "BLE Doorlock",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (destination == null) {
                // 아직 DataStore 확인 중
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (destination is SplashDestination.Register) {
                // 등록된 학번이 없을 때만 버튼을 보여주고, 사용자가 직접 넘어갑니다.
                Button(onClick = onNavigateToRegister) {
                    Text(text = "학번 등록하러 가기")
                }
            }
            // destination이 Home인 경우는 LaunchedEffect에서 바로 이동하므로 버튼을 보여주지 않습니다.
        }
    }
}
