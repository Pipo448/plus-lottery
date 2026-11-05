package com.plusgroup.pos

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.plusgroup.pos.util.UiScale

/**
 * Tout ekran app la eritye de klas sa a pou yo respekte "Gwosè ekran"
 * ajan an chwazi a (UiScale).
 */
open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }
}
