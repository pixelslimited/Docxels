package com.docscan.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docscan.app.R

private const val COMPANY_SITE = "https://pixelslimited.ng"
private const val APP_SITE = "https://docxels.pixelslimited.ng"
private const val SUPPORT = "docxels@pixelslimited.ng"


@Composable
fun AboutPanel(context: Context) {
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val version = versionName(context)
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(painterResource(R.drawable.pixels_logo), contentDescription = "Pixels Limited", modifier = Modifier.height(96.dp))
        Text("Pixels Limited", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Text("Makers of Docxels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Version $version · build ${version.substringAfterLast('.')}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(onClick = { open(COMPANY_SITE) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Pixels Limited website") }
        OutlinedButton(onClick = { open(APP_SITE) }, modifier = Modifier.fillMaxWidth()) { Text("Docxels website") }
        OutlinedButton(onClick = { open("mailto:$SUPPORT") }, modifier = Modifier.fillMaxWidth()) { Text("Contact support") }
        OutlinedButton(onClick = { open("$APP_SITE/privacy.html") }, modifier = Modifier.fillMaxWidth()) { Text("Privacy policy") }
        OutlinedButton(onClick = { open("$APP_SITE/terms.html") }, modifier = Modifier.fillMaxWidth()) { Text("Terms of use") }
    }
    InfoRow(
        "Privacy",
        "Scanning, text recognition and exports all run on this phone. Files you export or share leave the app only where you send them. The only network use is checking for and downloading app updates when you ask.",
    )
    InfoRow(
        "Open-source notices",
        "Interface font: Inter © The Inter Project Authors, SIL Open Font License 1.1. PDF tools: PdfBox-Android (Apache 2.0). Text recognition: Google ML Kit. Image processing: OpenCV (Apache 2.0).",
    )
    Text(
        "© Pixels Limited",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}
