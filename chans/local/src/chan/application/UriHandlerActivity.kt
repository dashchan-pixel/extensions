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
                // The archive directory does not have to be the immediate parent: the local chan
                // names a thread after the archive's file name wherever in the archive the user
                // filed it away, so anything below Archive opens, and the name is the last segment
                // either way.
                val index = segments.lastIndexOf(DIRECTORY_ARCHIVE)
                if (index >= 0 && index + 1 < segments.size) {
                    // pathSegments are decoded, and the synthetic URI below is parsed from a string.
                    val origin = Uri.encode(segments[segments.size - 1])
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
