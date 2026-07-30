package com.worxbend.zephyr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveShellTest {
    @Test
    fun selectsLayoutAtSupportedViewportBoundaries() {
        assertEquals(ShellLayout.Narrow, shellLayoutForWidth(400f))
        assertEquals(ShellLayout.Narrow, shellLayoutForWidth(800f))
        assertEquals(ShellLayout.Medium, shellLayoutForWidth(900f))
        assertEquals(ShellLayout.Medium, shellLayoutForWidth(1040f))
        assertEquals(ShellLayout.Wide, shellLayoutForWidth(1180f))
        assertEquals(ShellLayout.Wide, shellLayoutForWidth(1280f))
    }

    @Test
    fun narrowLayoutUsesOverlayNavigationAndOnlyWideShowsAllToolbarActions() {
        assertFalse(ShellLayout.Narrow.hasPersistentNavigation)
        assertTrue(ShellLayout.Medium.hasPersistentNavigation)
        assertTrue(ShellLayout.Wide.showsFullToolbar)
        assertFalse(ShellLayout.Medium.showsFullToolbar)
    }
}
