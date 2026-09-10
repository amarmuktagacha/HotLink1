package com.shohan.hotlink.ui.screens

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shohan.hotlink.viewmodel.ViewerStatus
import com.shohan.hotlink.viewmodel.ViewerViewModel

@Composable
fun ViewerScreen(onBack: () -> Unit, viewModel: ViewerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.status == ViewerStatus.CONNECTED || uiState.status == ViewerStatus.CONNECTING) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                viewModel.onSurfaceReady(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                viewModel.onSurfaceDestroyed()
                            }
                        })
                    }
                }
            )

            if (uiState.status == ViewerStatus.CONNECTING) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            IconButton(
                onClick = {
                    viewModel.disconnect()
                    onBack()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = Color.White)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "ফিরে যান")
                }
                Text("ভিউয়ার", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "প্রথমে এই ফোনের WiFi থেকে হোস্ট ফোনের হটস্পটে কানেক্ট করুন, তারপর নিচে হোস্টের IP ও পেয়ারিং কোড দিন।",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.hostAddress,
                onValueChange = { viewModel.updateHostAddress(it) },
                label = { Text("হোস্টের IP ঠিকানা") },
                placeholder = { Text("যেমন 192.168.43.1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.pairingCode,
                onValueChange = { viewModel.updatePairingCode(it) },
                label = { Text("পেয়ারিং কোড") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (uiState.status == ViewerStatus.ERROR && uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.connect() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("কানেক্ট করুন")
            }
        }
    }
}
