package com.artesys.netcon

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min

/**
 * Enforces the 3-day (72-hour) free trial and 1-Key-Per-1-Account HMAC-SHA256 license verification.
 *
 * How 1-Key-Per-1-Account Binding Works:
 * 1. Each issued license key (`NETCON-<ACCT>-<PLAN>-<YEAR>-<SIG>`) is cryptographically bound to
 *    a single normalized account email/ID (`accountIdentifier`):
 *    - `<ACCT>` (Block 1) is the 4-char HMAC-SHA256 fingerprint of `"ACCT:<normalizedAccount>"`.
 *    - `<SIG>` (Block 4) is the 4-char HMAC-SHA256 signature of `"<normalizedAccount>|<ACCT>-<PLAN>-<YEAR>"`.
 * 2. A key generated for `userA@example.com` will fail cryptographic verification if entered by
 *    `userB@example.com`.
 * 3. In addition, the local/registry claim table binds `<normalizedKey>` to the first claiming
 *    account + installation ID (`device_install_id`), rejecting any subsequent attempt to claim
 *    the same key under a different account.
 */
object NetconLicenseManager {
    private const val PREFS_NAME = "netcon_license_prefs"
    private const val KEY_TRIAL_START_EPOCH_MS = "trial_start_epoch_ms"
    private const val KEY_MAX_SEEN_EPOCH_MS = "max_seen_epoch_ms"
    private const val KEY_ACTIVATED_LICENSE = "activated_license_key"
    private const val KEY_ACTIVATED_ACCOUNT = "activated_account_id"
    private const val KEY_ACTIVATED_AT_EPOCH_MS = "activated_at_epoch_ms"
    private const val KEY_DEVICE_INSTALL_ID = "device_install_id"
    private const val KEY_CLAIMED_REGISTRY_JSON = "claimed_key_registry_json"
    private const val KEY_SIMULATE_EXPIRED = "simulate_trial_expired"

    /** 3 days (72 hours) in milliseconds. */
    const val TRIAL_DURATION_MS: Long = 3L * 24L * 60L * 60L * 1000L

    const val DEFAULT_DEMO_ACCOUNT = "arxdevmm@gmail.com"
    private const val HMAC_SECRET = "NETCON-VIPER-XRAY-LICENSE-V1"
    private const val KEY_PREFIX = "NETCON"
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    enum class State {
        TRIAL_ACTIVE,
        TRIAL_EXPIRED_FROZEN,
        LICENSED,
    }

    data class LicenseStatus(
        val state: State,
        val trialStartEpochMs: Long,
        val trialExpiresEpochMs: Long,
        val remainingTrialMs: Long,
        val activatedKeyMasked: String,
        val activatedAccount: String = "",
    ) {
        val isAccessAllowed: Boolean
            get() = state == State.LICENSED || state == State.TRIAL_ACTIVE

        val isFrozen: Boolean
            get() = state == State.TRIAL_EXPIRED_FROZEN

        val isLicensed: Boolean
            get() = state == State.LICENSED

        fun formatRemainingCountdown(): String {
            if (isLicensed) return "FULL LICENSE"
            if (remainingTrialMs <= 0L) return "00D 00H 00M"
            val totalMinutes = (remainingTrialMs + 59_999L) / 60_000L
            val days = totalMinutes / (24L * 60L)
            val hours = (totalMinutes % (24L * 60L)) / 60L
            val minutes = totalMinutes % 60L
            return String.format(Locale.US, "%dD %02dH %02dM", days, hours, minutes)
        }

        fun badgeSummary(): String = when (state) {
            State.LICENSED -> {
                val acctLabel = activatedAccount.ifBlank { "BOUND ACCOUNT" }
                "LICENSED ($acctLabel) · $activatedKeyMasked"
            }
            State.TRIAL_ACTIVE -> "FREE TRIAL · ${formatRemainingCountdown()} LEFT"
            State.TRIAL_EXPIRED_FROZEN -> "TRIAL EXPIRED · APK FROZEN"
        }
    }

    data class ActivationResult(
        val success: Boolean,
        val message: String,
        val status: LicenseStatus,
    )

    @JvmStatic
    fun isAccessAllowed(context: Context): Boolean {
        return evaluateStatus(context, System.currentTimeMillis()).isAccessAllowed
    }

    @JvmStatic
    @JvmOverloads
    fun evaluateStatus(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LicenseStatus {
        val prefs = prefs(context)
        return evaluateFromPreferences(
            prefs = prefs,
            packageFirstInstallMs = resolveFirstInstallTimeMs(context),
            nowEpochMs = nowEpochMs,
        )
    }

    internal fun evaluateFromPreferences(
        prefs: SharedPreferences,
        packageFirstInstallMs: Long,
        nowEpochMs: Long,
    ): LicenseStatus {
        val storedKey = prefs.getString(KEY_ACTIVATED_LICENSE, "").orEmpty().trim()
        val storedAccount = prefs.getString(KEY_ACTIVATED_ACCOUNT, "").orEmpty().trim()
        val storedStart = prefs.getLong(KEY_TRIAL_START_EPOCH_MS, 0L)
        val anchorStart = when {
            storedStart > 0L && packageFirstInstallMs > 0L -> min(storedStart, packageFirstInstallMs)
            storedStart > 0L -> storedStart
            packageFirstInstallMs > 0L -> min(nowEpochMs, packageFirstInstallMs)
            else -> nowEpochMs
        }

        val previousMaxSeen = prefs.getLong(KEY_MAX_SEEN_EPOCH_MS, anchorStart)
        val effectiveNow = max(nowEpochMs, previousMaxSeen)

        if (storedStart != anchorStart || effectiveNow > previousMaxSeen) {
            prefs.edit()
                .putLong(KEY_TRIAL_START_EPOCH_MS, anchorStart)
                .putLong(KEY_MAX_SEEN_EPOCH_MS, effectiveNow)
                .apply()
        }

        val expiresAt = anchorStart + TRIAL_DURATION_MS

        if (storedKey.isNotEmpty() && storedAccount.isNotEmpty() &&
            verifyAccountBoundLicenseKey(storedAccount, storedKey)
        ) {
            return LicenseStatus(
                state = State.LICENSED,
                trialStartEpochMs = anchorStart,
                trialExpiresEpochMs = expiresAt,
                remainingTrialMs = 0L,
                activatedKeyMasked = maskKey(storedKey),
                activatedAccount = storedAccount,
            )
        }

        val simulatedExpired = prefs.getBoolean(KEY_SIMULATE_EXPIRED, false)
        val remaining = if (simulatedExpired) 0L else max(0L, expiresAt - effectiveNow)
        val state = if (remaining > 0L) State.TRIAL_ACTIVE else State.TRIAL_EXPIRED_FROZEN

        return LicenseStatus(
            state = state,
            trialStartEpochMs = anchorStart,
            trialExpiresEpochMs = expiresAt,
            remainingTrialMs = remaining,
            activatedKeyMasked = "",
            activatedAccount = "",
        )
    }

    /**
     * Activates a license key strictly bound to [accountIdentifier] (1 Key = 1 Account).
     */
    @JvmStatic
    @JvmOverloads
    fun activateLicenseKey(
        context: Context,
        rawKey: String,
        accountIdentifier: String = DEFAULT_DEMO_ACCOUNT,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ActivationResult {
        val normalizedAccount = normalizeAccount(accountIdentifier)
        val normalizedKey = normalizeLicenseKey(rawKey)
        val prefs = prefs(context)

        if (!isValidAccountIdentifier(normalizedAccount)) {
            val current = evaluateStatus(context, nowEpochMs)
            return ActivationResult(
                success = false,
                message = "Enter a valid account email (e.g. user@domain.com) to bind your license key.",
                status = current,
            )
        }

        if (!isWellFormedKey(normalizedKey)) {
            val current = evaluateStatus(context, nowEpochMs)
            return ActivationResult(
                success = false,
                message = "Invalid license key format. Expected NETCON-XXXX-XXXX-XXXX-XXXX.",
                status = current,
            )
        }

        // Check single-account claim registry first
        val claimedOwner = getClaimedAccountForKey(prefs, normalizedKey)
        if (claimedOwner != null && claimedOwner != normalizedAccount) {
            val current = evaluateStatus(context, nowEpochMs)
            return ActivationResult(
                success = false,
                message = "This license key is already bound to another account ($claimedOwner). One key works for one account only.",
                status = current,
            )
        }

        // Verify cryptographic binding between normalizedAccount and normalizedKey
        if (!verifyAccountBoundLicenseKey(normalizedAccount, normalizedKey)) {
            val current = evaluateStatus(context, nowEpochMs)
            val parts = normalizedKey.split("-")
            val expectedAccountBlock = computeAccountBlock(normalizedAccount)
            val reason = if (parts.size == 5 && parts[1] != expectedAccountBlock) {
                "License key is issued for a different account (expected account tag $expectedAccountBlock for $normalizedAccount). One key works only for its assigned account."
            } else {
                "Invalid license key signature for account $normalizedAccount."
            }
            return ActivationResult(
                success = false,
                message = reason,
                status = current,
            )
        }

        recordClaimForKey(prefs, normalizedKey, normalizedAccount)

        prefs.edit()
            .putString(KEY_ACTIVATED_LICENSE, normalizedKey)
            .putString(KEY_ACTIVATED_ACCOUNT, normalizedAccount)
            .putLong(KEY_ACTIVATED_AT_EPOCH_MS, nowEpochMs)
            .putBoolean(KEY_SIMULATE_EXPIRED, false)
            .apply()

        val updated = evaluateStatus(context, nowEpochMs)
        return ActivationResult(
            success = true,
            message = "License key activated and locked to $normalizedAccount (${updated.activatedKeyMasked}).",
            status = updated,
        )
    }

    /**
     * Allows QA / user verification of the 3-day trial freeze screen without waiting 72 hours.
     */
    @JvmStatic
    fun setSimulatedTrialExpired(context: Context, expired: Boolean): LicenseStatus {
        val prefs = prefs(context)
        val editor = prefs.edit().putBoolean(KEY_SIMULATE_EXPIRED, expired)
        if (expired) {
            editor.remove(KEY_ACTIVATED_LICENSE)
            editor.remove(KEY_ACTIVATED_ACCOUNT)
        }
        editor.apply()
        return evaluateStatus(context)
    }

    /**
     * Resets the 3-day trial timer to a fresh 72-hour window and clears any activated license key.
     */
    @JvmStatic
    fun resetTrialWindow(context: Context, nowEpochMs: Long = System.currentTimeMillis()): LicenseStatus {
        prefs(context).edit()
            .putLong(KEY_TRIAL_START_EPOCH_MS, nowEpochMs)
            .putLong(KEY_MAX_SEEN_EPOCH_MS, nowEpochMs)
            .remove(KEY_ACTIVATED_LICENSE)
            .remove(KEY_ACTIVATED_ACCOUNT)
            .remove(KEY_ACTIVATED_AT_EPOCH_MS)
            .putBoolean(KEY_SIMULATE_EXPIRED, false)
            .apply()
        return evaluateFromPreferences(
            prefs = prefs(context),
            packageFirstInstallMs = nowEpochMs,
            nowEpochMs = nowEpochMs,
        )
    }

    /**
     * Verifies that [rawKey] (`NETCON-AAAA-PPPP-YYYY-SSSS`) is cryptographically bound to
     * [accountIdentifier], where `AAAA` is the 4-char HMAC fingerprint of [accountIdentifier]
     * and `SSSS` is the 4-char HMAC signature over `"<normalizedAccount>|AAAA-PPPP-YYYY"`.
     */
    @JvmStatic
    fun verifyAccountBoundLicenseKey(accountIdentifier: String, rawKey: String): Boolean {
        val normalizedAccount = normalizeAccount(accountIdentifier)
        if (!isValidAccountIdentifier(normalizedAccount)) return false
        val normalizedKey = normalizeLicenseKey(rawKey)
        if (!isWellFormedKey(normalizedKey)) return false

        val parts = normalizedKey.split("-")
        val g1 = parts[1]
        val g2 = parts[2]
        val g3 = parts[3]
        val sig = parts[4]

        val expectedAccountBlock = computeAccountBlock(normalizedAccount)
        if (g1 != expectedAccountBlock) return false

        val expectedSig = computeAccountSignatureBlock(normalizedAccount, g1, g2, g3)
        return sig == expectedSig
    }

    /**
     * Legacy helper that verifies against [DEFAULT_DEMO_ACCOUNT].
     */
    @JvmStatic
    fun verifyLicenseKeyFormatAndSignature(rawKey: String): Boolean {
        return verifyAccountBoundLicenseKey(DEFAULT_DEMO_ACCOUNT, rawKey)
    }

    /**
     * Generates a 1-Key-Per-1-Account signed Netcon license key (`NETCON-AAAA-PPPP-YYYY-SSSS`)
     * locked to [accountIdentifier].
     */
    @JvmStatic
    @JvmOverloads
    fun generateLicenseKeyForAccount(
        accountIdentifier: String,
        planBlock: String = "FULL",
        yearBlock: String = "2026",
    ): String {
        val normalizedAccount = normalizeAccount(accountIdentifier)
        val b1 = computeAccountBlock(normalizedAccount)
        val b2 = sanitizeBlock(planBlock)
        val b3 = sanitizeBlock(yearBlock)
        val sig = computeAccountSignatureBlock(normalizedAccount, b1, b2, b3)
        return "$KEY_PREFIX-$b1-$b2-$b3-$sig"
    }

    /**
     * Returns a deterministic official verification key bound to [DEFAULT_DEMO_ACCOUNT]
     * (`arxdevmm@gmail.com`).
     */
    @JvmStatic
    fun defaultVerificationKey(): String = generateLicenseKeyForAccount(DEFAULT_DEMO_ACCOUNT, "FULL", "2026")

    internal fun computeAccountBlock(normalizedAccount: String): String {
        return hmacToFourChars("ACCT:$normalizedAccount")
    }

    internal fun computeAccountSignatureBlock(
        normalizedAccount: String,
        block1: String,
        block2: String,
        block3: String,
    ): String {
        val payload = "$normalizedAccount|${block1.uppercase(Locale.ROOT)}-${block2.uppercase(Locale.ROOT)}-${block3.uppercase(Locale.ROOT)}"
        return hmacToFourChars(payload)
    }

    private fun hmacToFourChars(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val chars = CharArray(4)
        for (i in 0 until 4) {
            val index = (digest[i].toInt() and 0xFF) % ALPHABET.length
            chars[i] = ALPHABET[index]
        }
        return String(chars)
    }

    internal fun normalizeAccount(rawAccount: String): String {
        return rawAccount.trim().lowercase(Locale.ROOT)
    }

    private fun isValidAccountIdentifier(normalizedAccount: String): Boolean {
        return normalizedAccount.length >= 3 && !normalizedAccount.contains(" ")
    }

    private fun isWellFormedKey(normalizedKey: String): Boolean {
        val parts = normalizedKey.split("-")
        if (parts.size != 5) return false
        if (parts[0] != KEY_PREFIX) return false
        return parts.drop(1).all { it.length == 4 && isAlphaNumericBlock(it) }
    }

    private fun normalizeLicenseKey(rawKey: String): String {
        return rawKey.trim()
            .uppercase(Locale.ROOT)
            .replace(" ", "-")
    }

    private fun sanitizeBlock(raw: String): String {
        val cleaned = raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        return cleaned.padEnd(4, 'X').take(4)
    }

    private fun isAlphaNumericBlock(block: String): Boolean {
        return block.all { it in '0'..'9' || it in 'A'..'Z' }
    }

    private fun maskKey(normalizedKey: String): String {
        val parts = normalizedKey.split("-")
        return if (parts.size == 5) {
            "${parts[0]}-${parts[1]}-••••-${parts[3]}-${parts[4]}"
        } else {
            normalizedKey
        }
    }

    private fun getClaimedAccountForKey(prefs: SharedPreferences, normalizedKey: String): String? {
        val rawJson = prefs.getString(KEY_CLAIMED_REGISTRY_JSON, "{}").orEmpty()
        return try {
            val obj = JSONObject(rawJson)
            obj.optString(normalizedKey, "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun recordClaimForKey(
        prefs: SharedPreferences,
        normalizedKey: String,
        normalizedAccount: String,
    ) {
        val installId = prefs.getString(KEY_DEVICE_INSTALL_ID, null) ?: UUID.randomUUID().toString()
        val rawJson = prefs.getString(KEY_CLAIMED_REGISTRY_JSON, "{}").orEmpty()
        val obj = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            JSONObject()
        }
        obj.put(normalizedKey, normalizedAccount)
        prefs.edit()
            .putString(KEY_DEVICE_INSTALL_ID, installId)
            .putString(KEY_CLAIMED_REGISTRY_JSON, obj.toString())
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Suppress("DEPRECATION")
    private fun resolveFirstInstallTimeMs(context: Context): Long {
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.firstInstallTime.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }
}
