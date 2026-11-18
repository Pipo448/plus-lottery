package com.plusgroup.pos.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Logo klib yo (paryaj): chaje imaj la an bakgrawonn, kenbe l an memwa.
 * URL la soti nan sèvè a (homeLogo / awayLogo). Si l pa chaje → onFail().
 */
object TeamLogos {
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, view: ImageView, onFail: () -> Unit) {
        view.tag = url
        cache.get(url)?.let { view.setImageBitmap(it); return }
        if (synchronized(failed) { url in failed }) { onFail(); return }
        pool.execute {
            val bmp = fetch("$url/tiny") ?: fetch(url)
            main.post {
                if (view.tag != url) return@post
                if (bmp != null) { cache.put(url, bmp); view.setImageBitmap(bmp) }
                else { synchronized(failed) { failed.add(url) }; onFail() }
            }
        }
    }

    private fun fetch(u: String): Bitmap? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000
        c.inputStream.use { input ->
            val bytes = input.readBytes()
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 96) s *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
        }
    } catch (_: Exception) { null }
}
