package dev.vclient.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KProperty

/**
 * Base class for all module settings. A setting is:
 *  - reactive (backed by a StateFlow, so Compose re-renders instantly),
 *  - observable (onChange listeners -> profile auto-save),
 *  - serializable (encodeJson/decodeJson -> profile.json),
 *  - resettable to its default.
 *
 * Settings also implement Kotlin property delegation, so modules can write
 * `private val gap by settingOf(IntSetting("gap", "Gap", 4, 0, 20))`.
 */
sealed class Setting<T>(
    val id: String,
    val name: String,
    val description: String,
    val default: T,
) {
    private val _state = MutableStateFlow(default)
    val state: StateFlow<T> get() = _state

    var value: T
        get() = _state.value
        set(next) {
            if (next == _state.value) return
            _state.value = next
            val snapshot = listeners.toList()
            snapshot.forEach { runCatching { it(next) } }
        }

    private val listeners = mutableListOf<(T) -> Unit>()

    /** Registers a listener; returns an unsubscribe function. */
    fun onChange(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun reset() {
        value = default
    }

    // --- property delegation -------------------------------------------------
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, next: T) {
        value = next
    }
}

class BoolSetting(
    id: String,
    name: String,
    default: Boolean,
    description: String = "",
) : Setting<Boolean>(id, name, description, default)

class IntSetting(
    id: String,
    name: String,
    default: Int,
    val min: Int,
    val max: Int,
    val step: Int = 1,
    val suffix: String = "",
    description: String = "",
) : Setting<Int>(id, name, description, default) {
    init { require(min <= max) { "min > max for setting $id" } }
}

class FloatSetting(
    id: String,
    name: String,
    default: Float,
    val min: Float,
    val max: Float,
    val step: Float = 0.1f,
    val suffix: String = "",
    val decimals: Int = 1,
    description: String = "",
) : Setting<Float>(id, name, description, default) {
    init { require(min <= max) { "min > max for setting $id" } }
}

/** Color as packed ARGB int (e.g. 0xFF8B5CF6.toInt()). */
class ColorSetting(
    id: String,
    name: String,
    defaultArgb: Int,
    description: String = "",
) : Setting<Int>(id, name, description, defaultArgb)

/** One-of-many option, stored by option name for forward compatibility. */
class ModeSetting(
    id: String,
    name: String,
    val options: List<String>,
    default: String = options.first(),
    description: String = "",
) : Setting<String>(id, name, description, default) {
    init { require(options.contains(default)) { "default not in options for $id" } }
    val index: Int get() = options.indexOf(value).coerceAtLeast(0)
}

class TextSetting(
    id: String,
    name: String,
    default: String = "",
    val hint: String = "",
    description: String = "",
) : Setting<String>(id, name, description, default)

/**
 * Ordered collection of settings owned by a module. The profile serializer
 * walks [all]; the UI walks [all] to render setting rows automatically.
 */
class SettingContainer {
    private val items = mutableListOf<Setting<*>>()

    val all: List<Setting<*>> get() = items

    fun <T> add(setting: Setting<T>): Setting<T> {
        check(byId(setting.id) == null) { "Duplicate setting id: ${setting.id}" }
        items.add(setting)
        return setting
    }

    fun byId(id: String): Setting<*>? = items.firstOrNull { it.id == id }

    fun resetAll() = items.forEach { it.reset() }
}

// --- JSON (de)serialization, used by the profile system ----------------------

fun Setting<*>.encodeJson(): JsonElement = when (this) {
    is BoolSetting -> JsonPrimitive(value)
    is IntSetting -> JsonPrimitive(value)
    is FloatSetting -> JsonPrimitive(value)
    is ColorSetting -> JsonPrimitive("#%08X".format(value))
    is ModeSetting -> JsonPrimitive(value)
    is TextSetting -> JsonPrimitive(value)
}

/** Applies a persisted value; returns false when the value was invalid/unknown. */
fun Setting<*>.decodeJson(element: JsonElement): Boolean {
    runCatching {
        when (this) {
            is BoolSetting -> (element as? JsonPrimitive)?.content?.toBooleanStrictOrNull()?.let { value = it }
            is IntSetting -> (element as? JsonPrimitive)?.content?.toIntOrNull()?.let { value = it.coerceIn(min, max) }
            is FloatSetting -> (element as? JsonPrimitive)?.content?.toFloatOrNull()?.let { value = it.coerceIn(min, max) }
            is ColorSetting -> (element as? JsonPrimitive)?.content?.let {
                value = parseColorOrNull(it) ?: return false
            }
            is ModeSetting -> (element as? JsonPrimitive)?.content?.let {
                if (options.contains(it)) value = it else return false
            }
            is TextSetting -> (element as? JsonPrimitive)?.content?.let { value = it }
        }
        return true
    }
    return false
}

fun parseColorOrNull(hex: String): Int? {
    val s = hex.removePrefix("#")
    return when (s.length) {
        6 -> runCatching { (0xFF000000L or s.toLong(16)).toInt() }.getOrNull()
        8 -> runCatching { s.toLong(16).toInt() }.getOrNull()
        else -> null
    }
}
