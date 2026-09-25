package pl.quicktask.app

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ProcessLifecycleOwner
import pl.quicktask.app.auth.session.AppContext
import pl.quicktask.app.auth.presentation.receiveOAuthDeepLink
import pl.quicktask.app.sync.platform.SyncLifecycleObserver

class MainActivity : ComponentActivity() {
    private lateinit var updateGate: RequiredUpdateGate

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

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
        intent?.dataString?.let(::receiveOAuthDeepLink)
        AppContext.applicationContext = applicationContext
        updateGate = RequiredUpdateGate(this)
        splashScreen.setKeepOnScreenCondition { updateGate.checking }
        updateGate.check()
        ProcessLifecycleOwner.get().lifecycle.addObserver(SyncLifecycleObserver(pl.quicktask.app.di.sharedAppModule.sync))

        setContent {
            if (updateGate.state == UpdateGateState.Ready) {
                App()
            } else {
                RequiredUpdateScreen(updateGate)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(::receiveOAuthDeepLink)
    }

    override fun onResume() {
        super.onResume()
        updateGate.check()
    }

    override fun onDestroy() {
        updateGate.dispose()
        super.onDestroy()
    }
}

@Composable
fun AppAndroidPreview() {
    App()
}
