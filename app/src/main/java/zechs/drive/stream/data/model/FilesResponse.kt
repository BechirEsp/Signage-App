package zechs.drive.stream.data.model

import androidx.annotation.Keep
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.ToJson

@Keep
data class FilesResponse(
    val files: List<File>,
    val nextPageToken: String?
)

@Keep
data class File(
    val id: String,
    val name: String,
    val size: Long?,
    val iconLink: String,
    val mimeType: String,
    val modifiedTime: String? = null,
    val md5Checksum: String? = null,
    val shortcutDetails: ShortcutDetails = ShortcutDetails(),
    @Json(name = "starred")
    val starred: Starred
) {
    fun toDriveFile() = DriveFile(
        id = id,
        name = name,
        size = size,
        mimeType = mimeType,
        iconLink = iconLink,
        shortcutDetails = shortcutDetails,
        starred = starred,
        modifiedTime = modifiedTime,
        md5Checksum = md5Checksum
    )
}

@Keep
data class ShortcutDetails(
    val targetId: String? = null,
    val targetMimeType: String? = null
)

enum class Starred {
    UNSTARRED, STARRED, UNKNOWN, LOADING
}


class StarredAdapter {

    @FromJson
    fun fromJson(starred: Boolean): Starred {
        return if (starred) Starred.STARRED else Starred.UNSTARRED
    }

    @ToJson
    fun toJson(starred: Starred): Boolean {
        return starred == Starred.STARRED
    }

}
