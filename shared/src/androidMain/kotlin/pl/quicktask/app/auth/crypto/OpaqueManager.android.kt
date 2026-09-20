package pl.quicktask.app.auth.crypto

class AndroidOpaqueManager : OpaqueManager by NativeOpaqueManager()

actual fun createOpaqueManager(): OpaqueManager = AndroidOpaqueManager()
