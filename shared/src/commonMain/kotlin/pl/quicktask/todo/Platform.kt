package pl.quicktask.todo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform