package com.mishiranu.dashchan.chan.e444

import android.net.Uri
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.SimpleEntity
import chan.util.CommonUtils
import chan.util.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Resolves `ech.u` to the IPv4 addresses it currently lives on.
 *
 * `.u` is an Unstoppable Domains name, not a DNS name, so there is nothing for the system
 * resolver to look up. The address list is stored as the `dns.A` record of a UNS token and is
 * read with an `eth_call` against the UNS proxy registry on the Base network.
 *
 * The result is cached for [CACHE_MAX_AGE]. If every RPC endpoint is unreachable an expired
 * cache is still preferred over failing outright, up to [STALE_CACHE_MAX_AGE] — the board is
 * far more likely to be reachable at its last known address than not at all.
 */
internal object E444Web3HostResolver {
    private const val WEB3_DOMAIN = "ech.u"
    private const val RECORD_DNS_A = "dns.A"
    private const val UNS_PROXY = "0xF6c1b83977DE3dEffC476f5048A0a84d3375d498"

    private const val CACHE_MAX_AGE = 5L * 60L * 1000L
    private const val STALE_CACHE_MAX_AGE = 24L * 60L * 60L * 1000L

    private const val WORD = 32
    private const val SELECTOR_LENGTH = 4

    private val BASE_RPC_URIS =
        listOf(
            "https://base.rpc.blxrbdn.com",
            "https://api.zan.top/base-mainnet",
        )

    private val IPV4_PATTERN: Pattern =
        Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)" +
                "\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$",
        )

    private val FUNCTION_SELECTOR_GET =
        Keccak256.digest("get(string,uint256)".toByteArray()).copyOf(SELECTOR_LENGTH)

    private class Cache(
        val hosts: List<String>,
        val timestamp: Long,
    )

    @Volatile
    private var cache: Cache? = null

    private val lock = Any()

    @Throws(HttpException::class)
    fun resolveHosts(preset: HttpRequest.Preset): List<String> {
        val now = System.currentTimeMillis()
        validCache(cache, now, CACHE_MAX_AGE)?.let { return it }
        synchronized(lock) {
            validCache(cache, now, CACHE_MAX_AGE)?.let { return it }
            var lastException: HttpException? = null
            for (rpcUri in BASE_RPC_URIS) {
                try {
                    val hosts = resolveHostsViaRpc(preset, Uri.parse(rpcUri))
                    if (hosts.isNotEmpty()) {
                        cache = Cache(hosts, now)
                        return hosts
                    }
                } catch (e: HttpException) {
                    lastException = e
                }
            }
            validCache(cache, now, STALE_CACHE_MAX_AGE)?.let { return it }
            throw lastException ?: HttpException(0, "Failed to resolve Web3 hosts")
        }
    }

    fun isResolvedHost(host: String): Boolean = cache?.hosts?.contains(host) == true

    private fun validCache(
        cache: Cache?,
        now: Long,
        maxAge: Long,
    ): List<String>? =
        cache
            ?.takeIf { it.hosts.isNotEmpty() && now - it.timestamp <= maxAge }
            ?.hosts

    @Throws(HttpException::class)
    private fun resolveHostsViaRpc(
        preset: HttpRequest.Preset,
        rpcUri: Uri,
    ): List<String> {
        val encodedResult = performEthCall(preset, rpcUri, encodeGetCall(RECORD_DNS_A, namehash(WEB3_DOMAIN)))
        val recordValue = decodeSingleStringResult(encodedResult)
        if (StringUtils.isEmpty(recordValue)) {
            throw HttpException(0, "Record $RECORD_DNS_A is empty for $WEB3_DOMAIN")
        }
        val hosts = parseIpv4Record(recordValue)
        if (hosts.isEmpty()) {
            throw HttpException(0, "Record $RECORD_DNS_A has no valid IPv4 hosts for $WEB3_DOMAIN")
        }
        return hosts
    }

    @Throws(HttpException::class)
    private fun performEthCall(
        preset: HttpRequest.Preset,
        rpcUri: Uri,
        callData: String,
    ): String {
        val entity = SimpleEntity()
        entity.setData(buildEthCallPayload(callData))
        entity.setContentType("application/json")
        val responseText = HttpRequest(rpcUri, preset).setPostMethod(entity).perform().readString()
        val jsonObject =
            try {
                JSONObject(responseText)
            } catch (e: JSONException) {
                throw HttpException(0, "Invalid RPC response JSON")
            }
        jsonObject.optJSONObject("error")?.let { error ->
            throw HttpException(0, CommonUtils.optJsonString(error, "message") ?: "RPC eth_call failed")
        }
        return StringUtils.nullIfEmpty(CommonUtils.optJsonString(jsonObject, "result"))
            ?: throw HttpException(0, "RPC eth_call returned empty result")
    }

    @Throws(HttpException::class)
    private fun buildEthCallPayload(callData: String): String =
        try {
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "eth_call")
                .put(
                    "params",
                    JSONArray()
                        .put(JSONObject().put("to", UNS_PROXY).put("data", callData))
                        .put("latest"),
                ).toString()
        } catch (e: JSONException) {
            throw HttpException(0, "Unable to build RPC payload")
        }

    @Throws(HttpException::class)
    private fun parseIpv4Record(recordValue: String?): List<String> {
        val jsonArray =
            try {
                JSONArray(recordValue)
            } catch (e: JSONException) {
                throw HttpException(0, "Record $RECORD_DNS_A is not a valid JSON array")
            }
        val hosts = LinkedHashSet<String>()
        for (i in 0 until jsonArray.length()) {
            val host = jsonArray.optString(i).trim()
            if (host.isNotEmpty() && IPV4_PATTERN.matcher(host).matches()) {
                hosts.add(host)
            }
        }
        return hosts.toList()
    }

    /**
     * ERC-137 name hashing: `keccak(keccak(...) ‖ keccak(label))` folded right to left, with the
     * empty name hashing to 32 zero bytes.
     */
    private fun namehash(domain: String): ByteArray {
        if (domain.isEmpty()) {
            return ByteArray(Keccak256.DIGEST_LENGTH)
        }
        val separator = domain.indexOf('.')
        val label = if (separator >= 0) domain.substring(0, separator) else domain
        val remainder = if (separator >= 0) domain.substring(separator + 1) else ""
        return Keccak256.digest(namehash(remainder) + Keccak256.digest(label.lowercase().toByteArray()))
    }

    /**
     * ABI-encodes `get(string key, uint256 tokenId)`. The token id is the raw 32-byte namehash,
     * which is already exactly one ABI word, so no bignum conversion is involved.
     */
    private fun encodeGetCall(
        key: String,
        tokenId: ByteArray,
    ): String {
        val keyBytes = key.toByteArray()
        val paddedKeyLength = (keyBytes.size + WORD - 1) / WORD * WORD
        val payload = ByteArray(SELECTOR_LENGTH + 3 * WORD + paddedKeyLength)
        FUNCTION_SELECTOR_GET.copyInto(payload)
        // The dynamic `string` argument is a tail offset: two head words follow the selector.
        writeUInt256(payload, SELECTOR_LENGTH, 2L * WORD)
        tokenId.copyInto(payload, SELECTOR_LENGTH + WORD)
        writeUInt256(payload, SELECTOR_LENGTH + 2 * WORD, keyBytes.size.toLong())
        keyBytes.copyInto(payload, SELECTOR_LENGTH + 3 * WORD)
        return "0x" + payload.toHexString()
    }

    @Throws(HttpException::class)
    private fun decodeSingleStringResult(encoded: String): String? {
        val data = decodeHex(encoded)
        if (data.isEmpty()) {
            return null
        }
        if (data.size < 2 * WORD) {
            throw HttpException(0, "Invalid ABI response data")
        }
        val offset = readUInt256AsInt(data, 0)
        if (offset + WORD > data.size) {
            throw HttpException(0, "Invalid ABI response data")
        }
        val length = readUInt256AsInt(data, offset)
        val start = offset + WORD
        if (start + length > data.size) {
            throw HttpException(0, "Invalid ABI response data")
        }
        return StringUtils.nullIfEmpty(String(data, start, length, Charsets.UTF_8))
    }

    private fun writeUInt256(
        output: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (i in 0 until 8) {
            output[offset + WORD - 1 - i] = (value ushr 8 * i).toByte()
        }
    }

    @Throws(HttpException::class)
    private fun readUInt256AsInt(
        input: ByteArray,
        offset: Int,
    ): Int {
        if (offset < 0 || offset + WORD > input.size) {
            throw HttpException(0, "Invalid ABI response data")
        }
        // Anything that does not fit in the low four bytes is not a length or an offset we can use.
        for (i in offset until offset + WORD - 4) {
            if (input[i] != 0.toByte()) {
                throw HttpException(0, "Invalid ABI response data")
            }
        }
        var value = 0
        for (i in offset + WORD - 4 until offset + WORD) {
            value = value shl 8 or (input[i].toInt() and 0xff)
        }
        if (value < 0) {
            throw HttpException(0, "Invalid ABI response data")
        }
        return value
    }

    @Throws(HttpException::class)
    private fun decodeHex(value: String): ByteArray {
        val hex = if (value.startsWith("0x") || value.startsWith("0X")) value.substring(2) else value
        if (hex.length % 2 != 0) {
            throw HttpException(0, "Invalid ABI response data")
        }
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = (hexDigit(hex[i * 2]) shl 4 or hexDigit(hex[i * 2 + 1])).toByte()
        }
        return result
    }

    @Throws(HttpException::class)
    private fun hexDigit(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw HttpException(0, "Invalid HEX")
        }

    private fun ByteArray.toHexString(): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(size * 2)
        for (i in indices) {
            val value = this[i].toInt() and 0xff
            chars[i * 2] = digits[value ushr 4]
            chars[i * 2 + 1] = digits[value and 0x0f]
        }
        return String(chars)
    }
}
