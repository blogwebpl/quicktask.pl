package pl.quicktask.app

@Suppress("unused")
class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}