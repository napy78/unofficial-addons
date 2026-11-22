package com.napy.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.napy.musicplayer.data.LibraryDb
import com.napy.musicplayer.ui.ArtFetcher
import com.napy.musicplayer.ui.ArtKey

class MusicApp : Application(), ImageLoaderFactory {
    val db by lazy { LibraryDb.get(this) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(coil.key.Keyer<ArtKey> { data, _ -> data.uri })
                add(ArtFetcher.Factory(this@MusicApp))
            }
            .crossfade(true)
            .build()
}
