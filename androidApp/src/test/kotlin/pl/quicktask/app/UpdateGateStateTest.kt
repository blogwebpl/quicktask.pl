package pl.quicktask.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateGateStateTest {
    @Test fun unavailableStoreWithoutKnownUpdateAllowsStartup() {
        assertEquals(UpdateGateState.Ready, updateGateState(1190, 0))
    }

    @Test fun knownUpdateStaysRequiredAfterCancellationOfflineOrRestart() {
        assertEquals(UpdateGateState.Required, updateGateState(1190, 1191))
    }

    @Test fun installedUpdateClearsTheGate() {
        assertEquals(UpdateGateState.Ready, updateGateState(1191, 1191))
        assertEquals(UpdateGateState.Ready, updateGateState(1192, 1191))
    }
}
