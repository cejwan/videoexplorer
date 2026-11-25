package ir.videoexplorer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("video_explorer", Context.MODE_PRIVATE)

    fun folders(): List<Uri> =
        prefs.getStringSet("folders", emptySet()).orEmpty().mapNotNull {
            runCatching { Uri.parse(it) }.getOrNull()
        }

    fun addFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        val updated = folders().map { it.toString() }.toMutableSet()
        updated.add(uri.toString())
        prefs.edit().putStringSet("folders", updated).apply()
    }

    fun removeFolder(uri: Uri) {
        val updated = folders().map { it.toString() }.toMutableSet()
        updated.remove(uri.toString())
        prefs.edit().putStringSet("folders", updated).apply()
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    suspend fun scan(): List<VideoItem> = withContext(Dispatchers.IO) {
        val found = mutableListOf<VideoItem>()
        folders().forEach { treeUri ->
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@forEach
            walk(root, root.name ?: "Folder", found)
        }
        found.distinctBy { it.uri.toString() }.sortedBy { it.name.lowercase() }
    }

    private fun walk(dir: DocumentFile, folderName: String, out: MutableList<VideoItem>) {
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        children.forEach { file ->
            when {
                file.isDirectory -> walk(file, folderName, out)
                file.isFile && (
                    file.type?.startsWith("video/") == true ||
                    file.name?.substringAfterLast('.', "")?.lowercase() in
                    setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v")
                ) -> out.add(VideoItem(file.uri, file.name ?: "Video", folderName))
            }
        }
    }
}
