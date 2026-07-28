package com.inktone.infrastructure.tts

// Portage Kotlin de test_alignment.py::text_to_token_ids et
// run_viterbi_prototype.py::viterbi_forced_alignment /
// words_from_viterbi_segments — deja valides et executes reellement cote
// Python (Tache 5.2), puis prouves sur device physique dans le scratchpad
// de prototypage (docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md §6-8).
// Traduction terme a terme, aucune logique nouvelle inventee ici.

internal data class TokenTable(
    val sym2id: Map<String, Int>,
    val id2sym: Map<Int, String>,
    val blankId: Int,
)

internal fun loadTokens(lines: List<String>): TokenTable {
    val sym2id = mutableMapOf<String, Int>()
    val id2sym = mutableMapOf<Int, String>()
    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val parts = line.split(Regex("\\s+"))
        if (parts.size >= 2) {
            val tid = parts[1].toIntOrNull() ?: continue
            sym2id[parts[0]] = tid
            id2sym[tid] = parts[0]
        }
    }
    var blankId = 0
    for ((sym, tid) in sym2id) {
        if (sym == "<blk>" || sym == "<eps>" || sym == "<blank>") {
            blankId = tid
            break
        }
    }
    return TokenTable(sym2id, id2sym, blankId)
}

internal fun textToTokenIds(text: String, table: TokenTable): List<Int> {
    var t = text.lowercase().trim()
    for (ch in ".,!?;:…\"«»()") {
        t = t.replace(ch.toString(), " ")
    }
    t = t.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    t = "▁" + t.replace(" ", "▁")

    val tokenIds = mutableListOf<Int>()
    var i = 0
    val maxSymLen = table.sym2id.keys.maxOf { it.length }
    while (i < t.length) {
        var matched = false
        var length = minOf(maxSymLen, t.length - i)
        while (length > 0) {
            val candidate = t.substring(i, i + length)
            val tid = table.sym2id[candidate]
            if (tid != null) {
                tokenIds.add(tid)
                i += length
                matched = true
                break
            }
            length--
        }
        if (!matched) {
            val ch = t[i].toString()
            table.sym2id[ch]?.let { tokenIds.add(it) }
            i++
        }
    }
    return tokenIds
}

internal data class TokenSegment(val tokenId: Int, val startS: Double, val endS: Double)

internal fun viterbiForcedAlignment(
    logProbs: Array<FloatArray>, // (T, vocabSize)
    refTokenIds: List<Int>,
    blankId: Int,
    frameShiftS: Double,
): List<TokenSegment> {
    val timeSteps = logProbs.size
    val refLen = refTokenIds.size
    val numStates = 2 * refLen + 1
    if (timeSteps == 0 || refLen == 0) return emptyList()

    val stateToToken = IntArray(numStates)
    for (k in 0 until refLen) {
        stateToToken[2 * k] = -1
        stateToToken[2 * k + 1] = refTokenIds[k]
    }
    stateToToken[numStates - 1] = -1

    val logZero = -1e30
    val dp = Array(timeSteps) { DoubleArray(numStates) { logZero } }
    val backPtr = Array(timeSteps) { IntArray(numStates) }

    dp[0][0] = logProbs[0][blankId].toDouble()
    dp[0][1] = logProbs[0][refTokenIds[0]].toDouble()

    for (tt in 1 until timeSteps) {
        for (s in 0 until numStates) {
            val tokenId = stateToToken[s]
            val emitLogp = if (tokenId == -1) logProbs[tt][blankId] else logProbs[tt][tokenId]

            var bestLogp = logZero
            var bestPrevS = 0

            val cand0 = dp[tt - 1][s] + emitLogp
            if (cand0 > bestLogp) {
                bestLogp = cand0
                bestPrevS = s
            }

            if (s >= 1) {
                val cand1 = dp[tt - 1][s - 1] + emitLogp
                if (cand1 > bestLogp) {
                    bestLogp = cand1
                    bestPrevS = s - 1
                }
            }

            if (s >= 2 && stateToToken[s] != -1 && stateToToken[s] != stateToToken[s - 2]) {
                val cand2 = dp[tt - 1][s - 2] + emitLogp
                if (cand2 > bestLogp) {
                    bestLogp = cand2
                    bestPrevS = s - 2
                }
            }

            dp[tt][s] = bestLogp
            backPtr[tt][s] = bestPrevS
        }
    }

    val finalCandidates = mutableListOf(numStates - 1)
    if (numStates >= 2) finalCandidates.add(numStates - 2)
    val bestFinalS = finalCandidates.maxByOrNull { dp[timeSteps - 1][it] }!!

    val path = IntArray(timeSteps)
    var s = bestFinalS
    for (tt in timeSteps - 1 downTo 0) {
        path[tt] = s
        s = backPtr[tt][s]
    }

    val segments = mutableListOf<TokenSegment>()
    var currentTokenS = -1
    var currentStartFrame = 0

    for (tt in 0 until timeSteps) {
        val st = path[tt]
        val token = stateToToken[st]
        if (token != -1 && currentTokenS == -1) {
            currentTokenS = st
            currentStartFrame = tt
        } else if (token == -1 && currentTokenS != -1 && stateToToken[st] != stateToToken[currentTokenS]) {
            val tid = stateToToken[currentTokenS]
            segments.add(TokenSegment(tid, currentStartFrame * frameShiftS, tt * frameShiftS))
            currentTokenS = -1
        } else if (token != -1 && st != currentTokenS && currentTokenS != -1) {
            val tid = stateToToken[currentTokenS]
            segments.add(TokenSegment(tid, currentStartFrame * frameShiftS, tt * frameShiftS))
            currentTokenS = st
            currentStartFrame = tt
        }
    }
    if (currentTokenS != -1) {
        val tid = stateToToken[currentTokenS]
        segments.add(TokenSegment(tid, currentStartFrame * frameShiftS, timeSteps * frameShiftS))
    }

    return segments
}

internal data class AlignedWord(val startS: Double, val endS: Double, val word: String)

internal fun wordsFromViterbiSegments(segments: List<TokenSegment>, table: TokenTable): List<AlignedWord> {
    val words = mutableListOf<AlignedWord>()
    val currentParts = mutableListOf<String>()
    var wordStart: Double? = null
    var wordEnd = 0.0

    for (seg in segments) {
        val sym = table.id2sym[seg.tokenId] ?: "<${seg.tokenId}>"
        val isBoundary = sym.startsWith("▁")

        if (isBoundary && currentParts.isNotEmpty()) {
            val wordText = currentParts.joinToString("").replace("▁", "")
            if (wordText.isNotEmpty()) {
                words.add(AlignedWord(wordStart!!, wordEnd, wordText))
            }
            currentParts.clear()
            wordStart = null
        }

        if (wordStart == null) wordStart = seg.startS
        wordEnd = seg.endS
        currentParts.add(sym)
    }

    if (currentParts.isNotEmpty()) {
        val wordText = currentParts.joinToString("").replace("▁", "")
        if (wordText.isNotEmpty()) {
            words.add(AlignedWord(wordStart!!, wordEnd, wordText))
        }
    }

    return words
}
