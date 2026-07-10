package com.eventmanager.app.ui.desktop

enum class AdminNavLayout(val value: String) {
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromString(value: String?): AdminNavLayout =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: LEFT
    }
}
