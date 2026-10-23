package com.docscan.app.ui.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.docscan.app.cloud.Cloud
import com.docscan.app.cloud.ServiceState
import com.docscan.app.cloud.ServiceStatus
import kotlinx.coroutines.delay

/**
 * A bar across the top that tells people when Docxels' online services cannot be reached (instead of leaving them with
 * unexplained errors), and turns into a refresh button once they are back. While the services are down it checks again
 * every half minute, only while the app is open; the check sends no personal data.
 */
@Composable
fun ServiceBanner(cloud: Cloud, modifier: Modifier = Modifier) {
    val state by ServiceStatus.state.collectAsState()
    LaunchedEffect(state) {
        while (state == ServiceState.DOWN) {
            delay(30_000)
            cloud.probe()
        }
    }
    when (state) {
        ServiceState.OK -> Unit
        ServiceState.DOWN -> Row(
            modifier.fillMaxWidth().background(Color(0xFFB45309)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Docxels online services can't be reached right now: your account, the writing assistant and Pro. Scanning, editing and PDFs still work.",
                color = Color.White, style = MaterialTheme.typography.bodyMedium,
            )
        }
        ServiceState.BACK -> Row(
            modifier.fillMaxWidth().background(Color(0xFF15803D)).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Online services are back.", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { ServiceStatus.acknowledged() }) { Text("Refresh", color = Color.White) }
        }
    }
}
