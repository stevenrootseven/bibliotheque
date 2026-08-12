package com.stevenroot.mabibliotheque.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.stevenroot.mabibliotheque.data.repository.NetworkShareConfig

/** Hands a NAS file off to whatever app on the phone can open an smb:// URL (VLC, MX Player,
 * Kodi, dedicated SMB players — several are commonly installed). No local download/proxy: the
 * file plays straight off the NAS over the network. Shared between movie copies and series
 * episodes — same NAS, same playback story.
 *
 * Sets only the URI, not a MIME type: passing an explicit video MIME type via setDataAndType
 * requires the target's intent-filter to declare a matching android:mimeType, but SMB-capable
 * players typically filter on the smb URI scheme alone with no mimeType — forcing one made the
 * intent match nothing, so it always fell through to "no compatible app" even with several
 * installed.
 *
 * Also launches directly rather than pre-checking with resolveActivity(): on Android 11+,
 * package visibility rules make resolveActivity()/queryIntentActivities() blind to other apps
 * unless they're declared in a queries manifest block. startActivity() itself isn't subject to
 * that restriction, so catching the failure it can actually throw is simpler and correct. */
fun playSmbFile(context: Context, config: NetworkShareConfig, sourcePath: String) {
    val user = Uri.encode(config.username)
    val pass = Uri.encode(config.password)
    val encodedPath = sourcePath.split("/").joinToString("/") { Uri.encode(it) }
    val uri = Uri.parse("smb://$user:$pass@${config.host}/${Uri.encode(config.shareName)}/$encodedPath")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application compatible SMB trouvée (ex. VLC)", Toast.LENGTH_LONG).show()
    }
}
