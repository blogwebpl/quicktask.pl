package pl.quicktask.app.items.di

import io.ktor.client.HttpClient
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.data.SessionRefresher
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.inbox.data.InboxRepository
import pl.quicktask.app.items.data.CompletedItemsRepository
import pl.quicktask.app.items.data.FileOperations
import pl.quicktask.app.items.data.FilesRepository
import pl.quicktask.app.items.data.ItemQueries
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.ItemLifecycleService
import pl.quicktask.app.items.domain.ItemSyncService
import pl.quicktask.app.items.domain.UserKeysProvider
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.network.config.ApiConfig
import pl.quicktask.app.nextactions.data.NextActionsRepository
import pl.quicktask.app.now.data.NowOperations
import pl.quicktask.app.now.data.NowRepository
import pl.quicktask.app.settings.data.SettingsOperations
import pl.quicktask.app.settings.data.SettingsRepository
import pl.quicktask.app.trash.data.TrashRepository
import pl.quicktask.app.scheduled.data.ScheduledRepository
import pl.quicktask.app.scheduled.data.ScheduledOperations

/** Composition root: every item operation shares the same session, keys and lists. */
class ItemModule(
    httpClient: HttpClient,
    sessionManager: SessionManager,
    dPoPManager: DPoPManager,
    baseUrl: String = ApiConfig.BASE_URL,
    sessionRefresher: SessionRefresher,
    keysProvider: UserKeysProvider,
    val store: ItemStore = ItemStore(),
    fileOperations: FileOperations? = null,
    settingsOperations: SettingsOperations? = null,
) {
    val api = AuthenticatedApiClient(httpClient, dPoPManager, sessionManager, sessionRefresher, baseUrl)
    val mapper = ItemCryptoMapper(keysProvider)
    val queries = ItemQueries(api, mapper, store)
    val sync = ItemSyncService(api, mapper, store, queries)
    val files: FileOperations = fileOperations ?: FilesRepository(api, keysProvider)
    val inbox = InboxRepository(api, mapper, store, queries, files)
    val nextActions = NextActionsRepository(api, mapper, store, sync, files)
    val scheduled: ScheduledOperations = ScheduledRepository(api, mapper, store, sync, files)
    val now: NowOperations = NowRepository(api, mapper, sync, store)
    val trash = TrashRepository(api, store, queries, sync)
    val completed = CompletedItemsRepository(api, mapper, sync)
    val projects: pl.quicktask.app.projects.data.ProjectsOperations = pl.quicktask.app.projects.data.ProjectsRepository(api, mapper, store, sync, files, queries)
    val lifecycle = ItemLifecycleService(api, sync)
    val settings: SettingsOperations = settingsOperations ?: SettingsRepository(api)
}
