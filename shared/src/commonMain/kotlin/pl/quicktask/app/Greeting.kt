package pl.quicktask.app

import pl.quicktask.app.platform.getPlatform

@Suppress("unused")
class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}
