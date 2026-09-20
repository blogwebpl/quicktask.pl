package pl.quicktask.app.ui.components

enum class DialogType {
    QUESTION,
    DANGER,
    WARNING,
    INFO,
}

enum class DialogButtonStyle {
    PRIMARY,
    SECONDARY,
    OUTLINED,
    TEXT,
    DESTRUCTIVE,
}

enum class DialogButtonLayout {
    AUTO,
    HORIZONTAL,
    VERTICAL,
}

data class DialogButton(
    val text: String,
    val onClick: () -> Unit,
    val style: DialogButtonStyle = DialogButtonStyle.PRIMARY,
    val enabled: Boolean = true,
    val isLoading: Boolean = false,
)
