package dev.vclient.core.config

import android.content.Context
import android.net.Uri
import dev.vclient.core.AppSettings
import dev.vclient.core.isolation.IsolatedPaths
import dev.vclient.core.logging.VLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Owns the isolated profile store: <isolated>/profiles/<name>/profile.json.
 *
 * Responsibilities: list / create / rename / delete / activate profiles,
 * debounced auto-save of the live session, SAF export & import, and the
 * waypoint list of the active profile.
 *
 * Pure storage layer — it never touches module objects. [ProfileSync] (wired
 * by VClientCore) converts between modules and DTOs.
 */
class ProfileManager(
    private val context: Context,
    private val paths: IsolatedPaths,
    private val logger: VLogger,
    private val json: Json,
) {

    /** Wired by VClientCore: returns the live session captured into a ProfileData. */
    var onAutoCapture: (() -> ProfileData)? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSave: Runnable? = null

    private val _activeName = MutableStateFlow(AppSettings.DEFAULT_PROFILE)
    val activeName: StateFlow<String> get() = _activeName

    private val _activeData = MutableStateFlow(newEmpty(AppSettings.DEFAULT_PROFILE))
    val activeData: StateFlow<ProfileData> get() = _activeData

    private val _profileList = MutableStateFlow<List<ProfileMeta>>(emptyList())
    val profileList: StateFlow<List<ProfileMeta>> get() = _profileList

    // --- lifecycle -------------------------------------------------------------

    /** Loads the persisted active profile (creating Default on first run). */
    fun loadInitial(settings: AppSettings): ProfileData {
        refreshList()
        val wanted = settings.activeProfileName
        val data = loadData(wanted) ?: loadData(AppSettings.DEFAULT_PROFILE)
        val initial = data ?: newEmpty(AppSettings.DEFAULT_PROFILE).also { saveData(it) }
        _activeName.value = initial.name
        _activeData.value = initial
        settings.activeProfileName = initial.name
        logger.i(TAG, "Loaded profile '${initial.name}' (${initial.modules.size} modules)")
        return initial
    }

    // --- queries ---------------------------------------------------------------

    fun exists(name: String): Boolean = fileFor(name).isFile

    fun loadData(name: String): ProfileData? {
        val text = IsolatedPaths.readTextOrNull(fileFor(name)) ?: return null
        return runCatching { json.decodeFromString(ProfileData.serializer(), text) }
            .onFailure { logger.e(TAG, "Failed to parse profile '$name'", it) }
            .getOrNull()
            ?.copy(name = sanitize(name))
    }

    fun saveData(data: ProfileData): ProfileData {
        val stamped = data.copy(
            name = sanitize(data.name.ifBlank { _activeName.value }),
            modifiedAtMs = System.currentTimeMillis(),
        )
        val dir = dirFor(stamped.name).also { if (!it.exists()) it.mkdirs() }
        IsolatedPaths.atomicWrite(fileFor(stamped.name), json.encodeToString(ProfileData.serializer(), stamped))
        if (_activeName.value == stamped.name) _activeData.value = stamped
        refreshList()
        return stamped
    }

    fun activate(name: String): ProfileData? {
        flush()
        val data = loadData(name) ?: return null
        _activeName.value = data.name
        _activeData.value = data
        logger.i(TAG, "Activated profile '${data.name}'")
        return data
    }

    fun create(name: String, copyFrom: String? = null): Boolean {
        val clean = sanitize(name)
        if (clean.isBlank() || exists(clean)) return false
        val base = copyFrom?.let { loadData(it) }
        val created = (base ?: newEmpty(clean)).copy(
            name = clean,
            createdAtMs = System.currentTimeMillis(),
            modifiedAtMs = System.currentTimeMillis(),
        )
        saveData(created)
        return true
    }

    fun delete(name: String): Boolean {
        if (name == _activeName.value) return false // never delete the active profile
        val dir = dirFor(name)
        val ok = dir.deleteRecursively()
        refreshList()
        return ok
    }

    fun rename(oldName: String, newName: String): Boolean {
        val clean = sanitize(newName)
        if (clean.isBlank() || exists(clean) || !exists(oldName)) return false
        val wasActive = oldName == _activeName.value
        val data = loadData(oldName) ?: return false
        dirFor(oldName).deleteRecursively()
        saveData(data.copy(name = clean))
        if (wasActive) {
            _activeName.value = clean
            _activeData.value = loadData(clean) ?: data
        }
        return true
    }

    // --- auto-save ---------------------------------------------------------------

    /** Schedules a debounced capture+save of the live session. */
    fun markDirty() {
        pendingSave?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            pendingSave = null
            runCatching {
                val captured = onAutoCapture?.invoke() ?: _activeData.value
                saveData(captured)
            }.onFailure { logger.e(TAG, "Auto-save failed", it) }
        }
        pendingSave = task
        handler.postDelayed(task, SAVE_DEBOUNCE_MS)
    }

    /** Persists immediately if a save is pending. */
    fun flush() {
        pendingSave?.let {
            handler.removeCallbacks(it)
            it.run()
        }
    }

    // --- waypoints (stored on the active profile) ---------------------------------

    fun upsertWaypoint(waypoint: WaypointDto) {
        _activeData.update { data ->
            val list = data.waypoints.filterNot { it.id == waypoint.id } + waypoint
            data.copy(waypoints = list.sortedBy { it.name }, modifiedAtMs = System.currentTimeMillis())
        }
        markDirty()
    }

    fun removeWaypoint(id: String) {
        _activeData.update { it.copy(waypoints = it.waypoints.filterNot { w -> w.id == id }) }
        markDirty()
    }

    // --- export / import (SAF) -----------------------------------------------------

    fun export(name: String, uri: Uri): Boolean = runCatching {
        val data = loadData(name) ?: error("Profile not found: $name")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(ProfileData.serializer(), data).toByteArray())
        } ?: error("Cannot open output stream")
        true
    }.getOrElse {
        logger.e(TAG, "Export failed", it)
        false
    }

    /** Returns the name of the imported profile, or null on failure. */
    fun import(uri: Uri): String? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Cannot open input stream")
        val parsed = json.decodeFromString(ProfileData.serializer(), text)
        require(parsed.formatVersion <= ProfileData.FORMAT_VERSION) { "Newer profile format: ${parsed.formatVersion}" }
        var name = sanitize(parsed.name.ifBlank { "Imported" })
        if (exists(name)) name = "$name ${System.currentTimeMillis() % 1000}"
        saveData(parsed.copy(name = name))
        name
    }.getOrElse {
        logger.e(TAG, "Import failed", it)
        null
    }

    // --- internals -------------------------------------------------------------------

    private fun refreshList() {
        _profileList.value = paths.profiles.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val file = File(dir, FILE_NAME)
                if (!file.isFile) return@mapNotNull null
                val meta = loadData(dir.name) ?: return@mapNotNull null
                ProfileMeta(meta.name, meta.modifiedAtMs, meta.gameVersion)
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun dirFor(name: String) = File(paths.profiles, sanitize(name))
    private fun fileFor(name: String) = File(dirFor(name), FILE_NAME)

    private fun newEmpty(name: String) = ProfileData(
        name = name,
        createdAtMs = System.currentTimeMillis(),
        modules = emptyList(),
        hud = emptyList(),
    )

    companion object {
        const val TAG = "Profiles"
        const val FILE_NAME = "profile.json"
        const val SAVE_DEBOUNCE_MS = 600L

        /** Keeps names filesystem-safe and display-friendly. */
        fun sanitize(name: String): String =
            name.trim().replace(Regex("[^A-Za-z0-9 _\\-.]"), "").take(48)

        fun newWaypointId(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
