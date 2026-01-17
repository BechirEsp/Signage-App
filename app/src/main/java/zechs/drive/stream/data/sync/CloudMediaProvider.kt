package zechs.drive.stream.data.sync

import java.io.File

interface CloudMediaProvider {
    suspend fun listFolderItems(folderId: String): List<CloudMediaItem>

    suspend fun downloadItem(
        item: CloudMediaItem,
        destination: File
    )
}
