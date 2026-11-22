package com.napy.musicplayer.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chiave per caricare la locandina di un brano (copertina incorporata o della cartella/album). */
data class ArtKey(val uri: String)

class ArtFetcher(private val ctx: Context, private val key: ArtKey) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(key.uri)
        var bmp = if (Build.VERSION.SDK_INT >= 29) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(512, 512), null) }.getOrNull()
        } else null
        if (bmp == null) {
            bmp = runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, uri)
                    r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } finally { r.release() }
            }.getOrNull()
        }
        bmp?.let { DrawableResult(BitmapDrawable(ctx.resources, it), false, DataSource.DISK) }
    }

    class Factory(private val ctx: Context) : Fetcher.Factory<ArtKey> {
        override fun create(data: ArtKey, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtFetcher(ctx, data)
    }
}
