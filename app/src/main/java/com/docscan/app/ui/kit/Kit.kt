package com.docscan.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.app.data.DocKind
import com.docscan.app.ui.theme.Brand
import com.docscan.app.ui.theme.DocxelsTheme
import com.docscan.app.ui.theme.Tint

/** A pastel rounded square holding one icon: the building block of tool tiles and list rows. */
@Composable
fun TintIcon(icon: ImageVector, tint: Tint, modifier: Modifier = Modifier, box: Dp = 48.dp, iconSize: Dp = 24.dp) {
    Box(
        modifier.size(box).clip(RoundedCornerShape(box * 0.32f)).background(tint.container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint.strong, modifier = Modifier.size(iconSize)) }
}

/** The "PDF / DOCX / SCAN" badge that tells document kinds apart in lists. */
@Composable
fun DocBadge(kind: DocKind, modifier: Modifier = Modifier) {
    val t = DocxelsTheme.tints
    val (tint, label) = when (kind) {
        DocKind.SCAN -> t.green to "SCAN"
        DocKind.DOCX -> t.blue to "DOCX"
    }
    Box(
        modifier.size(width = 46.dp, height = 52.dp).clip(RoundedCornerShape(14.dp)).background(tint.container),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = tint.strong, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Full-width pill button filled with a brand gradient. */
@Composable
fun GradientButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = Brand.Gradient,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(brush)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(end = 8.dp))
        Text(label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

/** Soft pastel pill button (secondary action). */
@Composable
fun SoftButton(label: String, tint: Tint, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(tint.container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint.strong, modifier = Modifier.padding(end = 8.dp))
        Text(label, color = tint.strong, style = MaterialTheme.typography.titleMedium)
    }
}

/** A selectable chip in the filter row. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** White rounded card used for list rows and groups. */
@Composable
fun Modifier.docCard(corner: Dp = 20.dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corner))

/** A tool tile: pastel icon above a short label. [soon] shows that the tool is planned but not built yet. */
@Composable
fun ToolTile(
    icon: ImageVector,
    label: String,
    tint: Tint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    soon: Boolean = false,
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            TintIcon(icon, tint, box = 56.dp, iconSize = 26.dp, modifier = Modifier.alpha(if (soon) 0.55f else 1f))
            if (soon) {
                Text(
                    "Soon",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Small coloured section label ("Scan", "PDF", …). */
@Composable
fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp))
}
