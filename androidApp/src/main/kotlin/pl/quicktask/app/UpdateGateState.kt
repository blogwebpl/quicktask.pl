package pl.quicktask.app

internal enum class UpdateGateState { Ready, Required }

/** A failed lookup must never discard a previously discovered mandatory update. */
internal fun updateGateState(installedVersion: Long, requiredVersion: Long): UpdateGateState =
    if (requiredVersion > installedVersion) UpdateGateState.Required else UpdateGateState.Ready
