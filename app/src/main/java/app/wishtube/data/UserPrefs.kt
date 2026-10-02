package app.wishtube.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** App-wide settings as observable flows. Stored locally in SharedPreferences. */
class UserPrefs(private val p: SharedPreferences) {
    val theme = MutableStateFlow(p.getString("theme", "system") ?: "system")
    val dynamicColor = MutableStateFlow(p.getBoolean("dynamic", false))
    val compact = MutableStateFlow(p.getBoolean("compact", false))
    val reduceMotion = MutableStateFlow(p.getBoolean("reduceMotion", false))
    val autoplay = MutableStateFlow(p.getBoolean("autoplay", true))
    val captions = MutableStateFlow(p.getBoolean("captions", false))
    val background = MutableStateFlow(p.getBoolean("background", true))
    val pip = MutableStateFlow(p.getBoolean("pip", true))
    val gestures = MutableStateFlow(p.getBoolean("gestures", true))
    val creatorUpdates = MutableStateFlow(p.getBoolean("creatorUpdates", false))
    val glassEffects = MutableStateFlow(p.getBoolean("glassEffects", true))
    /** 0 = auto, 1 = 1080p, 2 = 720p, 3 = 480p, 4 = 360p */
    val quality = MutableStateFlow(p.getInt("quality", 0))
    val speed = MutableStateFlow(p.getFloat("speed", 1f))
    val autoplayNext = MutableStateFlow(p.getBoolean("autoplayNext", false))
    val onboardingCompleted = MutableStateFlow(p.getBoolean("onboardingCompleted", false))
    val selectedInterests = MutableStateFlow(p.getStringSet("interests", emptySet()) ?: emptySet())
    val showMature = MutableStateFlow(p.getBoolean("showMature", false))

    fun set(key: String, flow: MutableStateFlow<Boolean>, v: Boolean) { p.edit().putBoolean(key, v).apply(); flow.value = v }
    fun setTheme(v: String) { p.edit().putString("theme", v).apply(); theme.value = v }
    fun setQuality(v: Int) { p.edit().putInt("quality", v).apply(); quality.value = v }
    fun setSpeed(v: Float) { p.edit().putFloat("speed", v).apply(); speed.value = v }
    fun setAutoplayNext(v: Boolean) { p.edit().putBoolean("autoplayNext", v).apply(); autoplayNext.value = v }
    fun setOnboardingCompleted(completed: Boolean) { p.edit().putBoolean("onboardingCompleted", completed).apply(); onboardingCompleted.value = completed }
    fun setInterests(interests: Set<String>) { p.edit().putStringSet("interests", interests).apply(); selectedInterests.value = interests }
    fun setShowMature(v: Boolean) { p.edit().putBoolean("showMature", v).apply(); showMature.value = v }
}
