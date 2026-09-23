package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MessageItem
import com.example.data.model.MessageStatus
import java.util.regex.Pattern

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageItem,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isIncoming = message.isIncoming

    val bubbleShape = if (isIncoming) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
            bottomStart = 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp
        )
    }

    val bubbleColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        isIncoming -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isIncoming -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    val subTextColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        isIncoming -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    }

    // Pattern for URLs and Phone numbers
    val urlPattern = remember { Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+") }
    val phonePattern = remember { Pattern.compile("(\\+?[0-9]{1,4}[-\\s]?)?(\\(?[0-9]{3}\\)?[-\\s]?)?[0-9]{3}[-\\s]?[0-9]{4}") }

    val annotatedText = remember(message.body, textColor) {
        buildAnnotatedString {
            append(message.body)

            val urlMatcher = urlPattern.matcher(message.body)
            while (urlMatcher.find()) {
                val start = urlMatcher.start()
                val end = urlMatcher.end()
                addStyle(
                    style = SpanStyle(
                        color = if (isIncoming) Color(0xFF1E88E5) else Color(0xFFBBDEFB),
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = message.body.substring(start, end),
                    start = start,
                    end = end
                )
            }

            val phoneMatcher = phonePattern.matcher(message.body)
            while (phoneMatcher.find()) {
                val start = phoneMatcher.start()
                val end = phoneMatcher.end()
                addStyle(
                    style = SpanStyle(
                        color = if (isIncoming) Color(0xFF1E88E5) else Color(0xFFBBDEFB),
                        textDecoration = TextDecoration.Underline
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = "PHONE",
                    annotation = message.body.substring(start, end),
                    start = start,
                    end = end
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .testTag("message_bubble_${message.id}"),
        contentAlignment = if (isIncoming) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = if (isIncoming) Alignment.Start else Alignment.End
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                tonalElevation = if (isIncoming) 1.dp else 2.dp,
                modifier = Modifier
                    .widthIn(min = 64.dp, max = 310.dp)
                    .clip(bubbleShape)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Attachment preview if present
                    if (!message.attachmentUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(message.attachmentUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Message attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (message.body.isNotBlank()) {
                        ClickableText(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            onClick = { offset ->
                                val urlAnnotation = annotatedText.getStringAnnotations(
                                    tag = "URL",
                                    start = offset,
                                    end = offset
                                ).firstOrNull()

                                if (urlAnnotation != null) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlAnnotation.item))
                                        context.startActivity(intent)
                                        return@ClickableText
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }

                                val phoneAnnotation = annotatedText.getStringAnnotations(
                                    tag = "PHONE",
                                    start = offset,
                                    end = offset
                                ).firstOrNull()

                                if (phoneAnnotation != null) {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phoneAnnotation.item}"))
                                        context.startActivity(intent)
                                        return@ClickableText
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }

                                onClick()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = DateTimeUtils.formatSmartTimestamp(context, message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor,
                            fontSize = 10.sp
                        )

                        if (!isIncoming) {
                            when (message.status) {
                                MessageStatus.SENDING -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Sending",
                                        tint = subTextColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                MessageStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sent",
                                        tint = subTextColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageStatus.DELIVERED -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = subTextColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Failed",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // Failed sending indicator with Retry action
            if (!isIncoming && message.status == MessageStatus.FAILED) {
                Spacer(modifier = Modifier.height(2.dp))
                AssistChip(
                    onClick = onRetryClick,
                    label = { Text("Failed • Tap to retry", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}
