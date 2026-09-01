package com.worknotifier.app

import androidx.core.app.NotificationCompat

/**
 * Pure, Android-framework-free decision logic used by
 * [NotificationInterceptorService.createMimicNotification] to satisfy Android Auto's real (but
 * undocumented-until-you-hit-them) requirements for MessagingStyle notifications.
 *
 * Every function here corresponds to a bug that made mimic notifications post successfully and
 * display fine on the phone, while Android Auto silently refused to show them on the car screen -
 * with no error, log, or other visible symptom outside an actual car. See CLAUDE.md's "Android
 * Auto Mimic Notification" section for the full story. Extracted out of the service so a
 * regression here fails a JVM unit test instead of only being discoverable on the next car test.
 */
internal object MimicNotificationCompliance {

    /**
     * Bridged actions use PendingIntent request codes `mimicId + index` for index in
     * `[0, action count)`. These fallback offsets must stay comfortably clear of that range:
     * bridged and fallback actions can coexist in the same notification and both use the
     * `ACTION_MIMIC_ACTION` intent action, so a colliding request code would let
     * `FLAG_UPDATE_CURRENT` silently overwrite one action's PendingIntent with the other's.
     */
    const val MAX_SUPPORTED_BRIDGED_ACTIONS = 100
    const val FALLBACK_REPLY_REQUEST_CODE_OFFSET = 500
    const val FALLBACK_MARK_AS_READ_REQUEST_CODE_OFFSET = 501

    fun bridgedActionRequestCode(mimicId: Int, index: Int): Int = mimicId + index

    fun fallbackReplyRequestCode(mimicId: Int): Int = mimicId + FALLBACK_REPLY_REQUEST_CODE_OFFSET

    fun fallbackMarkAsReadRequestCode(mimicId: Int): Int = mimicId + FALLBACK_MARK_AS_READ_REQUEST_CODE_OFFSET

    /**
     * Android Auto requires a RemoteInput-bearing action to be tagged `SEMANTIC_ACTION_REPLY`,
     * but most apps never call `setSemanticAction()` on their own notification actions - so
     * bridging the original app's tag verbatim usually produces a reply-capable action Android
     * Auto doesn't recognize as the reply affordance. Force it whenever a RemoteInput is present.
     */
    fun effectiveSemanticAction(hasRemoteInput: Boolean, originalSemanticAction: Int): Int =
        if (hasRemoteInput) NotificationCompat.Action.SEMANTIC_ACTION_REPLY else originalSemanticAction

    /**
     * Android Auto silently refuses to display a MessagingStyle notification unless it has both
     * a `SEMANTIC_ACTION_REPLY` action and a `SEMANTIC_ACTION_MARK_AS_READ` action - the phone
     * shows the notification regardless, so violating this has no visible symptom on the phone.
     */
    fun needsActionBackfill(effectiveSemanticActions: List<Int>, requiredSemanticAction: Int): Boolean =
        effectiveSemanticActions.none { it == requiredSemanticAction }

    /** Android Auto needs a stable key per conversation participant to identify them reliably. */
    fun resolvePersonKey(originalKey: String?, fallbackKey: String): String = originalKey ?: fallbackKey

    fun resolvePersonName(originalName: CharSequence?, fallbackName: CharSequence): CharSequence =
        originalName ?: fallbackName

    /**
     * Must NOT be an adaptive/launcher icon (e.g. `R.mipmap.ic_launcher`) - Android Auto silently
     * drops notifications whose small icon it can't render as a flat vector, with no error on the
     * phone side, which is what made this bug so hard to diagnose in the first place.
     */
    fun mimicSmallIconRes(): Int = R.drawable.ic_notification
}
