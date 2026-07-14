package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.PlatformContext

/**
 * True when live wallpaper animation previews (and full-screen topographic / arches
 * effects) are likely to freeze or crash this device. Used to skip settings previews
 * and warn before enabling.
 */
expect fun isLowPerformanceDeviceForBackgroundAnimation(platformContext: PlatformContext): Boolean
