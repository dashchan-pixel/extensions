package chan.application

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

class UriHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialIntent = intent
        val newIntent = Intent(ACTION).setData(initialIntent.data)
        val extras = initialIntent.extras
        if (extras != null) {
            newIntent.putExtras(extras)
        }
        try {
            startActivity(newIntent)
        } catch (e: ActivityNotFoundException) {
            // Ignore exception
        }
        finish()
        System.exit(0)
    }

    companion object {
        private const val ACTION = "chan.intent.action.HANDLE_URI"
    }
}
