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
import androidx.core.util.PatternsCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
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
                val url = extractUrl(sharedText)
                if (url != null) {
                    openInBrowser("https://archive.is/?run=1&url=${Uri.encode(url)}")
                }
            }

        }
        finish()
    }

    private fun extractUrl(text: String): String? {
        val matcher = PatternsCompat.WEB_URL.matcher(text)
        return if (matcher.find()) {
            matcher.group(0)
        } else {
            null
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