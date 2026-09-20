package pl.quicktask.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.quicktask.app.ui.theme.AppTheme

@Composable
internal fun RequiredUpdateScreen(gate: RequiredUpdateGate) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.update_required_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.update_required_message), textAlign = TextAlign.Center)
                Button(onClick = { gate.check(userRequested = true) }, enabled = !gate.checking) {
                    Text(stringResource(if (gate.checking) R.string.update_checking else R.string.update_action))
                }
                TextButton(onClick = gate::openStore) {
                    Text(stringResource(R.string.update_open_store))
                }
                if (gate.storeUnavailable) {
                    Text(stringResource(R.string.update_store_unavailable), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
