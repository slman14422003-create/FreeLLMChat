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
    private val settings: SettingsStore,
    private val dao: com.freellm.chat.data.db.ChatDao? = null,
    private val attachments: AttachmentStore? = null
) {
    class WrongPassword : Exception()
    class BadFile : Exception()

    data class Preview(val keys: Int, val providers: Int, val settings: Int, val chats: Int = 0, val messages: Int = 0)

    private suspend fun chatsJson(withFiles: Boolean): Pair<JsonArray, JsonObject> {
        val d = dao ?: return JsonArray(emptyList()) to JsonObject(emptyMap())
        val files = LinkedHashMap<String, JsonElement>()
        var budget = MAX_FILES_BYTES
        val list = d.allConversationsOnce().map { c ->
            val msgs = d.messagesOnce(c.id).map { m ->
                var att = m.attachments
                if (withFiles && !att.isNullOrBlank()) {
                    AttachmentCodec.decode(att).forEach { a ->
                        val f = java.io.File(a.path)
                        if (a.path !in files && f.isFile && f.length() <= budget) {
                            budget -= f.length()
                            files[a.path] = JsonPrimitive(android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                        }
                    }
                } else {
                    att = null
                }
                buildJsonObject {
                    put("r", m.role)
                    put("c", m.content)
                    put("ca", m.createdAt)
                    m.modelName?.let { put("mn", it) }
                    m.providerName?.let { put("pn", it) }
                    m.modelKey?.let { put("mk", it) }
                    m.notes?.let { put("n", it) }
                    m.sources?.let { put("so", it) }
                    att?.let { put("a", it) }
                }
            }
            buildJsonObject {
                put("t", c.title)
                put("c", c.createdAt)
                put("u", c.updatedAt)
                c.pinnedModel?.let { put("p", it) }
                put("s", c.starred)
                c.instructions?.let { put("i", it) }
                c.summary?.let { put("sum", it) }
                put("m", JsonArray(msgs))
            }
        }
        return JsonArray(list) to JsonObject(files)
    }

    private suspend fun importChats(obj: JsonObject) {
        val d = dao ?: return
        val chats = obj["chats"] as? JsonArray ?: return
        val files = obj["files"] as? JsonObject
        val remap = HashMap<String, String>()
        val store = attachments
        fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull
        fun lng(o: JsonObject, k: String): Long = str(o, k)?.toLongOrNull() ?: 0L
        for (el in chats) {
            val c = el as? JsonObject ?: continue
            val cid = d.insertConversation(
                com.freellm.chat.data.db.ConversationEntity(
                    title = str(c, "t").orEmpty(),
                    createdAt = lng(c, "c"),
                    updatedAt = lng(c, "u"),
                    pinnedModel = str(c, "p"),
                    starred = (c["s"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    instructions = str(c, "i"),
                    summary = str(c, "sum")
                )
            )
            for (me in (c["m"] as? JsonArray).orEmpty()) {
                val m = me as? JsonObject ?: continue
                var att: String? = null
                val raw = str(m, "a")
                if (!raw.isNullOrBlank() && files != null && store != null) {
                    val list = AttachmentCodec.decode(raw).mapNotNull { a ->
                        val path = remap[a.path] ?: run {
                            val b64 = (files[a.path] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                            val ext = a.path.substringAfterLast('.', "bin")
                            val np = store.storeBytes(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP), ext)
                            remap[a.path] = np
                            np
                        }
                        a.copy(path = path)
                    }
                    att = AttachmentCodec.encode(list)
                }
                d.insertMessage(
                    com.freellm.chat.data.db.MessageEntity(
                        conversationId = cid,
                        role = str(m, "r").orEmpty(),
                        content = str(m, "c").orEmpty(),
                        modelName = str(m, "mn"),
                        providerName = str(m, "pn"),
                        modelKey = str(m, "mk"),
                        notes = str(m, "n"),
                        createdAt = lng(m, "ca"),
                        attachments = att,
                        sources = str(m, "so")
                    )
                )
            }
        }
    }

    suspend fun export(uri: Uri, password: CharArray, includeChats: Boolean = false, includeFiles: Boolean = false) {
        val chatData = if (includeChats) chatsJson(includeFiles) else null
        val root = buildJsonObject {
            put("app", "freellm")
            put("version", 1)
            put("secrets", buildJsonObject { secrets.exportAll().forEach { (k, v) -> put(k, v) } })
            if (chatData != null) {
                put("chats", chatData.first)
                if (includeFiles) put("files", chatData.second)
            }
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
        val providers = countProviders(sec.keys)
        val st = (obj["settings"] as? JsonObject)?.size ?: 0
        val chats = (obj["chats"] as? JsonArray).orEmpty()
        val messages = chats.sumOf { ((it as? JsonObject)?.get("m") as? JsonArray)?.size ?: 0 }
        return Preview(keys, providers, st, chats.size, messages)
    }

    suspend fun apply(obj: JsonObject, replace: Boolean) {
        val sec = (obj["secrets"] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
        }?.toMap() ?: emptyMap()
        secrets.importAll(sec, replace)
        val st = LinkedHashMap<String, Any>()
        (obj["settings"] as? JsonObject)?.forEach { (k, v) -> toNative(v)?.let { st[k] = it } }
        settings.importAll(st)
        importChats(obj)
    }

    private fun toNative(e: JsonElement): Any? = when (e) {
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
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
        private const val MAX_FILES_BYTES = 40L * 1024 * 1024

        fun countProviders(secretKeys: Collection<String>): Int =
            secretKeys.filter { it.startsWith("key_") }.map { it.removePrefix("key_") }.filter { it.isNotEmpty() }.toSet().size

        private val MAGIC = "FLB1".toByteArray(Charsets.US_ASCII)
        private const val ITERATIONS = 210_000
    }
}
