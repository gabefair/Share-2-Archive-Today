package org.gnosco.share2archivetoday

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.gnosco.share2archivetoday.ui.theme.Share2ArchiveTodayTheme

import android.net.Uri
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                Log.d("MainActivity", "Shared text: $sharedText")
                val trimmedText = sharedText.trim()
                val sharedUri = Uri.parse(trimmedText)
                if (sharedUri != null && sharedUri.scheme != null && sharedUri.host != null) {
//                    val modifiedUri = Uri.Builder()
//                        .scheme("https")
//                        .authority("archive.today")
//                        .appendPath("share")
//                        .appendQueryParameter("url", sharedText)
//                        .build()
//                    openInBrowser(modifiedUri.toString())
//                    openInBrowser("https://archive.is/?run=1&url=$trimmedText")
                    openInBrowser("https://archive.today/share?url=${Uri.encode(trimmedText)}")

                }
            }
        }
    }

    private fun openInBrowser(url: String) {
        Log.d("MainActivity", "Opening URL: $url")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
        finish()
    }


}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Share2ArchiveTodayTheme {
        Greeting("Android")
    }
}