package com.worxbend.zephyr

import com.worxbend.zephyr.viewmodel.ZephyrRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardShortcutsTest {
    @Test
    fun resolvesPrimaryShiftWorkspaceRoutes() {
        assertEquals(
            ZephyrRoute.UpdateCenter,
            resolveWorkspaceShortcut(WorkspaceShortcutKey.U, primaryPressed = true, shiftPressed = true),
        )
        assertEquals(
            ZephyrRoute.History,
            resolveWorkspaceShortcut(WorkspaceShortcutKey.H, primaryPressed = true, shiftPressed = true),
        )
    }

    @Test
    fun ignoresIncompleteShortcutModifiers() {
        assertNull(
            resolveWorkspaceShortcut(WorkspaceShortcutKey.O, primaryPressed = true, shiftPressed = false),
        )
    }
}
