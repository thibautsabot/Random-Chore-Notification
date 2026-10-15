package app.randomchorenotification

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat

/**
 * Draws behind the system bars on every Android version (it is forced on from Android 15), so
 * layouts must pad themselves with the system-bar insets instead of relying on bar colors.
 */
fun Activity.enableEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT < 35) {
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
    }
}
