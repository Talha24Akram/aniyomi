package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fork: always-on playback diagnostics. Samples mpv's cache/network state every 2 s and logs every
 * buffering transition to logcat (tag PlayTrace) and to <external files>/play-trace.log
 * (pullable with `adb pull`, capped at 1 MB, rotated once).
 */
object PlaybackTrace {
    private const val TAG = "PlayTrace"
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var lastSample = 0L

    fun init(context: Context) {
        runCatching {
            val f = File(context.getExternalFilesDir(null), "play-trace.log")
            if (f.exists() && f.length() > 1_000_000) f.renameTo(File(f.path + ".1"))
            file = f
        }
    }

    private fun prop(name: String) = runCatching { MPVLib.getPropertyString(name) as String? }.getOrNull() ?: "-"

    fun log(msg: String) {
        Log.i(TAG, msg)
        runCatching { file?.appendText("${fmt.format(Date())} $msg\n") }
    }

    fun snapshot(tag: String) {
        val path = prop("path").let { p -> if (p.length > 90) p.take(90) + "…" else p }
        log(
            "$tag pos=${prop("time-pos")} paused=${prop("pause")} pfc=${prop("paused-for-cache")} " +
                "bufState=${prop("cache-buffering-state")} ahead=${prop("demuxer-cache-duration")}s " +
                "cacheSpeed=${prop("cache-speed")}B/s fwdBytes=${prop("demuxer-cache-state/fw-bytes")} " +
                "vBitrate=${prop("video-bitrate")} aBitrate=${prop("audio-bitrate")} " +
                "drop=${prop("frame-drop-count")}/${prop("decoder-frame-drop-count")} " +
                "res=${prop("video-params/w")}x${prop("video-params/h")} hwdec=${prop("hwdec-current")} url=$path",
        )
    }

    fun sample() {
        val now = System.currentTimeMillis()
        if (now - lastSample < 2000) return
        lastSample = now
        snapshot("tick")
    }
}
