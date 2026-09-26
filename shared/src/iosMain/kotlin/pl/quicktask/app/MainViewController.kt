package pl.quicktask.app

import androidx.compose.ui.window.ComposeUIViewController
import pl.quicktask.app.auth.crypto.AppleOpaqueBridge
import pl.quicktask.app.auth.crypto.AppleOpaqueManager
import pl.quicktask.app.di.AppModule

@Suppress("unused", "FunctionName")
fun MainViewController(opaqueBridge: AppleOpaqueBridge): platform.UIKit.UIViewController {
    val module = AppModule(opaqueManager = AppleOpaqueManager(opaqueBridge))
    return ComposeUIViewController { App(module = module) }
}
