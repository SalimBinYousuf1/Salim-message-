package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs

@Composable
fun AvatarView(
    photoUri: String?,
    nameOrAddress: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalContext.current

    val avatarColor = remember(nameOrAddress) {
        getDeterministicAvatarColor(nameOrAddress)
    }

    val initials = remember(nameOrAddress) {
        extractInitials(nameOrAddress)
    }

    if (!photoUri.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photoUri)
                .crossfade(true)
                .build(),
            contentDescription = "Contact avatar for $nameOrAddress",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            if (initials.isNotBlank()) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    }
}

private fun extractInitials(nameOrAddress: String): String {
    val clean = nameOrAddress.trim()
    if (clean.isEmpty()) return ""

    // If pure phone number, return "" so it shows person icon
    if (clean.all { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }) {
        return ""
    }

    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> {
            "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        }
        parts.size == 1 -> {
            parts[0].take(2).uppercase()
        }
        else -> ""
    }
}

private fun getDeterministicAvatarColor(key: String): Color {
    val palette = listOf(
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFE53935), // Red
        Color(0xFF8E24AA), // Purple
        Color(0xFFFB8C00), // Orange
        Color(0xFF00ACC1), // Cyan
        Color(0xFF3949AB), // Indigo
        Color(0xFFD81B60), // Pink
        Color(0xFF00897B), // Teal
        Color(0xFF5E35B1)  // Deep Purple
    )
    val index = abs(key.hashCode()) % palette.size
    return palette[index]
}
