package com.shohan.hotlink.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shohan.hotlink.capture.ScreenCaptureService
import com.shohan.hotlink.viewmodel.HostStatus
import com.shohan.hotlink.viewmodel.HostViewModel

@Composable
fun HostScreen(onBack: () -> Unit, viewModel: HostViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    val projectionManager = remember {
        context.getSystemService(MediaProjectionManager::class.java)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_PAIRING_CODE, uiState.pairingCode)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
                viewModel.onSharingStarted()
            } catch (error: Exception) {
                viewModel.onStartFailed(error.message ?: "স্ক্রিন শেয়ার সার্ভিস চালু করা যায়নি")
            }
        } else {
            viewModel.onSharingStopped()
        }
    }

    fun stopSharing() {
        context.startService(
            Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
        )
        viewModel.onSharingStopped()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (uiState.status != HostStatus.IDLE) stopSharing()
                onBack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "ফিরে যান")
            }
            Text("হোস্ট", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (uiState.status) {
            HostStatus.IDLE -> {
                Text(
                    "শুরু করার আগে এই ফোনের হটস্পট চালু করুন এবং অন্য ফোনটিকে সেই হটস্পটে যুক্ত করুন।",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("পেয়ারিং কোড", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = uiState.pairingCode,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = projectionManager?.createScreenCaptureIntent()
                        if (intent != null) {
                            viewModel.setWaitingPermission()
                            launcher.launch(intent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("শেয়ারিং শুরু করুন")
                }
            }

            HostStatus.WAITING_PERMISSION -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("অনুমতি নিশ্চিত করা হচ্ছে...")
            }

            HostStatus.SHARING, HostStatus.CLIENT_CONNECTED -> {
                Text(
                    text = if (uiState.status == HostStatus.CLIENT_CONNECTED)
                        "ভিউয়ার কানেক্টেড — স্ক্রিন শেয়ার হচ্ছে"
                    else
                        "অপেক্ষা করা হচ্ছে — ভিউয়ার ফোনে নিচের কোডটি দিন",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("পেয়ারিং কোড", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.pairingCode,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.pairingCode)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "কপি করুন")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (uiState.localIps.isNotEmpty()) {
                    Text(
                        "এই ফোনের IP ঠিকানা (প্রয়োজনে ভিউয়ারে ম্যানুয়ালি লিখুন):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    uiState.localIps.forEach { ip ->
                        Text(ip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { stopSharing() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("শেয়ারিং বন্ধ করুন")
                }
            }

            HostStatus.ERROR -> {
                Text(
                    text = uiState.errorMessage ?: "একটি সমস্যা হয়েছে",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.onSharingStopped() }) {
                    Text("আবার চেষ্টা করুন")
                }
            }
        }
    }
}
