package com.mishiranu.dashchan.chan.e444

import android.util.Base64
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The board's "private text" ([secret] tag): parts of a comment that only chosen recipients can
 * read. The server delivers them already rendered as
 * `<span class="secret-text" data-encrypted="…" data-keys="…">[placeholder]</span>`, and the
 * site's board.js decrypts them in the browser from the reader's `usercode_auth` cookie. This is a
 * faithful port of that `decryptSecretText()` / `.secret-text` click handler, so the app can reveal
 * messages addressed to the logged-in user inline instead of showing an opaque placeholder.
 *
 * The scheme is two layers of the same primitive. A payload is
 * `base64( sha256(inner)[32 raw bytes] || xor(inner, key) )`, where `inner = base64(utf8(text))` and
 * the leading hash both authenticates the plaintext and tells a reader whether `key` was right. The
 * message is encrypted under a random per-message key; that key is then encrypted once per recipient
 * with the recipient's `usercode_auth`, and those wrapped keys are the comma-separated `data-keys`.
 * A reader recovers the message key from whichever `data-keys` slot their usercode decrypts, then
 * decrypts `data-encrypted` with it.
 *
 * Every primitive here mirrors the browser's exact behaviour (its own base64, its own SHA-256 over
 * UTF-8 code units, its `charCodeAt` XOR), because the pieces are chained: a byte that differs from
 * the JS in the key-unwrap layer would silently corrupt the message layer.
 */
internal object SecretText {
    private const val HASH_PREFIX_LENGTH = 32

    private val HEX = "0123456789abcdef".toCharArray()

    /** One `[secret]` span; group 1 is its inner placeholder, kept for the "not for you" case. */
    private val SPAN: Pattern =
        Pattern.compile(
            "<span\\b[^>]*\\bclass=\"secret-text\"[^>]*>(.*?)</span>",
            Pattern.DOTALL,
        )
    private val DATA_ENCRYPTED: Pattern = Pattern.compile("\\bdata-encrypted=\"([^\"]*)\"")
    private val DATA_KEYS: Pattern = Pattern.compile("\\bdata-keys=\"([^\"]*)\"")
    private val NON_BASE64: Regex = Regex("[^A-Za-z0-9+/=]")

    /**
     * Kept on both revealed and locked private text: E444ChanMarkup maps `span.secret-text` to
     * TAG_CAPCODE, so the app draws it in the theme's capcode colour. The board's own data-* markup
     * is dropped — it is only needed to decrypt, which has already happened here.
     */
    private const val MARK_OPEN = "<span class=\"secret-text\">"
    private const val MARK_CLOSE = "</span>"

    /**
     * Marks every private-text span so it stands out in the capcode colour: the decrypted text when
     * a `data-keys` slot opens with the reader's [usercode], the board's placeholder otherwise
     * (including when there is no usercode at all, so private posts are still flagged). Returns
     * [comment] unchanged when it holds no private text, so the common path costs one `contains`.
     */
    fun reveal(
        comment: String?,
        usercode: String?,
    ): String? {
        if (comment == null || !comment.contains("secret-text")) {
            return comment
        }
        val matcher = SPAN.matcher(comment)
        if (!matcher.find()) {
            return comment
        }
        val result = StringBuffer(comment.length)
        do {
            val revealed = if (usercode.isNullOrEmpty()) null else revealSpan(matcher.group(), usercode)
            // Decrypted text for a recipient, the board's placeholder (group 1) otherwise.
            val content = revealed ?: matcher.group(1).orEmpty()
            matcher.appendReplacement(result, Matcher.quoteReplacement(MARK_OPEN + content + MARK_CLOSE))
        } while (matcher.find())
        matcher.appendTail(result)
        return result.toString()
    }

    /** The decrypted text of one span, or null when no `data-keys` slot opens with [usercode]. */
    private fun revealSpan(
        span: String,
        usercode: String,
    ): String? {
        val encrypted = firstGroup(DATA_ENCRYPTED, span) ?: return null
        val keys = firstGroup(DATA_KEYS, span) ?: return null
        for (wrappedKey in keys.split(',')) {
            val messageKey = decrypt(wrappedKey, usercode)
            if (messageKey.isNullOrEmpty()) {
                continue
            }
            val text = decrypt(encrypted, messageKey)
            if (!text.isNullOrEmpty()) {
                return text
            }
        }
        return null
    }

    private fun firstGroup(
        pattern: Pattern,
        input: String,
    ): String? {
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Undoes one layer: `base64 -> [32-byte hash | xor(inner, key)] -> verify -> base64-decode`.
     * Null means the payload was malformed or [key] did not authenticate it — i.e. the wrong key,
     * which is how the caller learns a `data-keys` slot is not the reader's.
     */
    private fun decrypt(
        ciphertext: String,
        key: String,
    ): String? {
        if (ciphertext.isEmpty() || key.isEmpty()) {
            return null
        }
        val decoded = base64ToBytes(ciphertext) ?: return null
        if (decoded.size < HASH_PREFIX_LENGTH) {
            return null
        }
        val innerLength = decoded.size - HASH_PREFIX_LENGTH
        val inner = CharArray(innerLength)
        for (i in 0 until innerLength) {
            // charCodeAt semantics: the byte and the key's UTF-16 code unit, XORed.
            inner[i] = ((decoded[HASH_PREFIX_LENGTH + i].toInt() and 0xFF) xor key[i % key.length].code).toChar()
        }
        val innerString = String(inner)
        if (sha256Hex(innerString) != hex(decoded, HASH_PREFIX_LENGTH)) {
            return null
        }
        return base64Decode(innerString)
    }

    /** JS `atob`: base64 text to raw bytes, tolerant of a malformed payload (null, not a throw). */
    private fun base64ToBytes(base64: String): ByteArray? =
        try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            // A malformed payload is simply not decryptable, exactly as the board's JS treats it.
            null
        }

    /**
     * The board's `Base64.decode`: strip anything outside the alphabet, decode, then read the bytes
     * back as UTF-8. Kept as its own step because [decrypt]'s XOR output is a base64 *string*.
     */
    private fun base64Decode(text: String): String? {
        val bytes = base64ToBytes(NON_BASE64.replace(text, "")) ?: return null
        return utf8Decode(bytes)
    }

    /** Lowercase hex of [count] bytes from the front of [bytes], matching the stored hash format. */
    private fun hex(
        bytes: ByteArray,
        count: Int,
    ): String {
        val out = CharArray(count * 2)
        for (i in 0 until count) {
            val v = bytes[i].toInt() and 0xFF
            out[2 * i] = HEX[v ushr 4]
            out[2 * i + 1] = HEX[v and 0xF]
        }
        return String(out)
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(utf8Encode(text))
        return hex(digest, digest.size)
    }

    /**
     * The board's `Utf8Encode`: per UTF-16 code unit, 1–3 bytes, with CRLF folded to LF. Deliberately
     * not [String.toByteArray] — that pairs surrogates into 4-byte sequences, which the site's hash
     * (and therefore the stored one it is checked against) never does.
     */
    private fun utf8Encode(input: String): ByteArray {
        val normalized = input.replace("\r\n", "\n")
        val out = ArrayList<Byte>(normalized.length)
        for (ch in normalized) {
            val c = ch.code
            when {
                c < 0x80 -> out.add(c.toByte())
                c < 0x800 -> {
                    out.add(((c shr 6) or 0xC0).toByte())
                    out.add(((c and 0x3F) or 0x80).toByte())
                }
                else -> {
                    out.add(((c shr 12) or 0xE0).toByte())
                    out.add((((c shr 6) and 0x3F) or 0x80).toByte())
                    out.add(((c and 0x3F) or 0x80).toByte())
                }
            }
        }
        return out.toByteArray()
    }

    /**
     * The board's `_utf8_decode`, byte-for-byte: the recovered message key is fed straight back in
     * as the next layer's XOR key, so its decoding has to match the JS even for the continuation
     * bytes a standard UTF-8 decoder would reject.
     */
    private fun utf8Decode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val c = bytes[i].toInt() and 0xFF
            when {
                c < 0x80 -> {
                    out.append(c.toChar())
                    i += 1
                }
                c in 0xC0..0xDF -> {
                    val c2 = byteAt(bytes, i + 1)
                    out.append((((c and 0x1F) shl 6) or (c2 and 0x3F)).toChar())
                    i += 2
                }
                else -> {
                    val c2 = byteAt(bytes, i + 1)
                    val c3 = byteAt(bytes, i + 2)
                    out.append((((c and 0x0F) shl 12) or ((c2 and 0x3F) shl 6) or (c3 and 0x3F)).toChar())
                    i += 3
                }
            }
        }
        return out.toString()
    }

    /** `charCodeAt` past the end is NaN, and `NaN & 0x3F` is 0 — so a truncated tail reads as zero. */
    private fun byteAt(
        bytes: ByteArray,
        index: Int,
    ): Int = if (index < bytes.size) bytes[index].toInt() and 0xFF else 0
}
