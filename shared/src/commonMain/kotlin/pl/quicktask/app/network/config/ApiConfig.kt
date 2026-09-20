package pl.quicktask.app.network.config

object ApiConfig {
    val BASE_URL: String get() = if (pl.quicktask.app.auth.session.browserSessions) pl.quicktask.app.auth.session.browserOrigin() else "https://quicktask.pl"
}
