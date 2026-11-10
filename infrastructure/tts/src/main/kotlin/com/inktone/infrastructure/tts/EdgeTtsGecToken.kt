package com.inktone.infrastructure.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Jeton `Sec-MS-GEC` v2 du endpoint Edge Read Aloud (ADR-024).
 *
 * Depuis mi-octobre 2026, le service rejette le jeton v1 (SHA-256 de
 * `{ticks}{token}`) par un 403 au handshake WebSocket. Le jeton v2 a la forme
 * `<16 hex>-<64 hex>` : un condensé « CS64 » suivi d'un SHA-256 dont l'état
 * initial et le calendrier de messages sont altérés.
 *
 * Port fidèle de `generate_sec_ms_gec_v2` (projet Python `edge-tts` >= 7.3.0,
 * module `drm.py`). Aucune de ces constantes n'est « corrigeable » à la main :
 * toute divergence d'un bit produit un 403. Les vecteurs de test de
 * `EdgeTtsGecTokenTest` viennent de l'implémentation Python de référence.
 *
 * Toute l'arithmétique est sur 32 bits avec débordement (`Int`), ce qui
 * équivaut aux masques `& 0xFFFFFFFF` de la référence.
 */
internal object EdgeTtsGecToken {

    private const val WIN_EPOCH_SECONDS = 11_644_473_600L
    private const val TICKS_PER_SECOND = 10_000_000L
    private const val WINDOW_SECONDS = 300L

    private val SHA256_INITIAL = intArrayOf(
        0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(),
        0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19,
    )
    private val SHA256_STATE = intArrayOf(
        0xFD9FBEA2.toInt(), 0x91243CE7.toInt(), 0xBE499094.toInt(), 0xCD2E1E0A.toInt(),
        0x6C006F49, 0x04BD35AB, 0xFEB0A7E4.toInt(), 0x03EFFBD2,
    )
    private val SHA256_SCHEDULE = intArrayOf(
        0xEB24BF9E.toInt(), 0x1558691F, 0xA0C09EE2.toInt(), 0x0CFA32BA,
        0xA622EE8C.toInt(), 0xD1DD9491.toInt(), 0xC0DD25A0.toInt(), 0xF9E70AB6.toInt(),
        0xF0C31573.toInt(), 0x7058CE4E, 0x7188E606, 0x00E5AB33,
        0xD258B060.toInt(), 0x72766564,
    )
    private val SHA256_ROUND_CONSTANTS = intArrayOf(
        0x428A2F98, 0x71374491, 0xB5C0FBCF.toInt(), 0xE9B5DBA5.toInt(),
        0x3956C25B, 0x59F111F1, 0x923F82A4.toInt(), 0xAB1C5ED5.toInt(),
        0xD807AA98.toInt(), 0x12835B01, 0x243185BE, 0x550C7DC3,
        0x72BE5D74, 0x80DEB1FE.toInt(), 0x9BDC06A7.toInt(), 0xC19BF174.toInt(),
        0xE49B69C1.toInt(), 0xEFBE4786.toInt(), 0x0FC19DC6, 0x240CA1CC,
        0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152.toInt(), 0xA831C66D.toInt(), 0xB00327C8.toInt(), 0xBF597FC7.toInt(),
        0xC6E00BF3.toInt(), 0xD5A79147.toInt(), 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
        0x650A7354, 0x766A0ABB, 0x81C2C92E.toInt(), 0x92722C85.toInt(),
        0xA2BFE8A1.toInt(), 0xA81A664B.toInt(), 0xC24B8B70.toInt(), 0xC76C51A3.toInt(),
        0xD192E819.toInt(), 0xD6990624.toInt(), 0xF40E3585.toInt(), 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
        0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814.toInt(), 0x8CC70208.toInt(),
        0x90BEFFFA.toInt(), 0xA4506CEB.toInt(), 0xBEF9A3F7.toInt(), 0xC67178F2.toInt(),
    )
    private const val SHA256_ADJUSTMENT = 0xC9DC06A7.toInt()
    private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80
    private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0

    /** Constantes de blocs : la ronde 14 utilise la constante ajustée. */
    private val SHA256_BLOCK_CONSTANTS: IntArray =
        SHA256_ROUND_CONSTANTS.copyOf().also { it[14] = SHA256_ADJUSTMENT }

    private val CS64_COEFFICIENTS = intArrayOf(
        0xE82E91B1.toInt(), 0xB32700D0.toInt(),
        0x44DE24C6, 0x88DFF51D.toInt(),
        0x3BEB65BC, 0x29514038,
        0xC89C5933.toInt(), 0xB328CBDF.toInt(),
        0x8D1EC211.toInt(), 0xD405B4D5.toInt(),
        0x314EF5D5, 0x26E55F3C,
        0xE92491B1.toInt(), 0x607801C6,
        0xF0A5B9D5.toInt(), 0x82B2C1FB.toInt(),
        0x06064CF3, 0xFA51A3DA.toInt(),
        0xDA79FA60.toInt(), 0x207CC464,
        0xBED20560.toInt(), 0x3C3C74F3,
        0x89F9C9F8.toInt(), 0xD2D14B61.toInt(),
        0x35FA38C6, 0xBB04A2C1.toInt(),
        0xCB49E3C6.toInt(), 0xC5626167.toInt(),
    ) // paires (coefficient, décalage), 14 paires
    private const val CS64_FACTOR = 0x82893933.toInt()
    private const val CS64_ADJUSTMENT0 = 0xF2F90B1A.toInt()
    private const val CS64_ADJUSTMENT1 = 0x0B1A0000

    /**
     * Jeton pour un instant donné (secondes Unix). Arrondi à la fenêtre de
     * 5 minutes précédente, comme la référence.
     */
    fun generate(unixSeconds: Long, trustedClientToken: String): String {
        val winSeconds = unixSeconds + WIN_EPOCH_SECONDS
        val ticks = (winSeconds - Math.floorMod(winSeconds, WINDOW_SECONDS)) * TICKS_PER_SECOND
        val data = "$ticks$trustedClientToken".toByteArray(Charsets.US_ASCII)
        val digest = sha256Digest(data)
        val (cs64a, cs64b) = cs64Digest(data, digest)
        val cs64 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cs64a).putInt(cs64b).array()
        return hexUpper(cs64) + "-" + hexUpper(digestBytes(digest))
    }

    // ---- SHA-256 altéré ---------------------------------------------------

    private fun rotr(v: Int, n: Int): Int = (v ushr n) or (v shl (32 - n))
    private fun rol16(v: Int): Int = (v shl 16) or (v ushr 16)

    private fun sigma0(x: Int): Int = rotr(x, 7) xor rotr(x, 18) xor (x ushr 3)
    private fun sigma1(x: Int): Int = rotr(x, 17) xor rotr(x, 19) xor (x ushr 10)

    private fun sha256Rounds(state: IntArray, words: IntArray, firstRound: Int): IntArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        val k = if (firstRound == 14) SHA256_BLOCK_CONSTANTS else SHA256_ROUND_CONSTANTS
        for (i in firstRound until 64) {
            val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val ch = g xor (e and (f xor g))
            val t1 = h + s1 + ch + k[i] + words[i]
            val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val maj = (a and b) or (c and (a or b))
            val t2 = s0 + maj
            h = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    /** Étend le calendrier de messages de [from] à 63 (tableau de 64 mots). */
    private fun expandSchedule(words: IntArray, from: Int) {
        for (i in from until 64) {
            words[i] = sigma1(words[i - 2]) + words[i - 7] + sigma0(words[i - 15]) + words[i - 16]
        }
    }

    /** État initial dérivé des 7 premiers octets du matériau à condenser. */
    private fun sha256Init(prefix: ByteArray): IntArray {
        val w = IntArray(64)
        w[14] = ((prefix[0].toInt() and 0xFF) shl 16) or
            ((prefix[1].toInt() and 0xFF) shl 8) or (prefix[2].toInt() and 0xFF)
        w[15] = ((prefix[3].toInt() and 0xFF) shl 24) or
            ((prefix[4].toInt() and 0xFF) shl 16) or
            ((prefix[5].toInt() and 0xFF) shl 8) or (prefix[6].toInt() and 0xFF)
        for (index in 16 until 30) {
            var value = SHA256_SCHEDULE[index - 16]
            if (index - 2 >= 14) {
                val s = sigma1(w[index - 2])
                value += if (index == 16) s xor SHA256_SIGMA1_ADJUSTMENT else s
            }
            if (index - 7 >= 14) value += w[index - 7]
            if (index - 15 >= 14) {
                val s = sigma0(w[index - 15])
                value += if (index == 29) s xor SHA256_SIGMA0_ADJUSTMENT else s
            }
            w[index] = value
        }
        w[30] = sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] +
            SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14]
        expandSchedule(w, 31)
        val working = sha256Rounds(SHA256_STATE, w, 14)
        return IntArray(8) { SHA256_INITIAL[it] + working[it] }
    }

    private fun sha256Digest(data: ByteArray): IntArray {
        require(data.size >= 7) { "Le sel du jeton doit contenir au moins sept octets" }
        val length = 57L + data.size
        val zeros = Math.floorMod(55L - length, 64L).toInt()
        val tail = data.size - 7
        val padded = ByteArray(tail + 1 + zeros + 8)
        System.arraycopy(data, 7, padded, 0, tail)
        padded[tail] = 0x80.toByte()
        ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN).putLong(padded.size - 8, length * 8)

        var state = sha256Init(data.copyOfRange(0, 7))
        val buf = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN)
        for (offset in padded.indices step 64) {
            val w = IntArray(64)
            for (i in 0 until 16) w[i] = buf.getInt(offset + i * 4)
            expandSchedule(w, 16)
            val working = sha256Rounds(state, w, 0)
            state = IntArray(8) { state[it] + working[it] }
        }
        return state
    }

    private fun digestBytes(state: IntArray): ByteArray {
        val out = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        state.forEach { out.putInt(it) }
        return out.array()
    }

    // ---- CS64 -------------------------------------------------------------

    private fun cs64Digest(data: ByteArray, digestState: IntArray): Pair<Int, Int> {
        // Les deux premiers mots du condensé, relus en little-endian.
        val raw = digestBytes(digestState)
        val le = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val keyA = le.getInt(0) or 1
        val keyB = le.getInt(4) or 1
        val factors = intArrayOf(keyA * CS64_FACTOR, keyB * CS64_FACTOR)

        // Mots little-endian de (0x00 + data), complétés à zéro, nombre pair.
        val shifted = ByteArray(1 + data.size)
        System.arraycopy(data, 0, shifted, 1, data.size)
        val padded = shifted.copyOf(shifted.size + Math.floorMod(-shifted.size, 4))
        var wordCount = padded.size / 4
        if (wordCount % 2 == 1) wordCount += 1
        val wordBuf = ByteBuffer.wrap(padded.copyOf(wordCount * 4)).order(ByteOrder.LITTLE_ENDIAN)

        val pairs = CS64_COEFFICIENTS.size / 2
        val terms = IntArray(pairs + wordCount)
        val offsets = IntArray(pairs + wordCount)
        for (i in 0 until pairs) {
            terms[i] = CS64_COEFFICIENTS[2 * i] * factors[i % 2]
            offsets[i] = CS64_COEFFICIENTS[2 * i + 1]
        }
        for (index in 0 until wordCount) {
            val word = wordBuf.getInt(index * 4)
            var term = word * (if (index % 2 == 0) keyA else keyB)
            var offset = rol16(word) * (if (index % 2 == 0) 0xEF0569FB.toInt() else 0xC31713DB.toInt())
            if (index == 0) {
                term += CS64_ADJUSTMENT0 * factors[0]
                offset += CS64_ADJUSTMENT1
            }
            terms[pairs + index] = term
            offsets[pairs + index] = offset
        }

        var swapAcc = 0
        var swapLast = 0
        var state = 0
        var acc = 0
        for (index in terms.indices step 2) {
            val x = terms[index]
            val y = terms[index + 1]
            var t = rol16(offsets[index] + x)
            var q = t * 0x76F50F34
            swapAcc += rol16(q) * 0xC3EFEA97.toInt() + q
            t = rol16(offsets[index + 1] + y)
            q = t * 0x1881CE3C
            swapLast = rol16(q) * 0x35BD1EC9 + q
            swapAcc += swapLast

            state = state * keyA + x
            state = rol16(state) * 0xCF98B111.toInt()
            state = rol16(state) * 0x87085B9F.toInt()
            state = rol16(state) * 0x12CEB96D
            state = rol16(state) * 0x257E1D83
            acc += state
            t = state * keyB + y
            t = rol16(t) * 0xA27416F5.toInt()
            t = rol16(t) * 0xD38396FF.toInt()
            state = rol16(rol16(t) * 0x7C932B89) * 0xBFA49F69.toInt()
            acc += state
        }
        return Pair(swapLast xor state, swapAcc xor acc)
    }

    private fun hexUpper(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append("0123456789ABCDEF"[(b.toInt() shr 4) and 0xF])
            sb.append("0123456789ABCDEF"[b.toInt() and 0xF])
        }
        return sb.toString()
    }
}
