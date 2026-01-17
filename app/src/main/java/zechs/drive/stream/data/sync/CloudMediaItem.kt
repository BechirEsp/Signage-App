package zechs.drive.stream.data.sync

data class CloudMediaItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedTime: String?,
    val md5Checksum: String?,
    val downloadUrl: String
) {
    val isVideo = mimeType.startsWith("video/")
    val isImage = mimeType.startsWith("image/")
}
