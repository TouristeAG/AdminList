package com.eventmanager.app.platform

/**
 * Cross-platform application context providing paths and lifecycle hooks.
 */
expect class PlatformContext

expect fun createPlatformContext(): PlatformContext

expect val PlatformContext.appDataDir: java.io.File

expect val PlatformContext.isDesktop: Boolean
