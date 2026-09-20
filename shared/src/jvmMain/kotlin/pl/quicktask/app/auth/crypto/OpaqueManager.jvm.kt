package pl.quicktask.app.auth.crypto

class JvmOpaqueManager : OpaqueManager by NativeOpaqueManager()

actual fun createOpaqueManager(): OpaqueManager = JvmOpaqueManager()
