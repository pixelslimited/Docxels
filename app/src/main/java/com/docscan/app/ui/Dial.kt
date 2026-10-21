package com.docscan.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the phone dialer with [number] filled in. No permission is needed, and nothing is called until the person presses call. */
fun dialNumber(context: Context, number: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
