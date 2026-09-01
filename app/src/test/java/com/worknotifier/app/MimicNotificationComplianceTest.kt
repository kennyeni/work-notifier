package com.worknotifier.app

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the fixes that got mimic notifications to actually display on Android
 * Auto (see CLAUDE.md's "Android Auto Mimic Notification" section for the full story). Each test
 * here corresponds to a real bug that was found and fixed - if one of these starts failing, the
 * notification will very likely stop showing on the car screen again, with no other symptom.
 */
class MimicNotificationComplianceTest {

    // --- Action semantic tagging: Android Auto requires a properly-tagged Reply action ---

    @Test
    fun `RemoteInput-bearing action is forced to SEMANTIC_ACTION_REPLY regardless of original tag`() {
        val result = MimicNotificationCompliance.effectiveSemanticAction(
            hasRemoteInput = true,
            originalSemanticAction = NotificationCompat.Action.SEMANTIC_ACTION_NONE
        )
        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_REPLY, result)
    }

    @Test
    fun `action without RemoteInput keeps its original semantic tag`() {
        val result = MimicNotificationCompliance.effectiveSemanticAction(
            hasRemoteInput = false,
            originalSemanticAction = NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ
        )
        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ, result)
    }

    // --- Mandatory action backfill: Auto silently refuses to display without both actions ---

    @Test
    fun `backfill is needed when no bridged action carries the required semantic tag`() {
        val actions = listOf(NotificationCompat.Action.SEMANTIC_ACTION_ARCHIVE)
        assertTrue(
            MimicNotificationCompliance.needsActionBackfill(actions, NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        )
        assertTrue(
            MimicNotificationCompliance.needsActionBackfill(
                actions, NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ
            )
        )
    }

    @Test
    fun `backfill is not needed once a bridged action already carries the required tag`() {
        val actions = listOf(
            NotificationCompat.Action.SEMANTIC_ACTION_REPLY,
            NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ
        )
        assertFalse(
            MimicNotificationCompliance.needsActionBackfill(actions, NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        )
        assertFalse(
            MimicNotificationCompliance.needsActionBackfill(
                actions, NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ
            )
        )
    }

    @Test
    fun `zero original actions needs both fallbacks`() {
        val actions = emptyList<Int>()
        assertTrue(
            MimicNotificationCompliance.needsActionBackfill(actions, NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        )
        assertTrue(
            MimicNotificationCompliance.needsActionBackfill(
                actions, NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ
            )
        )
    }

    // --- PendingIntent request codes: bridged and fallback actions must never collide ---

    @Test
    fun `fallback request codes never collide with any supported bridged action index`() {
        val mimicId = 100_000
        val replyCode = MimicNotificationCompliance.fallbackReplyRequestCode(mimicId)
        val markReadCode = MimicNotificationCompliance.fallbackMarkAsReadRequestCode(mimicId)

        for (index in 0 until MimicNotificationCompliance.MAX_SUPPORTED_BRIDGED_ACTIONS) {
            val bridgedCode = MimicNotificationCompliance.bridgedActionRequestCode(mimicId, index)
            assertNotEquals("bridged action $index collides with fallback reply", replyCode, bridgedCode)
            assertNotEquals("bridged action $index collides with fallback mark-as-read", markReadCode, bridgedCode)
        }
        assertNotEquals("fallback reply and mark-as-read codes collide with each other", replyCode, markReadCode)
    }

    // --- Person key/name resolution: Android Auto needs a stable key per participant ---

    @Test
    fun `person key falls back only when original has none`() {
        assertEquals("original-key", MimicNotificationCompliance.resolvePersonKey("original-key", "fallback-key"))
        assertEquals("fallback-key", MimicNotificationCompliance.resolvePersonKey(null, "fallback-key"))
    }

    @Test
    fun `person name falls back only when original has none`() {
        assertEquals(
            "Original Name",
            MimicNotificationCompliance.resolvePersonName("Original Name", "Fallback Name")
        )
        assertEquals(
            "Fallback Name",
            MimicNotificationCompliance.resolvePersonName(null, "Fallback Name")
        )
    }

    // --- Small icon: must be a flat vector, not the adaptive launcher icon ---

    @Test
    fun `mimic small icon is not the adaptive launcher icon`() {
        assertNotEquals(R.mipmap.ic_launcher, MimicNotificationCompliance.mimicSmallIconRes())
        assertEquals(R.drawable.ic_notification, MimicNotificationCompliance.mimicSmallIconRes())
    }
}
