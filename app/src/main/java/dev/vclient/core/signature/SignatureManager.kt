package dev.vclient.core.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dev.vclient.core.isolation.IsolatedPaths
import dev.vclient.core.logging.VLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

enum class SignatureState { NOT_INSTALLED, UNTRUSTED, VERIFIED, MISMATCH, ERROR }

data class SignatureStatus(
    val state: SignatureState,
    val digestHex: String? = null,
    val gameVersion: String? = null,
    val message: String = "",
)

/**
 * Verifies that the installed Minecraft APK is the legitimate, unmodified
 * release before V Client launches it (trust-on-first-use).
 *
 *  - Computes the SHA-256 of the game's signing certificate(s) via PackageManager.
 *  - The first run yields UNTRUSTED; the user reviews and trusts it once
 *    (see Home screen). The digest is then pinned in the isolated trust store.
 *  - Later runs compare against the pinned digest: VERIFIED, or MISMATCH when
 *    the game was re-signed/modified (possible tampering).
 *
 * Read-only with respect to Minecraft: no data leaves V Client's sandbox.
 */
class SignatureManager(
    private val context: Context,
    paths: IsolatedPaths,
    private val logger: VLogger,
) {

    @Serializable
    private data class TrustEntry(
        val pkg: String,
        val digest: String,
        val trustedAtMs: Long,
        val versionsSeen: List<String> = emptyList(),
    )

    @Serializable
    private data class TrustStore(val entries: List<TrustEntry> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val trustFile: File = File(paths.signatures, "trusted.json")

    private val _status = MutableStateFlow(SignatureStatus(SignatureState.NOT_INSTALLED))
    val status: StateFlow<SignatureStatus> get() = _status

    fun verifyInstalled(): SignatureStatus {
        val status = verifyInternal()
        _status.value = status
        return status
    }

    /** Pins the currently installed certificate digest as trusted. */
    fun trustCurrent(): Boolean {
        val digest = currentDigest() ?: return false
        val store = readStore()
        val now = System.currentTimeMillis()
        val existing = store.entries.firstOrNull { it.pkg == MC_PACKAGE }
        val updated = TrustEntry(
            pkg = MC_PACKAGE,
            digest = digest,
            trustedAtMs = now,
            versionsSeen = ((existing?.versionsSeen ?: emptyList()) + currentVersion()).distinct(),
        )
        IsolatedPaths.atomicWrite(
            trustFile,
            json.encodeToString(TrustStore.serializer(), TrustStore(store.entries.filterNot { it.pkg == MC_PACKAGE } + updated))
        )
        logger.i(TAG, "Trusted signature for $MC_PACKAGE ($digest)")
        verifyInstalled()
        return true
    }

    fun forgetTrust() {
        val store = readStore()
        IsolatedPaths.atomicWrite(
            trustFile,
            json.encodeToString(TrustStore.serializer(), TrustStore(store.entries.filterNot { it.pkg == MC_PACKAGE }))
        )
        verifyInstalled()
    }

    // --- internals -----------------------------------------------------------

    private fun verifyInternal(): SignatureStatus {
        val version = currentVersion() ?: return SignatureStatus(
            SignatureState.NOT_INSTALLED,
            message = "Minecraft ($MC_PACKAGE) is not installed on this device."
        )
        val digest = currentDigest() ?: return SignatureStatus(
            SignatureState.ERROR,
            gameVersion = version,
            message = "Could not read Minecraft's signing certificate."
        )
        val entry = readStore().entries.firstOrNull { it.pkg == MC_PACKAGE }
        return when {
            entry == null -> SignatureStatus(
                SignatureState.UNTRUSTED, digest, version,
                "Not trusted yet. Review the certificate digest and trust it once."
            )
            entry.digest == digest -> SignatureStatus(
                SignatureState.VERIFIED, digest, version,
                "Signature matches the trusted certificate."
            )
            else -> SignatureStatus(
                SignatureState.MISMATCH, digest, version,
                "Signature changed since it was trusted. The APK may have been modified or re-signed."
            )
        }.also {
            if (it.state == SignatureState.MISMATCH) {
                logger.w(TAG, "Signature mismatch for $MC_PACKAGE! trusted=${entry.digest} actual=$digest")
            }
        }
    }

    private fun currentVersion(): String? = runCatching {
        packageInfo()?.versionName
    }.getOrNull()

    private fun currentDigest(): String? {
        val info = packageInfo() ?: return null
        return runCatching {
            val certs: Array<out android.content.pm.Signature>? =
                if (Build.VERSION.SDK_INT >= 28) {
                    info.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    info.signatures
                }
            val first = certs?.firstOrNull() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(first.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }.getOrElse {
            logger.e(TAG, "Digest computation failed", it)
            null
        }
    }

    private fun packageInfo(): PackageInfo? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        context.packageManager.getPackageInfo(MC_PACKAGE, flags)
    }.getOrNull()

    private fun readStore(): TrustStore {
        val text = IsolatedPaths.readTextOrNull(trustFile) ?: return TrustStore()
        return runCatching { json.decodeFromString(TrustStore.serializer(), text) }.getOrDefault(TrustStore())
    }

    companion object {
        const val TAG = "Signature"
        const val MC_PACKAGE = "com.mojang.minecraftpe"
    }
}
