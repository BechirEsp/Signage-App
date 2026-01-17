package zechs.drive.stream.data.sync

data class DriveManifest(
    val folderId: String,
    val lastSyncedAt: Long,
    val items: List<DriveManifestItem>
)

data class DriveManifestItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedTime: String?,
    val md5Checksum: String?,
    val localPath: String,
    val lastSyncedAt: Long
)

data class LocalMediaItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val localPath: String
) {
    val isVideo = mimeType.startsWith("video/")
    val isImage = mimeType.startsWith("image/")
}
