package pl.quicktask.todo.main

enum class Screen(val id: String, val title: String) {
    INBOX("inbox", "Inbox"),
    NEXT_ACTIONS("next", "Next Actions"),
    SCHEDULED("scheduled", "Scheduled"),
    WAITING("waiting", "Waiting"),
    PROJECTS("projects", "All Projects"),
    SOMEDAY("someday", "Someday/Maybe"),
    REFERENCE("reference", "Reference"),
    WEEKLY_REVIEW("review", "Weekly Review"),
    COMPLETED("completed", "Completed"),
    TRASH("trash", "Trash");

    companion object {
        fun fromId(id: String): Screen = entries.firstOrNull { it.id == id } ?: INBOX
    }
}
