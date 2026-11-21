package com.jeal.switchdriver

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Green = Color(0xFF2EE6A6)
val Amber = Color(0xFFFFC857)
val Red = Color(0xFFFF5C5C)

@Composable
fun SDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green, onPrimary = Color(0xFF0E1116),
            background = Color(0xFF0E1116), onBackground = Color(0xFFE8EDF3),
            surface = Color(0xFF141922), onSurface = Color(0xFFE8EDF3),
            surfaceVariant = Color(0xFF1C2330), onSurfaceVariant = Color(0xFFB8C2CF),
            secondaryContainer = Color(0xFF223046), onSecondaryContainer = Color(0xFFE8EDF3),
            error = Red, outline = Color(0xFF3A4556),
        ),
        content = content,
    )
}

@Composable
fun Screen(modifier: Modifier, title: String, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
    ) {
        item { Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
fun Dot(color: Color) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) }

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { Catalog.icon(ctx, pkg, 96) }
    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("?") }
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); if (sub != null) Muted(sub) }
        Switch(checked, onChange, enabled = enabled)
    }
}
