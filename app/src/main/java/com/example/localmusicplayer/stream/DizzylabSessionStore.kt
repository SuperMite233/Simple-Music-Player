package com.supermite.smp.stream

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Keystore-backed account storage. Never included in the player's settings export. */
class DizzylabSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dizzylab_session", Context.MODE_PRIVATE)
    private var lastSaved: DizzylabSavedSession? = null

    @Synchronized
    fun read(): DizzylabSavedSession? = runCatching {
        val value = prefs.getString("session", null) ?: return null
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        DizzylabSavedSession(json.optString("cookie"), json.optString("token"), json.optString("uid")).also { lastSaved = it }
    }.getOrNull()

    @Synchronized
    fun save(cookie: String, token: String, uid: String) {
        val session = DizzylabSavedSession(cookie, token, uid)
        if (cookie.isBlank() && token.isBlank()) { prefs.edit().clear().apply(); lastSaved = null; return }
        if (session == lastSaved) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = JSONObject().put("cookie", cookie).put("token", token).put("uid", uid).toString()
        val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("session", Base64.encodeToString(data, Base64.NO_WRAP)).commit()) {
            "无法保存 Dizzylab 登录状态"
        }
        lastSaved = session
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object { private const val ALIAS = "smp.dizzylab.session.v1" }
}

data class DizzylabSavedSession(val cookie: String, val token: String, val uid: String)
