package com.jeal.smartwidget.core

object Format {
    fun duration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 1 -> "<1 min"
            min < 60 -> "$min min"
            else -> "${min / 60} h ${min % 60} min"
        }
    }

    fun shortDuration(ms: Long): String {
        val min = ms / 60_000
        return if (min < 60) "${min}m" else "${min / 60}h"
    }
}
