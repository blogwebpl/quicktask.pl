package pl.quicktask.app.main.model

import org.jetbrains.compose.resources.StringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.screen_completed
import todo.shared.generated.resources.screen_inbox
import todo.shared.generated.resources.screen_now
import todo.shared.generated.resources.screen_next_actions
import todo.shared.generated.resources.screen_projects
import todo.shared.generated.resources.screen_reference
import todo.shared.generated.resources.screen_scheduled
import todo.shared.generated.resources.screen_someday
import todo.shared.generated.resources.screen_trash
import todo.shared.generated.resources.screen_waiting
import todo.shared.generated.resources.screen_review
import todo.shared.generated.resources.settings

enum class Screen(val id: String, val titleRes: StringResource) {
    INBOX("inbox", Res.string.screen_inbox),
    NOW("now", Res.string.screen_now),
    NEXT_ACTIONS("next", Res.string.screen_next_actions),
    SCHEDULED("scheduled", Res.string.screen_scheduled),
    WAITING("waiting", Res.string.screen_waiting),
    PROJECTS("projects", Res.string.screen_projects),
    SOMEDAY("someday", Res.string.screen_someday),
    REFERENCE("reference", Res.string.screen_reference),
    REVIEW("review", Res.string.screen_review),
    COMPLETED("completed", Res.string.screen_completed),
    TRASH("trash", Res.string.screen_trash),
    SETTINGS("settings", Res.string.settings);

    companion object {
        fun fromId(id: String): Screen = entries.firstOrNull { it.id == id } ?: INBOX
    }
}
