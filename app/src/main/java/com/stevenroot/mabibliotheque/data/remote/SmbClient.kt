package com.stevenroot.mabibliotheque.data.remote

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.stevenroot.mabibliotheque.data.repository.NetworkShareConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmbEntry(val name: String, val path: String, val isDirectory: Boolean, val sizeBytes: Long)

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "m4v", "mov", "wmv", "ts", "m2ts")

fun isVideoFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

/**
 * Thin wrapper over smbj (SMB2/3 — modern NAS setups, Synology included, disable SMB1 by
 * default). Every call opens a fresh connection/session/share and closes it afterwards rather
 * than keeping one alive, since scans only happen on explicit user action ("Rescanner"), not
 * continuously.
 */
class SmbClient {

    suspend fun <T> withShare(config: NetworkShareConfig, block: (DiskShare) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = SMBClient()
                client.connect(config.host).use { connection ->
                    val auth = AuthenticationContext(config.username, config.password.toCharArray(), null)
                    val session = connection.authenticate(auth)
                    (session.connectShare(config.shareName) as DiskShare).use { share ->
                        block(share)
                    }
                }
            }
        }

    suspend fun testConnection(config: NetworkShareConfig): Result<Unit> =
        withShare(config) { share -> share.list("") }.map { }

    /** Non-recursive: the folder picker needs one level at a time. */
    suspend fun listDirectory(config: NetworkShareConfig, path: String): Result<List<SmbEntry>> =
        withShare(config) { share ->
            share.list(path)
                .filter { it.fileName != "." && it.fileName != ".." }
                .map { info ->
                    val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value.toLong()) != 0L
                    val childPath = if (path.isBlank()) info.fileName else "$path/${info.fileName}"
                    SmbEntry(info.fileName, childPath, isDir, info.endOfFile)
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }

    /** Recursively lists every video file under [rootPath] (used by the digital-library scan). */
    suspend fun listVideoFilesRecursive(config: NetworkShareConfig, rootPath: String): Result<List<SmbEntry>> =
        withShare(config) { share ->
            val results = mutableListOf<SmbEntry>()
            fun walk(path: String) {
                for (info in share.list(path)) {
                    if (info.fileName == "." || info.fileName == "..") continue
                    val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value.toLong()) != 0L
                    val childPath = if (path.isBlank()) info.fileName else "$path/${info.fileName}"
                    if (isDir) {
                        walk(childPath)
                    } else if (isVideoFile(info.fileName)) {
                        results.add(SmbEntry(info.fileName, childPath, false, info.endOfFile))
                    }
                }
            }
            walk(rootPath)
            results
        }
}
