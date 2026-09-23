package com.example.ui.components

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    fun formatSmartTimestamp(context: Context, timestamp: Long): String {
        if (timestamp <= 0L) return ""

        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isToday = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

        val isYesterday = run {
            val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
        }

        val isSameYear = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)

        return when {
            isToday -> {
                val timeFormat = DateFormat.getTimeFormat(context)
                timeFormat.format(Date(timestamp))
            }
            isYesterday -> "Yesterday"
            isSameYear -> {
                val formatter = SimpleDateFormat("d MMM", Locale.getDefault())
                formatter.format(Date(timestamp))
            }
            else -> {
                val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                formatter.format(Date(timestamp))
            }
        }
    }

    fun formatDetailedTimestamp(context: Context, timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val date = Date(timestamp)
        return "${dateFormat.format(date)} at ${timeFormat.format(date)}"
    }
}
