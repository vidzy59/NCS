package com.ncs.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val contentType: ContentType,
    val platform: Platform,
    val brief: String,
    val promptPackageJson: String,
    val projectJson: String? = null,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
    val renderOutputPath: String? = null,
    val thumbnailPath: String? = null,
    val rating: Int? = null,
    val isTemplate: Boolean = false,
    val templateName: String? = null
) {
    val outputDir: java.io.File
        get() = java.io.File(renderOutputPath ?: "").parentFile ?: java.io.File(android.content.Context?.cacheDir ?: ".")
}

@Serializable
enum class ContentType { NATURE, LYRIC_VIBE, HYBRID }

@Serializable
enum class ProjectStatus { DRAFT, RESEARCHING, GENERATING_ASSETS, EDITING, RENDERING, COMPLETED, FAILED }
