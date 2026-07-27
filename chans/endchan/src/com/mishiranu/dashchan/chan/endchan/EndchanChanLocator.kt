package com.mishiranu.dashchan.chan.endchan

import android.net.Uri
import android.webkit.MimeTypeMap
import chan.content.LynxchanChanLocator
import chan.util.StringUtils

class EndchanChanLocator : LynxchanChanLocator() {
    init {
        addChanHost("endchan.net")
        addChanHost("endchan.org")
        setHttpsMode(HttpsMode.CONFIGURABLE)
    }

    /**
     * Attachment file names are stored as `<hash>-<mime type without the slash>`, e.g.
     * `abcdef-imagepng`. Restore the slash to look the extension up when the abbreviated
     * form isn't a known extension by itself.
     */
    override fun createAttachmentForcedName(fileUri: Uri): String? {
        var fileName = StringUtils.emptyIfNull(fileUri.lastPathSegment)
        val index = fileName.indexOf('-')
        if (index >= 0) {
            var mimeType = fileName.substring(index + 1)
            fileName = fileName.substring(0, index)
            var extension = getFileExtension(mimeType)
            if (extension == null) {
                val insert =
                    when {
                        mimeType.startsWith("text") -> 4
                        mimeType == "application" -> 11
                        else -> 5
                    }
                mimeType = mimeType.substring(0, insert) + '/' + mimeType.substring(insert)
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
            if (extension != null) {
                return "$fileName.$extension"
            }
        }
        return fileName
    }
}
