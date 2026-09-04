package pl.quicktask.todo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawer() {
    ModalDrawerSheet {
        Text(
            text = "Zawartość Drawera",
            modifier = Modifier.padding(16.dp)
        )
    }
}
