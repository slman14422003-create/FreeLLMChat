package com.freellm.chat.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.random.asKotlinRandom

object ExtraTools {
    val ids: Set<String> = setOf("encode", "random", "regex_test", "json_query", "text_stats")

    private const val MAX_TEXT = 20_000
    private const val MAX_OUT = 2_400
    private val rng = SecureRandom()
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pathToken = Regex("\\[(\\d+)]|([^.\\[\\]]+)")
    private val wordSplit = Regex("[\\s\\p{Punct}\\u060C\\u061B\\u061F]+")
    private val sentenceSplit = Regex("[.!?\\u061F]+")

    fun run(call: ToolCall): ToolResult? = when (call.name) {
        "encode" -> encode(call)
        "random" -> random(call)
        "regex_test" -> regexTest(call)
        "json_query" -> jsonQuery(call)
        "text_stats" -> textStats(call)
        else -> null
    }

    private fun bad(msg: String) = ToolResult(msg, false)

    private fun digest(algo: String, text: String): String =
        MessageDigest.getInstance(algo).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun encode(call: ToolCall): ToolResult {
        val op = call.str("op").lowercase().ifEmpty { "base64" }
        val text = call.args["text"] ?: call.args["value"] ?: return bad("text is required")
        if (text.length > MAX_TEXT) return bad("text is too long")
        return try {
            val out = when (op) {
                "base64" -> Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                "base64d" -> String(Base64.getDecoder().decode(text.trim()), Charsets.UTF_8)
                "url" -> URLEncoder.encode(text, "UTF-8")
                "urld" -> URLDecoder.decode(text, "UTF-8")
                "hex" -> text.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
                "hexd" -> {
                    val clean = text.filter { !it.isWhitespace() }
                    if (clean.length % 2 != 0) return bad("hex text must have an even length")
                    val bytes = ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                    String(bytes, Charsets.UTF_8)
                }
                "sha256" -> digest("SHA-256", text)
                "sha1" -> digest("SHA-1", text)
                "sha512" -> digest("SHA-512", text)
                "md5" -> digest("MD5", text)
                else -> return bad("Unknown op. Use base64, base64d, url, urld, hex, hexd, sha256, sha1, sha512 or md5.")
            }
            ToolResult(out.take(MAX_OUT))
        } catch (e: IllegalArgumentException) {
            bad("The input is not valid for " + op)
        }
    }

    private fun random(call: ToolCall): ToolResult {
        val op = call.str("op").lowercase().ifEmpty { "int" }
        return when (op) {
            "int" -> {
                var lo = (call.int("min") ?: 1).coerceIn(-1_000_000_000, 1_000_000_000)
                var hi = (call.int("max") ?: 100).coerceIn(-1_000_000_000, 1_000_000_000)
                if (lo > hi) {
                    val t = lo
                    lo = hi
                    hi = t
                }
                val n = (call.int("count") ?: 1).coerceIn(1, 20)
                val span = hi - lo + 1
                ToolResult((1..n).joinToString(", ") { (lo + rng.nextInt(span)).toString() })
            }
            "uuid" -> ToolResult(UUID.randomUUID().toString())
            "coin" -> ToolResult(if (rng.nextBoolean()) "heads" else "tails")
            "pick", "shuffle" -> {
                val items = call.str("items").split(',', '\n', '|', '\u060C').map { it.trim() }.filter { it.isNotEmpty() }
                if (items.isEmpty()) return bad("items is required, separate values with commas")
                if (op == "pick") {
                    val n = (call.int("count") ?: 1).coerceIn(1, items.size)
                    ToolResult(items.shuffled(rng.asKotlinRandom()).take(n).joinToString(", "))
                } else {
                    ToolResult(items.shuffled(rng.asKotlinRandom()).joinToString(", "))
                }
            }
            "password" -> {
                val len = (call.int("length") ?: 16).coerceIn(8, 64)
                val pool = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%^&*-_"
                ToolResult((1..len).map { pool[rng.nextInt(pool.length)] }.joinToString(""))
            }
            else -> bad("Unknown op. Use int, uuid, coin, pick, shuffle or password.")
        }
    }

    private fun regexTest(call: ToolCall): ToolResult {
        val pattern = call.str("pattern")
        if (pattern.isEmpty()) return bad("pattern is required")
        val text = (call.args["text"] ?: "").take(MAX_TEXT)
        val flags = call.str("flags").lowercase()
        val opts = HashSet<RegexOption>()
        if ('i' in flags) opts.add(RegexOption.IGNORE_CASE)
        if ('m' in flags) opts.add(RegexOption.MULTILINE)
        if ('s' in flags) opts.add(RegexOption.DOT_MATCHES_ALL)
        val rx = try {
            Regex(pattern, opts)
        } catch (e: Exception) {
            return bad("Invalid pattern: " + (e.message ?: "").lineSequence().first().take(120))
        }
        val found = rx.findAll(text).take(20).toList()
        if (found.isEmpty()) return ToolResult("No match.")
        val sb = StringBuilder()
        sb.append(found.size).append(if (found.size == 20) "+ matches" else " matches").append('\n')
        found.forEach { m ->
            sb.append(m.range.first).append(": ").append(m.value.take(120))
            val groups = m.groupValues.drop(1)
            if (groups.isNotEmpty()) sb.append("  groups=").append(groups.joinToString("|") { it.take(60) })
            sb.append('\n')
        }
        return ToolResult(sb.toString().trim().take(MAX_OUT))
    }

    private fun walk(root: JsonElement, path: String): JsonElement? {
        var cur: JsonElement = root
        val clean = path.trim().removePrefix("$").trimStart('.')
        for (m in pathToken.findAll(clean)) {
            val idx = m.groups[1]?.value
            cur = if (idx != null) {
                (cur as? JsonArray)?.getOrNull(idx.toInt()) ?: return null
            } else {
                (cur as? JsonObject)?.get(m.groupValues[2]) ?: return null
            }
        }
        return cur
    }

    private fun describe(e: JsonElement): String = when (e) {
        is JsonObject -> "object with " + e.size + " keys: " + e.keys.take(40).joinToString(", ")
        is JsonArray -> "array with " + e.size + " items"
        is JsonPrimitive -> e.content
    }

    private fun jsonQuery(call: ToolCall): ToolResult {
        val raw = call.args["json"] ?: call.args["text"] ?: return bad("json is required")
        val root = try {
            lenient.parseToJsonElement(raw.take(MAX_TEXT * 4))
        } catch (e: Exception) {
            return bad("Invalid JSON: " + (e.message ?: "").lineSequence().first().take(140))
        }
        val path = call.str("path")
        if (path.isEmpty()) return ToolResult(describe(root))
        val hit = walk(root, path) ?: return bad("Path not found: $path")
        val text = if (hit is JsonPrimitive) hit.content else hit.toString()
        return ToolResult(text.take(MAX_OUT))
    }

    private fun textStats(call: ToolCall): ToolResult {
        val text = call.args["text"] ?: return bad("text is required")
        val words = text.split(wordSplit).filter { it.isNotEmpty() }
        val lines = if (text.isEmpty()) 0 else text.lines().size
        val sentences = text.split(sentenceSplit).count { it.isNotBlank() }
        val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
        val minutes = maxOf(1, Math.ceil(words.size / 200.0).toInt())
        val top = words.map { it.lowercase() }.filter { it.length >= 3 }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5).joinToString(", ") { it.key + " x" + it.value }
        val sb = StringBuilder()
        sb.append("characters: ").append(text.length)
            .append(", without spaces: ").append(text.count { !it.isWhitespace() }).append('\n')
            .append("words: ").append(words.size).append(", unique: ").append(words.map { it.lowercase() }.toSet().size).append('\n')
            .append("lines: ").append(lines).append(", sentences: ").append(sentences).append(", paragraphs: ").append(paragraphs).append('\n')
            .append("reading time: about ").append(minutes).append(" min")
        if (top.isNotEmpty()) sb.append('\n').append("top words: ").append(top)
        return ToolResult(sb.toString())
    }

    fun weatherCode(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mostly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61, 63, 65 -> "rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown"
    }
}
