package com.gravityos.assistant.core

import android.os.Build
import android.os.Environment
import java.io.File

data class ModelFolderStatus(
    val title: String,
    val folderName: String,
    val exists: Boolean,
    val fileCount: Int,
    val relativePath: String
)

class ModelStorageManager {

    private val baseDir: File
        get() = File(Environment.getExternalStorageDirectory(), "GravityOS/models")

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun ensureModelDirectories(): Boolean {
        if (!hasAllFilesAccess()) return false
        return try {
            val subFolders = listOf("wakeword", "stt", "tts", "llm")
            subFolders.forEach { name ->
                val dir = File(baseDir, name)
                if (!dir.exists()) dir.mkdirs()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun inspectModelFolders(): List<ModelFolderStatus> {
        val specs = listOf(
            "Wake Word (Hey Gravity)" to "wakeword",
            "Offline Speech Recognition (STT)" to "stt",
            "Offline Neural Voice (TTS)" to "tts",
            "Offline Local AI Model (LLM)" to "llm"
        )

        return specs.map { (title, folder) ->
            val dir = File(baseDir, folder)
            val files = dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList()
            ModelFolderStatus(
                title = title,
                folderName = folder,
                exists = dir.exists(),
                fileCount = files.size,
                relativePath = "Internal Storage/GravityOS/models/$folder"
            )
        }
    }
}
