package com.ncs.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ncs.model.Project
import com.ncs.model.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.reflect.Type
import java.time.Instant

// ===== Room Database =====
@Database(entities = [Project::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project)

    @Query("SELECT * FROM projects WHERE isTemplate = 1 ORDER BY updatedAt DESC")
    suspend fun getTemplates(): List<Project>

    @Query("SELECT * FROM projects WHERE isTemplate = 0 ORDER BY updatedAt DESC")
    suspend fun getRecentProjects(limit: Int): List<Project>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): Project?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM projects WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getByStatus(status: String): List<Project>
}

// ===== Preferences Data Class =====
data class Preferences(
    val contentTypes: List<String> = listOf("nature", "lyric_vibe"),
    val preferredGenres: Map<String, List<String>> = mapOf(
        "nature" to listOf("ambient", "cinematic", "chillout"),
        "lyric_vibe" to listOf("indie_pop", "lofi", "acoustic", "slowed_reverb", "sped_up")
    ),
    val bpmRanges: Map<String, IntRange> = mapOf(
        "nature" to 60..85,
        "lyric_vibe" to 70..140
    ),
    val colorPalettes: Map<String, String> = mapOf(
        "nature" to "warm_golden_hour",
        "lyric_vibe" to "moody_teal_orange"
    ),
    val lensPreferences: Map<String, List<Int>> = mapOf(
        "nature" to listOf(24, 35, 50, 85),
        "lyric_vibe" to listOf(35, 50, 85)
    ),
    val cameraMovements: Map<String, List<String>> = mapOf(
        "nature" to listOf("slow_dolly", "gentle_pan", "static"),
        "lyric_vibe" to listOf("beat_snap_zoom", "whip_pan", "glide")
    ),
    val pacingRules: Map<String, String> = mapOf(
        "nature" to "variable_3_12s",
        "lyric_vibe" to "beat_synced_2_4s"
    ),
    val audioTreatments: Map<String, String> = mapOf(
        "nature" to "duck_ambience_under_vo",
        "lyric_vibe" to "vocal_forward_sidechain_music"
    ),
    val qualityTargets: QualityTargets = QualityTargets(),
    val avoidPatterns: List<AvoidPattern> = emptyList(),
    val lutAdjustments: Map<String, Float> = emptyMap()
)

data class QualityTargets(
    val loudnessLUFS: Double = -14.0,
    val maxPeakDB: Double = -1.0,
    val bitrate4K: String = "50M",
    val fps: Int = 60
)

data class AvoidPattern(
    val projectId: String,
    val reason: String,
    val timestamp: Long
)

// ===== Memory Store Implementation =====
class MemoryStore(private val context: Context) {

    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "ncs-db"
    ).fallbackToDestructiveMigration().build()

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context, "ncs_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()
    private val prefsType: Type = object : TypeToken<Preferences>() {}.type
    private val historyType: Type = object : TypeToken<List<HistoryEntry>>() {}.type

    // --- Preferences ---
    suspend fun getPreferences(): Preferences = withContext(Dispatchers.IO) {
        val json = encryptedPrefs.getString("preferences", "") ?: return@withContext defaultPreferences()
        try { gson.fromJson(json, prefsType) } catch (e: Exception) { defaultPreferences() }
    }

    suspend fun updatePreferences(block: (Preferences) -> Preferences) = withContext(Dispatchers.IO) {
        val current = getPreferences()
        val updated = block(current)
        encryptedPrefs.edit().putString("preferences", gson.toJson(updated)).apply()
        updated
    }

    private fun defaultPreferences(): Preferences = Preferences()

    // --- History ---
    data class HistoryEntry(
        val timestamp: Long = Instant.now().toEpochMilli(),
        val action: String,
        val projectId: String? = null,
        val contentType: String? = null,
        val promptPackageJson: String? = null,
        val projectJson: String? = null,
        val rating: Int? = null,
        val notes: String? = null,
        val delta: Map<String, Any>? = null
    )

    suspend fun appendHistory(entry: HistoryEntry) = withContext(Dispatchers.IO) {
        val json = encryptedPrefs.getString("history", "[]") ?: "[]"
        val list = mutableListOf<HistoryEntry>().apply { addAll(gson.fromJson(json, historyType)) }
        list.add(entry)
        if (list.size > 500) list.removeRange(0, list.size - 500)
        encryptedPrefs.edit().putString("history", gson.toJson(list)).apply()
    }

    suspend fun getHistory(limit: Int = 50): List<HistoryEntry> = withContext(Dispatchers.IO) {
        val json = encryptedPrefs.getString("history", "[]") ?: "[]"
        gson.fromJson(json, historyType).reversed().take(limit).toList()
    }

    // --- Project Operations ---
    suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
        db.projectDao().insert(project.copy(updatedAt = Instant.now().toEpochMilli()))
    }

    suspend fun getProject(id: String): Project? = withContext(Dispatchers.IO) {
        db.projectDao().getById(id)
    }

    suspend fun getRecentProjects(limit: Int = 20): List<Project> = withContext(Dispatchers.IO) {
        db.projectDao().getRecentProjects(limit)
    }

    suspend fun getTemplates(): List<Project> = withContext(Dispatchers.IO) {
        db.projectDao().getTemplates()
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        db.projectDao().delete(id)
    }

    // --- Learning from User Actions ---
    suspend fun onTemplateSaved(project: Project) = withContext(Dispatchers.IO) {
        updatePreferences { prefs ->
            val ct = project.contentType.name.lowercase()
            val analysis = gson.fromJson(project.promptPackageJson, com.ncs.model.PromptPackage::class.java)
            val visual = analysis.visual
            val audio = analysis.audio

            val newPalettes = prefs.colorPalettes.toMutableMap()
            if (visual.luts.isNotEmpty()) newPalettes[ct] = visual.luts.first()
            
            val newLensPrefs = prefs.lensPreferences.toMutableMap()
            val currentLens = newLensPrefs.getOrDefault(ct, mutableListOf())
            val lensMm = extractMm(visual.camera)
            if (lensMm > 0) {
                val updated = (currentLens + lensMm).distinct().takeLast(20)
                newLensPrefs[ct] = updated
            }

            val newCamMov = prefs.cameraMovements.toMutableMap()
            val currentMov = newCamMov.getOrDefault(ct, mutableListOf())
            val mov = extractMovement(visual.camera)
            if (mov.isNotBlank()) {
                val updated = (currentMov + mov).distinct().takeLast(20)
                newCamMov[ct] = updated
            }

            val newPacing = prefs.pacingRules.toMutableMap()
            newPacing[ct] = analysis.pacing

            val newBpm = prefs.bpmRanges.toMutableMap()
            audio.bpm?.let { b ->
                val current = newBpm[ct] ?: (60..140)
                val newMin = (current.start * 0.7 + b * 0.3).toInt()
                val newMax = (current.endInclusive * 0.7 + b * 0.3).toInt()
                newBpm[ct] = newMin..newMax
            }

            prefs.copy(
                colorPalettes = newPalettes,
                lensPreferences = newLensPrefs,
                cameraMovements = newCamMov,
                pacingRules = newPacing,
                bpmRanges = newBpm
            )
        }
        appendHistory(HistoryEntry(action = "template_save", projectId = project.id, contentType = project.contentType.name))
    }

    suspend fun onRating(projectId: String, rating: Int, notes: String = "") = withContext(Dispatchers.IO) {
        updatePreferences { prefs ->
            val project = getProject(projectId) ?: return@updatePreferences prefs
            if (rating < 3) {
                val newAvoid = prefs.avoidPatterns + AvoidPattern(projectId, notes.isBlank() ? "low_rating" : notes, Instant.now().toEpochMilli())
                return@updatePreferences prefs.copy(avoidPatterns = newAvoid.takeLast(50))
            }
            prefs
        }
        appendHistory(HistoryEntry(action = "rating", projectId = projectId, rating = rating, notes = notes))
    }

    suspend fun onReRender(projectId: String, delta: Map<String, Any>) = withContext(Dispatchers.IO) {
        updatePreferences { prefs ->
            val project = getProject(projectId) ?: return@updatePreferences prefs
            val ct = project.contentType.name.lowercase()
            
            val newBpm = prefs.bpmRanges.toMutableMap()
            delta["bpm"]?.let { b ->
                val current = newBpm[ct] ?: (60..140)
                val newMin = (current.start * 0.8 + (b as Number).toInt() * 0.2).toInt()
                val newMax = (current.endInclusive * 0.8 + (b as Number).toInt() * 0.2).toInt()
                newBpm[ct] = newMin..newMax
            }
            
            val newLutAdj = prefs.lutAdjustments.toMutableMap()
            delta["gain"]?.let { g ->
                newLutAdj["gain"] = (newLutAdj.getOrDefault("gain", 0f) * 0.8 + (g as Number).toFloat() * 0.2)
            }
            
            prefs.copy(bpmRanges = newBpm, lutAdjustments = newLutAdj)
        }
        appendHistory(HistoryEntry(action = "delta", projectId = projectId, delta = delta))
    }

    // --- Helpers ---
    private fun extractMm(cameraStr: String): Int {
        val regex = """(\d{2,3})\s*mm""".toRegex()
        return regex.find(cameraStr)?.groupValues?.get(1)?.toIntOrNull() ?: 35
    }

    private fun extractMovement(cameraStr: String): String {
        val movements = listOf("dolly", "pan", "tilt", "glide", "zoom", "whip", "static", "crane", "tracking")
        return movements.find { cameraStr.lowercase().contains(it) } ?: ""
    }
}
