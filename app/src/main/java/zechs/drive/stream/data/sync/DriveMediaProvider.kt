package zechs.drive.stream.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.util.Constants.Companion.DRIVE_API
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class DriveMediaProvider @Inject constructor(
    private val driveRepository: DriveRepository,
    private val sessionManager: SessionManager,
    @Named("OkHttpClientWithAuthenticator") private val okHttpClient: OkHttpClient
) : CloudMediaProvider {

    companion object {
        private const val TAG = "DriveMediaProvider"
        private const val PAGE_SIZE = 200
    }

    override suspend fun listFolderItems(folderId: String): List<CloudMediaItem> {
        val query = "'$folderId' in parents and trashed=false"
        val items = mutableListOf<CloudMediaItem>()
        var nextPageToken: String? = null
        do {
            val response = driveRepository.getFiles(
                query = query,
                pageToken = nextPageToken,
                pageSize = PAGE_SIZE
            )
            when (response) {
                is Resource.Success -> {
                    val files = response.data!!.files
                        .mapNotNull { toCloudItem(it) }
                        .filter { it.isVideo || it.isImage }
                    items.addAll(files)
                    nextPageToken = response.data.nextPageToken
                }

                is Resource.Error -> {
                    throw IOException(response.message ?: "Failed to list Drive folder items")
                }

                else -> {
                    throw IOException("Failed to list Drive folder items")
                }
            }
        } while (nextPageToken != null)

        return items.sortedBy { it.name.lowercase() }
    }

    override suspend fun downloadItem(item: CloudMediaItem, destination: File) {
        val client = sessionManager.fetchClient()
            ?: throw IOException("Drive client not found")
        val tokenResponse = driveRepository.fetchAccessToken(client)
        val accessToken = when (tokenResponse) {
            is Resource.Success -> tokenResponse.data!!.accessToken
            is Resource.Error -> throw IOException(tokenResponse.message ?: "Unable to fetch token")
            else -> throw IOException("Unable to fetch token")
        }

        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(item.downloadUrl)
                .header("Authorization", "Bearer $accessToken")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed: ${response.code} for ${item.id}")
                    throw IOException("Download failed with ${response.code}")
                }

                destination.outputStream().use { output ->
                    response.body?.byteStream()?.copyTo(output)
                        ?: throw IOException("Empty response body")
                }
            }
        }
    }

    private fun toCloudItem(file: DriveFile): CloudMediaItem? {
        if (file.isShortcut || file.isFolder) {
            return null
        }
        return CloudMediaItem(
            id = file.id,
            name = file.name,
            mimeType = file.mimeType,
            size = file.size,
            modifiedTime = file.modifiedTime,
            md5Checksum = file.md5Checksum,
            downloadUrl = "$DRIVE_API/files/${file.id}?supportsAllDrives=True&alt=media"
        )
    }
}
