package app.flashcards.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: ThemeMode.System,
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object { const val KEY = "theme" }
}
