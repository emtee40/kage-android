package com.absinthe.kage.media.audio

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import com.absinthe.kage.media.LocalMedia
import java.util.*

object MusicHelper {

    fun getAllLocalMusic(context: Context): List<LocalMusic> {
        val result: MutableList<LocalMusic> = ArrayList()
        val cursor = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null, null, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER)
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val albumId = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                val artistId = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID))
                if (title.isNotEmpty()) {
                    val music = LocalMusic()
                    music.title = title
                    music.album = album
                    music.albumId = albumId
                    music.artist = artist
                    music.artistId = artistId
                    music.filePath = path
                    music.type = LocalMedia.TYPE_AUDIO
                    result.add(music)
                }
            } while (cursor.moveToNext())
            cursor.close()
        }
        return result
    }

    @JvmStatic
    fun getAlbumArt(albumId: Long): Uri {
        val uriAlbums = "content://media/external/audio/albumart"
        return Uri.withAppendedPath(Uri.parse(uriAlbums), albumId.toString())
    }
}