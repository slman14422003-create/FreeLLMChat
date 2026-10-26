package com.freellm.chat.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(
    private val context: Context,
    private val secrets: SecretStore,
    private val settings: SettingsStore
) {
    class WrongPassword : Exception()
    class BadFile : Exception()

    data class Preview(val keys: Int, val providers: Int, val settings: Int)

    suspend fun export(uri: Uri, password: CharArray) {
        val root = buildJsonObject {
            put("app", "freellm")
            put("version", 1)
            put("secrets", buildJsonObject { secrets.exportAll().forEach { (k, v) -> put(k, v) } })
            put("settings", buildJsonObject {
                settings.exportAll().forEach { (k, v) ->
                    when (v) {
                        is String -> put(k, v)
                        is Boolean -> put(k, v)
                        is List<*> -> put(k, JsonArray(v.filterIsInstance<String>().map { JsonPrimitive(it) }))
                    }
                }
            })
        }
        val blob = encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(blob) } ?: throw BadFile()
    }

    fun read(uri: Uri, password: CharArray): JsonObject {
        val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BadFile()
        val plain = decrypt(blob, password)
        val el = try { Json.parseToJsonElement(String(plain, Charsets.UTF_8)) } catch (e: Exception) { throw BadFile() }
        val obj = el as? JsonObject ?: throw BadFile()
        if (obj["app"]?.jsonPrimitive?.contentOrNull != "freellm") throw BadFile()
        return obj
    }

    fun preview(obj: JsonObject): Preview {
        val sec = obj["secrets"] as? JsonObject ?: JsonObject(emptyMap())
        val keys = sec.keys.count { it.startsWith("key_") }
        val providers = sec.keys.filter { it.contains('_') }.map { it.substringAfter('_') }.toSet().size
        val st = (obj["settings"] as? JsonObject)?.size ?: 0
        return Preview(keys, providers, st)
    }

    suspend fun apply(obj: JsonObject, replace: Boolean) {
        val sec = (obj["secrets"] as? JsonObject)?.mapNotNull { (k, v) ->
            v.jsonPrimitive.contentOrNull?.let { k to it }
        }?.toMap() ?: emptyMap()
        secrets.importAll(sec, replace)
        val st = LinkedHashMap<String, Any>()
        (obj["settings"] as? JsonObject)?.forEach { (k, v) -> toNative(v)?.let { st[k] = it } }
        settings.importAll(st)
    }

    private fun toNative(e: JsonElement): Any? = when (e) {
        is JsonArray -> e.mapNotNull { it.jsonPrimitive.contentOrNull }
        is JsonPrimitive -> e.booleanOrNull ?: e.contentOrNull
        else -> null
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size < MAGIC.size + 28 + 16 || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BadFile()
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = blob.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val body = blob.copyOfRange(MAGIC.size + 28, blob.size)
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            c.doFinal(body)
        } catch (e: Exception) {
            throw WrongPassword()
        }
    }

    companion object {
        private val MAGIC = "FLB1".toByteArray(Charsets.US_ASCII)
        private const val ITERATIONS = 210_000
    }
}
