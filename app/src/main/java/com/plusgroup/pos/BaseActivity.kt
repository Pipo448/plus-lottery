package com.plusgroup.pos

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.util.UiScale

/**
 * Tout ekran app la eritye de klas sa a pou yo respekte "Gwosè ekran"
 * ajan an chwazi a (UiScale).
 *
 * MIZAJOU OTOMATIK: sou nenpòt ekran, lè l parèt epi chak 20 minit pandan
 * li louvri, APK a tcheke si gen yon nouvo vèsyon sou sit la. Si wi, yon
 * mesaj parèt pou ajan an telechaje l. Eksepsyon: ekran "Nouvo fich" la —
 * nou pa koupe ajan an pandan l ap vann (mesaj la ap parèt sou pwochen ekran an).
 */
open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
        com.plusgroup.pos.util.Lang.get(newBase) // chaje lang ajan an chwazi a
    }

    /** Apre chak setContentView: tradwi ekran an si ajan an chwazi Français. */
    override fun onContentChanged() {
        super.onContentChanged()
        com.plusgroup.pos.util.Lang.apply(findViewById(android.R.id.content))
    }

    // ---------- Mizajou otomatik ----------
    protected open val autoUpdateCheck: Boolean get() = true

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateTick = object : Runnable {
        override fun run() {
            if (!isFinishing && autoUpdateCheck) {
                com.plusgroup.pos.util.AppUpdate.check(this@BaseActivity, lifecycleScope)
            }
            updateHandler.postDelayed(this, UPDATE_TICK_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateHandler.removeCallbacks(updateTick)
        updateHandler.postDelayed(updateTick, 1500L) // kite ekran an fin parèt
    }

    override fun onPause() {
        updateHandler.removeCallbacks(updateTick)
        super.onPause()
    }

    private companion object {
        const val UPDATE_TICK_MS = 20 * 60 * 1000L
    }
}
