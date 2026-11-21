package com.blender.studio.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Historique annuler/rétablir (édition non destructive : on ne stocke que des paramètres). */
class History<T>(initial: T) {
    var current by mutableStateOf(initial); private set
    private var committed = initial
    private var undoS by mutableStateOf(listOf<T>())
    private var redoS by mutableStateOf(listOf<T>())
    val canUndo get() = undoS.isNotEmpty()
    val canRedo get() = redoS.isNotEmpty()

    /** Changement en direct (curseur en mouvement), sans entrée d'historique. */
    fun preview(v: T) { current = v }
    fun commit(v: T = current) {
        if (v == committed) { current = v; return }
        undoS = (undoS + committed).takeLast(500)
        committed = v; current = v; redoS = emptyList()
    }
    fun undo() {
        if (!canUndo) return
        redoS = redoS + committed; committed = undoS.last(); undoS = undoS.dropLast(1); current = committed
    }
    fun redo() {
        if (!canRedo) return
        undoS = undoS + committed; committed = redoS.last(); redoS = redoS.dropLast(1); current = committed
    }
}

data class Project(val id: String, val type: String, val uri: String, val title: String, val updated: Long, val state: String)

/** Sauvegarde automatique des projets (paramètres d'édition) dans SharedPreferences. */
object ProjectStore {
    private fun prefs(c: Context) = c.getSharedPreferences("projects", Context.MODE_PRIVATE)

    fun all(c: Context): List<Project> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Project(it.getString("id"), it.getString("type"), it.getString("uri"), it.getString("title"), it.getLong("updated"), it.getString("state"))
        }.sortedByDescending { it.updated }
    }

    @Synchronized
    fun save(c: Context, p: Project) {
        val list = all(c).filter { it.id != p.id } + p
        write(c, list.sortedByDescending { it.updated }.take(30))
    }

    @Synchronized
    fun delete(c: Context, id: String) = write(c, all(c).filter { it.id != id })

    private fun write(c: Context, list: List<Project>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type).put("uri", it.uri)
                .put("title", it.title).put("updated", it.updated).put("state", it.state))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }
}

enum class MediaKind { Image, Video, Audio }

object Media {
    /** Copie un fichier du cache vers la galerie (Pictures/Movies/Music › Blender). */
    fun saveFile(ctx: Context, src: File, mime: String, name: String, kind: MediaKind): Uri? {
        val (coll, dir) = when (kind) {
            MediaKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            MediaKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            MediaKind.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Blender")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(coll, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        src.delete()
        return uri
    }

    fun durationMs(ctx: Context, uri: Uri): Long = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } finally { r.release() }
    }.getOrDefault(0L)

    fun displayName(ctx: Context, uri: Uri, fallback: String): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0).substringBeforeLast('.') else null
        }
    }.getOrNull() ?: fallback

    fun fmt(ms: Long): String {
        val s = ms / 1000; val m = s / 60
        return "%d:%02d.%01d".format(m, s % 60, (ms % 1000) / 100)
    }
}
