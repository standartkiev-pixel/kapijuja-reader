package com.kapijuja.reader

import android.os.Handler
import android.os.Looper
import android.widget.Button
import java.util.WeakHashMap

/**
 * Shows a tiny rotating-triangle animation while a TTS request is being
 * prepared. It changes only the button text, so the button background itself
 * does not spin and the UI remains calm on small screens.
 */
object PlaybackBusyIndicator {
    private val handler = Handler(Looper.getMainLooper())
    private val active = WeakHashMap<Button, Runnable>()
    private val frames = arrayOf("▷", "▽", "◁", "△")

    fun start(button: Button) {
        stop(button, null)
        var frame = 0
        val task = object : Runnable {
            override fun run() {
                if (active[button] !== this) return
                button.text = frames[frame % frames.size]
                frame += 1
                handler.postDelayed(this, 140L)
            }
        }
        active[button] = task
        task.run()
    }

    fun stop(button: Button, text: CharSequence?) {
        active.remove(button)?.let { handler.removeCallbacks(it) }
        if (text != null) button.text = text
    }
}
