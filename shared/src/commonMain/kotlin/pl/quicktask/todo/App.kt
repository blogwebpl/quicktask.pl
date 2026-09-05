package pl.quicktask.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import pl.quicktask.todo.main.MainLayout

@Composable
@Preview
fun App() {
    MaterialTheme {
        MainLayout()
    }
}
