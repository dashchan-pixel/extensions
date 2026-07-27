package chan.application

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlin.system.exitProcess

class UriHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri != null) {
            val segments = uri.pathSegments
            if (segments != null) {
                // Everything below the archive directory is the thread's path relative to the
                // archive root, which is how the local chan names its threads — so a file the user
                // filed away in a subdirectory opens like one sitting at the top. Taking the last
                // "Archive" keeps a flat archive working even when the download directory sits
                // under one of its own.
                val index = segments.lastIndexOf(DIRECTORY_ARCHIVE)
                if (index >= 0 && index + 1 < segments.size) {
                    // pathSegments are decoded, and the synthetic URI below is parsed from a string.
                    val origin =
                        segments.subList(index + 1, segments.size).joinToString("/") { Uri.encode(it) }
                    // Internal synthetic URI used by LocalChanPerformer customUriHandler for local resources
                    val handleIntent = Intent(ACTION).setData(Uri.parse("http://localhost/null/res/$origin"))
                    intent.extras?.let { handleIntent.putExtras(it) }
                    try {
                        startActivity(handleIntent)
                    } catch (e: ActivityNotFoundException) {
                        // Ignore
                    }
                }
            }
        }
        finish()
        exitProcess(0)
    }

    companion object {
        private const val ACTION = "chan.intent.action.HANDLE_URI"
        private const val DIRECTORY_ARCHIVE = "Archive"
    }
}
