package zechs.drive.stream.data.sync

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveSyncManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val provider: CloudMediaProvider,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "DriveSyncManager"
        private const val MANIFEST_NAME = "manifest.json"
        private const val CACHE_ROOT = "cloud/drive"
    }

    data class SyncResult(
        val didChange: Boolean,
        val downloaded: Int,
        val deleted: Int,
        val items: List<LocalMediaItem>
    )

    suspend fun syncFolder(folderId: String): SyncResult = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "$CACHE_ROOT/$folderId")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        Log.d(TAG, "Sync start for folder=$folderId")

        val manifestFile = File(cacheDir, MANIFEST_NAME)
        val manifest = readManifest(manifestFile, folderId)
        val manifestById = manifest.items.associateBy { it.id }

        val remoteItems = provider.listFolderItems(folderId)
        val remoteIds = remoteItems.map { it.id }.toSet()

        var downloadCount = 0
        var deleteCount = 0

        val updatedItems = mutableListOf<DriveManifestItem>()

        for (remote in remoteItems) {
            val existing = manifestById[remote.id]
            val safeName = toSafeFileName(remote.id, remote.name)
            val targetFile = File(cacheDir, safeName)
            val hasChanged = existing == null
                || existing.size != remote.size
                || existing.modifiedTime != remote.modifiedTime
                || existing.md5Checksum != remote.md5Checksum
                || existing.name != remote.name

            if (hasChanged) {
                val tempFile = File(cacheDir, "$safeName.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                provider.downloadItem(remote, tempFile)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
                downloadCount += 1
                existing?.let {
                    if (it.localPath != targetFile.absolutePath) {
                        File(it.localPath).delete()
                    }
                }
            }

            val syncedAt = System.currentTimeMillis()
            updatedItems.add(
                DriveManifestItem(
                    id = remote.id,
                    name = remote.name,
                    mimeType = remote.mimeType,
                    size = remote.size,
                    modifiedTime = remote.modifiedTime,
                    md5Checksum = remote.md5Checksum,
                    localPath = targetFile.absolutePath,
                    lastSyncedAt = syncedAt
                )
            )
        }

        for (local in manifest.items) {
            if (!remoteIds.contains(local.id)) {
                File(local.localPath).delete()
                deleteCount += 1
            }
        }

        val newManifest = DriveManifest(
            folderId = folderId,
            lastSyncedAt = System.currentTimeMillis(),
            items = updatedItems
        )

        writeManifest(manifestFile, newManifest)

        val playlist = newManifest.items
            .sortedBy { it.name.lowercase() }
            .map {
                LocalMediaItem(
                    id = it.id,
                    name = it.name,
                    mimeType = it.mimeType,
                    localPath = it.localPath
                )
            }

        val didChange = downloadCount > 0 || deleteCount > 0 || manifest.items.size != updatedItems.size

        Log.d(
            TAG,
            "Sync complete. downloaded=$downloadCount deleted=$deleteCount total=${playlist.size}"
        )

        SyncResult(
            didChange = didChange,
            downloaded = downloadCount,
            deleted = deleteCount,
            items = playlist
        )
    }

    private fun readManifest(file: File, folderId: String): DriveManifest {
        return try {
            if (!file.exists()) {
                DriveManifest(folderId = folderId, lastSyncedAt = 0L, items = emptyList())
            } else {
                gson.fromJson(file.readText(), DriveManifest::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read manifest: ${e.message}")
            DriveManifest(folderId = folderId, lastSyncedAt = 0L, items = emptyList())
        }
    }

    private fun writeManifest(file: File, manifest: DriveManifest) {
        try {
            file.writeText(gson.toJson(manifest))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to write manifest: ${e.message}")
            throw IOException("Unable to write manifest")
        }
    }

    private fun toSafeFileName(id: String, name: String): String {
        val sanitized = name.replace("/", "_")
        return "${id}_$sanitized"
    }
}
