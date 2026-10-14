package com.stevenroot.mabibliotheque.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Opens the device's browser on a Google Images search, so the user can pick whichever cover
 * they find (useful whenever an automated source has nothing — French books via BnF, series,
 * which have no metadata source at all). This intentionally avoids any scraping or third-party
 * API: it's the same as the user typing the search themselves, so there's no key, quota, or ToS
 * to depend on. The chosen image is then saved via the existing gallery-import + crop flow, not
 * fetched automatically. */
fun searchCoverOnline(context: Context, query: String) {
    val uri = Uri.parse("https://www.google.com/search?tbm=isch&q=" + Uri.encode(query))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucun navigateur disponible.", Toast.LENGTH_SHORT).show()
    }
}

/** After [searchCoverOnline] sends the user to the browser to save an image, re-opening the
 * gallery picker automatically when they come back (instead of making them re-open the cover
 * dialog and tap "Choisir une image" again) saves a step. Call the returned function at the same
 * time as [searchCoverOnline]; [onReturn] then fires once, on the next time this screen resumes —
 * not on first composition, and not again on later unrelated resumes. */
@Composable
fun rememberOnlineSearchReturn(onReturn: () -> Unit): () -> Unit {
    var awaitingReturn by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingReturn) {
                awaitingReturn = false
                onReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return { awaitingReturn = true }
}
