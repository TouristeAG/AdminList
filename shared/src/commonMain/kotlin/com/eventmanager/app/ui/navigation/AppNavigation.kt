package com.eventmanager.app.ui.navigation

/**
 * Top-level app destinations shared between Android and desktop shells.
 */
enum class AppRootDestination {
    SetupWizard,
    Welcome,
    AdminAuth,
    AdminSetup,
    AdminMain,
    Billeterie,
}

enum class AdminTab(val index: Int) {
    Dashboard(0),
    Guests(1),
    Volunteers(2),
    Shifts(3),
    Benefits(4),
    Settings(5);

    companion object {
        fun fromIndex(index: Int): AdminTab = entries.firstOrNull { it.index == index } ?: Dashboard
    }
}

enum class BilleterieSection {
    Home,
    GuestList,
    Scanner,
    Settings
}

data class AppNavigationState(
    val destination: AppRootDestination = AppRootDestination.Welcome,
    val adminTab: AdminTab = AdminTab.Dashboard,
    val billeterieSection: BilleterieSection = BilleterieSection.Home,
    val showAdminAuth: Boolean = false,
    val showAdminSetup: Boolean = false,
)
