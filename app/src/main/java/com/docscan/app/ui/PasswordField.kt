package com.docscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/** A password box with an eye button to show or hide what is typed. Used wherever Docxels asks for a password. */
@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, modifier = modifier, singleLine = true, label = { Text(label) },
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (shown) "Hide password" else "Show password")
            }
        },
    )
}

/** The rules for a new Docxels password, in one place so the checklist and the buttons agree. */
object PasswordPolicy {
    fun checks(p: String): List<Pair<String, Boolean>> = listOf(
        "10 or more characters" to (p.length >= 10),
        "An upper case letter" to p.any { it.isUpperCase() },
        "A lower case letter" to p.any { it.isLowerCase() },
        "A digit" to p.any { it.isDigit() },
    )

    fun ok(p: String) = checks(p).all { it.second }

    /** 0 (nothing typed) to 4: the four rules met, with the last step needing a longer password or a symbol as well. */
    fun level(p: String): Int {
        if (p.isEmpty()) return 0
        val met = checks(p).count { it.second }
        return if (met == 4 && (p.length >= 14 || p.any { !it.isLetterOrDigit() })) 4 else minOf(met, 3)
    }
}

/** Strength bar and a checklist showing which rules a new password still misses. */
@Composable
fun PasswordStrength(password: String, modifier: Modifier = Modifier) {
    val level = PasswordPolicy.level(password)
    val names = listOf("Nothing typed yet", "Weak", "Fair", "Good", "Strong")
    val label = when { password.isEmpty() -> names[0]; level <= 1 -> "Weak"; level == 2 -> "Fair"; level == 3 -> "Good"; else -> "Strong" }
    val color = when { level <= 1 -> MaterialTheme.colorScheme.error; level == 2 -> androidx.compose.ui.graphics.Color(0xFFB45309); level == 3 -> MaterialTheme.colorScheme.primary; else -> androidx.compose.ui.graphics.Color(0xFF15803D) }
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 1..4) Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (password.isNotEmpty() && i <= maxOf(level, 1)) color else MaterialTheme.colorScheme.surfaceVariant))
        }
        Text("Password strength: $label", style = MaterialTheme.typography.labelLarge, color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else color)
        for ((rule, met) in PasswordPolicy.checks(password)) {
            Text((if (met) "\u2713  " else "\u25CB  ") + rule, style = MaterialTheme.typography.bodyMedium, color = if (met) androidx.compose.ui.graphics.Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
