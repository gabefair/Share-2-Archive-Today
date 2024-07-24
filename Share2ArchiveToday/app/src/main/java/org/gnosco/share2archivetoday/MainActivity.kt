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
                    val cleanedUrl = cleanYoutubeUrl(url)
                    openInBrowser("https://archive.is/?run=1&url=${Uri.encode(cleanedUrl)}")
                }
            }

        }
        finish()
    }

    private fun cleanYoutubeUrl(url: String): String {
        val uri = Uri.parse(url)
        if (uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true) {
            val newUriBuilder = uri.buildUpon().clearQuery()

            uri.queryParameterNames.forEach { param ->
                if (param != "si") {
                    newUriBuilder.appendQueryParameter(param, uri.getQueryParameter(param))
                }
            }

            // Special handling for nested query parameters like `redir_token`
            val nestedQueryParams = uri.getQueryParameter("q")
            if (nestedQueryParams != null) {
                val nestedUri = Uri.parse(nestedQueryParams)
                val newNestedUriBuilder = nestedUri.buildUpon().clearQuery()

                nestedUri.queryParameterNames.forEach { nestedParam ->
                    newNestedUriBuilder.appendQueryParameter(nestedParam, nestedUri.getQueryParameter(nestedParam))
                }

                newUriBuilder.appendQueryParameter("q", newNestedUriBuilder.build().toString())
            }

            newUriBuilder.path(uri.path?.replace("/shorts/", "/v/") ?: uri.path)

            return newUriBuilder.build().toString()
        }
        return url
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