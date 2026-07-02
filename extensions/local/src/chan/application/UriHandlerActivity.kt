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
			if (segments != null && segments.size >= 2) {
				val directory = segments[segments.size - 2]
				if (directory == "Archive") {
					val origin = segments[segments.size - 1]
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
	}
}
