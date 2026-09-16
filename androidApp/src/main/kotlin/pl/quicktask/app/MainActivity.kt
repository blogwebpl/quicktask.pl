package pl.quicktask.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ProcessLifecycleOwner
import pl.quicktask.app.auth.AppContext
import pl.quicktask.app.sync.SyncLifecycleObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        AppContext.applicationContext = applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(SyncLifecycleObserver())

        setContent {
            App()
        }
    }
}

@Composable
fun AppAndroidPreview() {
    App()
}
