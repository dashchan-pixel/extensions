package com.mishiranu.dashchan.chan.e444

/**
 * Keccak-256, the hash Ethereum name hashing is built on.
 *
 * This is the original Keccak padding (`0x01 … 0x80`), **not** the `0x06` padding of the
 * later SHA-3 standard, so [java.security.MessageDigest] with `SHA3-256` is not a substitute.
 * It is implemented here rather than pulled in from Bouncy Castle because the two call sites
 * ([E444Web3HostResolver]) need exactly one 32-byte digest of a short input, and bundling a
 * general-purpose crypto provider for that costs far more than it saves.
 */
internal object Keccak256 {
    const val DIGEST_LENGTH = 32

    /** Bitrate in bytes: (1600 - 2 * 256) / 8. */
    private const val RATE = 136

    private const val LANES = 25
    private const val ROUNDS = 24

    private val ROUND_CONSTANTS =
        longArrayOf(
            0x0000000000000001uL.toLong(),
            0x0000000000008082uL.toLong(),
            0x800000000000808AuL.toLong(),
            0x8000000080008000uL.toLong(),
            0x000000000000808BuL.toLong(),
            0x0000000080000001uL.toLong(),
            0x8000000080008081uL.toLong(),
            0x8000000000008009uL.toLong(),
            0x000000000000008AuL.toLong(),
            0x0000000000000088uL.toLong(),
            0x0000000080008009uL.toLong(),
            0x000000008000000AuL.toLong(),
            0x000000008000808BuL.toLong(),
            0x800000000000008BuL.toLong(),
            0x8000000000008089uL.toLong(),
            0x8000000000008003uL.toLong(),
            0x8000000000008002uL.toLong(),
            0x8000000000000080uL.toLong(),
            0x000000000000800AuL.toLong(),
            0x800000008000000AuL.toLong(),
            0x8000000080008081uL.toLong(),
            0x8000000000008080uL.toLong(),
            0x0000000080000001uL.toLong(),
            0x8000000080008008uL.toLong(),
        )

    /** Rho rotation offsets, indexed as `[x * 5 + y]`. */
    private val ROTATION =
        intArrayOf(
            0,
            36,
            3,
            41,
            18,
            1,
            44,
            10,
            45,
            2,
            62,
            6,
            43,
            15,
            61,
            28,
            55,
            25,
            21,
            56,
            27,
            20,
            39,
            8,
            14,
        )

    fun digest(input: ByteArray): ByteArray {
        val blocks = pad(input)
        val state = LongArray(LANES)
        var offset = 0
        while (offset < blocks.size) {
            for (lane in 0 until RATE / Long.SIZE_BYTES) {
                state[lane] = state[lane] xor readLongLe(blocks, offset + lane * Long.SIZE_BYTES)
            }
            permute(state)
            offset += RATE
        }
        val digest = ByteArray(DIGEST_LENGTH)
        for (lane in 0 until DIGEST_LENGTH / Long.SIZE_BYTES) {
            writeLongLe(digest, lane * Long.SIZE_BYTES, state[lane])
        }
        return digest
    }

    private fun pad(input: ByteArray): ByteArray {
        val padded = ByteArray((input.size / RATE + 1) * RATE)
        input.copyInto(padded)
        padded[input.size] = 0x01
        padded[padded.size - 1] = (padded[padded.size - 1].toInt() or 0x80).toByte()
        return padded
    }

    private fun permute(state: LongArray) {
        val parity = LongArray(5)
        val theta = LongArray(5)
        val scratch = LongArray(LANES)
        for (round in 0 until ROUNDS) {
            for (x in 0 until 5) {
                parity[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
            }
            for (x in 0 until 5) {
                theta[x] = parity[(x + 4) % 5] xor rotateLeft(parity[(x + 1) % 5], 1)
            }
            for (y in 0 until 5) {
                for (x in 0 until 5) {
                    state[x + 5 * y] = state[x + 5 * y] xor theta[x]
                }
            }
            for (y in 0 until 5) {
                for (x in 0 until 5) {
                    scratch[y + 5 * ((2 * x + 3 * y) % 5)] = rotateLeft(state[x + 5 * y], ROTATION[x * 5 + y])
                }
            }
            for (y in 0 until 5) {
                for (x in 0 until 5) {
                    state[x + 5 * y] =
                        scratch[x + 5 * y] xor (scratch[(x + 1) % 5 + 5 * y].inv() and scratch[(x + 2) % 5 + 5 * y])
                }
            }
            state[0] = state[0] xor ROUND_CONSTANTS[round]
        }
    }

    /**
     * A shift count is masked to its low 6 bits, so `bits == 0` leaves the value untouched
     * instead of clearing it.
     */
    private fun rotateLeft(
        value: Long,
        bits: Int,
    ): Long = value shl bits or (value ushr (Long.SIZE_BITS - bits))

    private fun readLongLe(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var value = 0L
        for (i in 7 downTo 0) {
            value = value shl 8 or (bytes[offset + i].toLong() and 0xff)
        }
        return value
    }

    private fun writeLongLe(
        bytes: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (i in 0 until 8) {
            bytes[offset + i] = (value ushr 8 * i).toByte()
        }
    }
}
