package com.eventmanager.app.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.ui.transitions.DramaticSpaceEntrance
import com.eventmanager.app.ui.transitions.SpaceEntrance

/** @deprecated Use [SpaceEntrance]. Kept as a desktop alias during migration. */
typealias DesktopSpaceEntrance = SpaceEntrance

/** @deprecated Use [DramaticSpaceEntrance]. Kept as a desktop alias during migration. */
@Composable
fun DesktopDramaticSpaceEntrance(
    enabled: Boolean,
    space: DesktopSpaceEntrance,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DramaticSpaceEntrance(
        enabled = enabled,
        space = space,
        modifier = modifier,
        content = content,
    )
}
