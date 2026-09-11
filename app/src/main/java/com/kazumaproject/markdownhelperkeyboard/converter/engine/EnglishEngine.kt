package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.english.EnglishTypoScorer
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDecodeOptions
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDecoder
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDecodeMetrics
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideCandidateCaseExpander
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideIndexedDictionaryProvider
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltLoadResult
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import dev.imaizentarou.latinime.LatinImeEngine
import dev.imaizentarou.latinime.LatinImeKey
import dev.imaizentarou.latinime.LatinImeKeyboardGeometry
import dev.imaizentarou.latinime.LatinImeTap
import com.kazumaproject.qwerty_keyboard.glide.QwertyTapSample

class EnglishEngine(
    private val latinImeEngine: LatinImeEngine? = null,
) : QwertyGlideCandidateProvider {
    private lateinit var readingLOUDS: LOUDSWithTermId
    private lateinit var wordLOUDS: LOUDS
    private lateinit var tokenArray: TokenArray
    private lateinit var succinctBitVectorLBSReading: SuccinctBitVector
    private lateinit var succinctBitVectorReadingIsLeaf: SuccinctBitVector
    private lateinit var succinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var succinctBitVectorLBSWord: SuccinctBitVector
    @Volatile
    private var dictionariesReady: Boolean = false
    @Volatile
    private var dictionaryLoader: (() -> EnglishDictionaryData)? = null
    @Volatile
    private var qwertyGlideDecoder: QwertyGlideDecoder? = null
    @Volatile
    private var qwertyFallbackGlideDecoder: QwertyGlideDecoder? = null
    @Volatile
    private var qwertyGlideDictionaryReady: Boolean = false
    @Volatile
    private var qwertyGlideWarmupJob: Job? = null
    @Volatile
    private var qwertyGlideInputEnabled: Boolean = false
    private val qwertyGlideCandidateCaseExpander = QwertyGlideCandidateCaseExpander()
    private val qwertyGlideWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var latinImeInputSnapshot: LatinImeInputSnapshot? = null

    private data class LatinImeInputSnapshot(
        val input: String,
        val previousWord: String?,
        val taps: List<LatinImeTap>,
        val geometry: LatinImeKeyboardGeometry,
    )

    companion object {
        const val LENGTH_MULTIPLY = 2000
    }

    fun buildEngine(
        englishReadingLOUDS: LOUDSWithTermId,
        englishWordLOUDS: LOUDS,
        englishTokenArray: TokenArray,
        englishSuccinctBitVectorLBSReading: SuccinctBitVector,
        englishSuccinctBitVectorLBSWord: SuccinctBitVector,
        englishSuccinctBitVectorReadingIsLeaf: SuccinctBitVector,
        englishSuccinctBitVectorTokenArray: SuccinctBitVector,
    ) {
        this.readingLOUDS = englishReadingLOUDS
        this.wordLOUDS = englishWordLOUDS
        this.tokenArray = englishTokenArray
        this.succinctBitVectorLBSReading = englishSuccinctBitVectorLBSReading
        this.succinctBitVectorLBSWord = englishSuccinctBitVectorLBSWord
        this.succinctBitVectorReadingIsLeaf = englishSuccinctBitVectorReadingIsLeaf
        this.succinctBitVectorTokenArray = englishSuccinctBitVectorTokenArray
        dictionariesReady = true
        dictionaryLoader = null
        qwertyGlideDictionaryReady = false
        qwertyGlideDecoder = null
        qwertyGlideInputEnabled = false
        qwertyFallbackGlideDecoder = createQwertyGlideDecoder(
            entries = fallbackGlideDictionaryEntries(),
            dictionaryReady = false
        )
    }

    fun configureLazyDictionaryLoading(reader: DictionaryBinaryReader) {
        dictionaryLoader = { loadDictionaryData(reader) }
        dictionariesReady = false
        qwertyGlideDictionaryReady = false
        qwertyGlideDecoder = null
        qwertyGlideInputEnabled = false
        qwertyFallbackGlideDecoder = createQwertyGlideDecoder(
            entries = fallbackGlideDictionaryEntries(),
            dictionaryReady = false,
        )
    }

    override suspend fun getGlideCandidates(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo,
        previousText: String,
        limit: Int
    ): List<Candidate> {
        if (limit <= 0 || inputPointers.points.size < 2 || proximityInfo.keys.isEmpty()) return emptyList()
        if (!qwertyGlideInputEnabled) return emptyList()
        val decoder = qwertyGlideDecoder ?: run {
            warmUpQwertyGlideDecoderAsync()
            getOrCreateFallbackQwertyGlideDecoder()
        }
        val baseCandidates = decoder.decode(
            inputPointers = inputPointers,
            proximityInfo = proximityInfo,
            previousText = previousText,
            limit = (limit * 3).coerceAtLeast(limit)
        )
        return qwertyGlideCandidateCaseExpander.expand(baseCandidates, limit)
    }

    fun warmUpQwertyGlideDecoderAsync() {
        if (!qwertyGlideInputEnabled) {
            logQwertyGlidePrebuilt("QWERTY glide preference disabled, skip runtime warmup")
            return
        }
        if (qwertyGlideDictionaryReady && qwertyGlideDecoder != null) return
        synchronized(this) {
            val existing = qwertyGlideWarmupJob
            if (existing?.isActive == true) return
            qwertyGlideWarmupJob = qwertyGlideWarmupScope.launch {
                val startedAt = System.nanoTime()
                val entries = buildGlideDictionaryEntries()
                val decoder = createQwertyGlideDecoder(
                    entries = entries,
                    dictionaryReady = true
                )
                if (!isActive) return@launch
                synchronized(this@EnglishEngine) {
                    if (!isActive) return@launch
                    qwertyGlideDecoder = decoder
                    qwertyGlideDictionaryReady = true
                }
                if (BuildConfig.DEBUG) {
                    Timber.d(
                        "QWERTY glide dictionary warmup complete: entries=${entries.size} elapsed_ms=${(System.nanoTime() - startedAt) / 1_000_000L}"
                    )
                }
            }
        }
    }

    fun reloadDictionariesFromCurrentSources(reader: DictionaryBinaryReader) {
        reloadDictionariesFromCurrentSources(
            reader = reader,
            qwertyGlideInputEnabled = qwertyGlideInputEnabled,
            qwertyGlidePrebuiltDictionaryLoader = null,
            canUseBundledPrebuiltIndex = false,
        )
    }

    fun reloadDictionariesFromCurrentSources(
        reader: DictionaryBinaryReader,
        qwertyGlideInputEnabled: Boolean,
        qwertyGlidePrebuiltDictionaryLoader: QwertyGlidePrebuiltDictionaryLoader?,
        canUseBundledPrebuiltIndex: Boolean,
    ) {
        synchronized(this) {
            cancelQwertyGlideWarmup()
            dictionaryLoader = { loadDictionaryData(reader) }
            dictionariesReady = false
            this.qwertyGlideInputEnabled = qwertyGlideInputEnabled
            qwertyGlideDictionaryReady = false
            qwertyGlideDecoder = null
            qwertyFallbackGlideDecoder = createQwertyGlideDecoder(
                entries = fallbackGlideDictionaryEntries(),
                dictionaryReady = false,
            )
        }
        configureQwertyGlideDecoder(
            enabled = qwertyGlideInputEnabled,
            canUseBundledPrebuiltIndex = canUseBundledPrebuiltIndex,
            prebuiltDictionaryLoader = qwertyGlidePrebuiltDictionaryLoader,
        )
    }

    fun isQwertyGlideDictionaryReady(): Boolean = qwertyGlideDictionaryReady

    fun isQwertyGlideInputEnabled(): Boolean = qwertyGlideInputEnabled

    fun hasQwertyGlideDecoder(): Boolean = qwertyGlideDecoder != null

    fun isQwertyGlideWarmupActive(): Boolean = qwertyGlideWarmupJob?.isActive == true

    fun configureQwertyGlideDecoder(
        enabled: Boolean,
        canUseBundledPrebuiltIndex: Boolean,
        prebuiltDictionaryLoader: QwertyGlidePrebuiltDictionaryLoader?,
    ) {
        if (!enabled) {
            synchronized(this) {
                qwertyGlideInputEnabled = false
                cancelQwertyGlideWarmup()
                qwertyGlideDecoder = null
                qwertyGlideDictionaryReady = false
            }
            logQwertyGlidePrebuilt("QWERTY glide preference disabled, skip prebuilt load")
            return
        }

        if (
            canUseBundledPrebuiltIndex &&
            qwertyGlideInputEnabled &&
            qwertyGlideDictionaryReady &&
            qwertyGlideDecoder != null
        ) {
            return
        }

        synchronized(this) {
            qwertyGlideInputEnabled = true
            cancelQwertyGlideWarmup()
            qwertyGlideDecoder = null
            qwertyGlideDictionaryReady = false
        }

        if (!canUseBundledPrebuiltIndex || prebuiltDictionaryLoader == null) {
            logQwertyGlidePrebuilt("External English dictionary override active, skip bundled prebuilt glide index")
            logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            return
        }

        when (val result = prebuiltDictionaryLoader.load()) {
            is QwertyGlidePrebuiltLoadResult.Loaded -> {
                val decoder = createQwertyGlideDecoder(
                    provider = result.provider,
                    dictionaryReady = true,
                )
                synchronized(this) {
                    if (!qwertyGlideInputEnabled) return
                    qwertyGlideDecoder = decoder
                    qwertyGlideDictionaryReady = true
                }
                logQwertyGlidePrebuilt("Bundled prebuilt glide index loaded: entries=${result.provider.entryCount}")
            }

            is QwertyGlidePrebuiltLoadResult.NotAvailable -> {
                synchronized(this) {
                    qwertyGlideDecoder = null
                    qwertyGlideDictionaryReady = false
                }
                logQwertyGlidePrebuilt("Bundled prebuilt glide index unavailable: ${result.reason}")
                logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            }

            is QwertyGlidePrebuiltLoadResult.Invalid -> {
                synchronized(this) {
                    qwertyGlideDecoder = null
                    qwertyGlideDictionaryReady = false
                }
                logQwertyGlidePrebuilt("Bundled prebuilt glide index invalid: ${result.reason}")
                logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            }
        }
    }

    /**
     * Loads the bundled glide index away from the IME main thread.
     *
     * Until the index is ready, glide input continues to use the small fallback decoder. Repeated
     * input sessions share the in-flight job instead of restarting the same asset read.
     */
    fun configureQwertyGlideDecoderAsync(
        enabled: Boolean,
        canUseBundledPrebuiltIndex: Boolean,
        prebuiltDictionaryLoader: QwertyGlidePrebuiltDictionaryLoader?,
    ) {
        if (!enabled) {
            configureQwertyGlideDecoder(
                enabled = false,
                canUseBundledPrebuiltIndex = canUseBundledPrebuiltIndex,
                prebuiltDictionaryLoader = prebuiltDictionaryLoader,
            )
            return
        }

        synchronized(this) {
            if (
                canUseBundledPrebuiltIndex &&
                qwertyGlideInputEnabled &&
                (qwertyGlideDictionaryReady && qwertyGlideDecoder != null ||
                    qwertyGlideWarmupJob?.isActive == true)
            ) {
                return
            }
            qwertyGlideInputEnabled = true
            cancelQwertyGlideWarmup()
            qwertyGlideDecoder = null
            qwertyGlideDictionaryReady = false
        }

        if (!canUseBundledPrebuiltIndex || prebuiltDictionaryLoader == null) {
            logQwertyGlidePrebuilt(
                "External English dictionary override active, skip bundled prebuilt glide index"
            )
            logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
            return
        }

        val job = qwertyGlideWarmupScope.launch(start = CoroutineStart.LAZY) {
            val startedAt = System.nanoTime()
            val result = prebuiltDictionaryLoader.load()
            if (!isActive) return@launch
            when (result) {
                is QwertyGlidePrebuiltLoadResult.Loaded -> {
                    val decoder = createQwertyGlideDecoder(
                        provider = result.provider,
                        dictionaryReady = true,
                    )
                    if (!isActive) return@launch
                    synchronized(this@EnglishEngine) {
                        if (!isActive || !qwertyGlideInputEnabled) return@synchronized
                        qwertyGlideDecoder = decoder
                        qwertyGlideDictionaryReady = true
                    }
                    logQwertyGlidePrebuilt(
                        "Bundled prebuilt glide index loaded asynchronously: " +
                            "entries=${result.provider.entryCount} " +
                            "elapsed_ms=${(System.nanoTime() - startedAt) / 1_000_000L}"
                    )
                }

                is QwertyGlidePrebuiltLoadResult.NotAvailable -> {
                    logQwertyGlidePrebuilt(
                        "Bundled prebuilt glide index unavailable: ${result.reason}"
                    )
                    logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
                }

                is QwertyGlidePrebuiltLoadResult.Invalid -> {
                    logQwertyGlidePrebuilt(
                        "Bundled prebuilt glide index invalid: ${result.reason}"
                    )
                    logQwertyGlidePrebuilt("Fallback to runtime glide dictionary build")
                }
            }
        }
        synchronized(this) {
            qwertyGlideWarmupJob = job
        }
        job.start()
    }

    internal suspend fun awaitQwertyGlideWarmup() {
        qwertyGlideWarmupJob?.join()
    }

    fun cancelQwertyGlideWarmup() {
        qwertyGlideWarmupJob?.cancel()
        qwertyGlideWarmupJob = null
    }

    fun releaseQwertyGlideResources() {
        cancelQwertyGlideWarmup()
        qwertyGlideDecoder = null
        qwertyFallbackGlideDecoder = null
        qwertyGlideDictionaryReady = false
        qwertyGlideInputEnabled = false
    }

    fun invalidateQwertyGlideCache() {
        qwertyGlideDecoder?.clearCache()
        qwertyFallbackGlideDecoder?.clearCache()
    }

    private fun getOrCreateFallbackQwertyGlideDecoder(): QwertyGlideDecoder {
        qwertyFallbackGlideDecoder?.let { return it }
        return synchronized(this) {
            qwertyFallbackGlideDecoder ?: createQwertyGlideDecoder(
                entries = fallbackGlideDictionaryEntries(),
                dictionaryReady = false
            ).also { qwertyFallbackGlideDecoder = it }
        }
    }

    private fun createQwertyGlideDecoder(
        entries: Iterable<QwertyGlideDictionaryEntry>,
        dictionaryReady: Boolean
    ): QwertyGlideDecoder {
        return createQwertyGlideDecoder(
            provider = QwertyGlideIndexedDictionaryProvider(entries),
            dictionaryReady = dictionaryReady,
        )
    }

    private fun createQwertyGlideDecoder(
        provider: QwertyGlideIndexedDictionaryProvider,
        dictionaryReady: Boolean
    ): QwertyGlideDecoder {
        return QwertyGlideDecoder(
            dictionaryProvider = provider,
            options = QwertyGlideDecodeOptions(),
            dictionaryReady = dictionaryReady,
            metricsListener = ::logQwertyGlideMetrics
        )
    }

    private fun logQwertyGlidePrebuilt(message: String) {
        if (BuildConfig.DEBUG) {
            Timber.d(message)
        }
    }

    private fun logQwertyGlideMetrics(metrics: QwertyGlideDecodeMetrics) {
        if (!BuildConfig.DEBUG) return
        Timber.d(
            "QWERTY glide decode: dictionary_ready=${metrics.dictionaryReady} " +
                    "raw_bucket_candidate_count=${metrics.rawBucketCandidateCount} " +
                    "prefilter_candidate_count=${metrics.prefilterCandidateCount} " +
                    "full_score_candidate_count=${metrics.fullScoreCandidateCount} " +
                    "rerank_candidate_count=${metrics.rerankCandidateCount} " +
                    "decode_total_ms=${metrics.decodeTotalMs} prefilter_ms=${metrics.prefilterMs} " +
                    "full_score_ms=${metrics.fullScoreMs} rerank_ms=${metrics.rerankMs} " +
                    "cache_hit=${metrics.cacheHit}"
        )
    }

    private fun fallbackGlideDictionaryEntries(): List<QwertyGlideDictionaryEntry> {
        return listOf(
            "hello", "good", "test", "word", "world", "keyboard", "android", "sumire",
            "coffee", "letter", "people", "glide", "time", "home", "something"
        ).map { word -> QwertyGlideDictionaryEntry(word, 6000) }
    }

    private fun buildGlideDictionaryEntries(): List<QwertyGlideDictionaryEntry> {
        ensureDictionariesLoaded()
        val entries = linkedMapOf<String, QwertyGlideDictionaryEntry>()
        val readings = readingLOUDS.predictiveSearch(
            prefix = "",
            succinctBitVector = succinctBitVectorLBSReading
        )
        for (reading in readings) {
            if (reading.length !in 2..24 || !reading.all { it in 'a'..'z' }) continue
            val nodeIndex = readingLOUDS.getNodeIndex(
                reading,
                succinctBitVector = succinctBitVectorLBSReading
            )
            if (nodeIndex <= 0) continue
            val termId = readingLOUDS.getTermId(
                nodeIndex,
                succinctBitVector = succinctBitVectorReadingIsLeaf
            )
            if (termId < 0) continue
            val tokens = tokenArray.getListDictionaryByYomiTermId(
                termId,
                succinctBitVector = succinctBitVectorTokenArray
            )
            if (tokens.isEmpty()) {
                entries.mergeGlideEntry(reading, 9000)
            } else {
                for (entry in tokens) {
                    val word = when (entry.nodeId) {
                        -1 -> reading
                        else -> wordLOUDS.getLetter(
                            entry.nodeId,
                            succinctBitVector = succinctBitVectorLBSWord
                        )
                    }.lowercase()
                    if (word.length in 2..24 && word.all { it in 'a'..'z' }) {
                        entries.mergeGlideEntry(word, entry.wordCost.toInt())
                    }
                }
            }
        }
        fallbackGlideDictionaryEntries().forEach { entry ->
            entries.mergeGlideEntry(entry.word, entry.wordCost)
        }
        return entries.values.toList()
    }

    fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean = false,
    ): List<Candidate> = getCandidates(
        input = input,
        enableTypoCorrection = enableTypoCorrection,
        enablePrediction = true,
    )

    fun isKnownWord(input: String): Boolean {
        if (input.isEmpty() || !input.all(Char::isLetter)) return false
        runCatching { latinImeEngine?.isKnownWord(input) }
            .getOrNull()
            ?.let { if (it) return true }
        ensureDictionariesLoaded()
        val nodeIndex = readingLOUDS.getNodeIndex(
            input.lowercase(),
            succinctBitVector = succinctBitVectorLBSReading,
        )
        return nodeIndex > 0 && readingLOUDS.getTermId(
            nodeIndex,
            succinctBitVector = succinctBitVectorReadingIsLeaf,
        ) >= 0
    }

    fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean,
        enablePrediction: Boolean,
    ): List<Candidate> {
        if (input.isEmpty()) return emptyList()
        ensureDictionariesLoaded()

        val defaultType = 29.toByte()
        val lowerInput = input.lowercase()
        val limit = if (input.length <= 2) 6 else 12

        val latinImeCandidates = if (enableTypoCorrection) {
            getLatinImeCandidates(input = input, defaultType = defaultType)
        } else {
            emptyList()
        }

        val predictiveSearchReading = if (enablePrediction) {
            readingLOUDS.predictiveSearch(
                prefix = lowerInput,
                succinctBitVector = succinctBitVectorLBSReading,
                limit = limit
            )
        } else {
            emptyList()
        }

        // Search the dictionary trie with one bounded edit. This catches adjacent swaps,
        // missing/extra letters and substitutions without scanning every dictionary word.
        val typoCorrection = if (enableTypoCorrection && input.length > 2) {
            readingLOUDS.fuzzySearch(
                input = lowerInput,
                succinctBitVector = succinctBitVectorLBSReading
            )
        } else {
            emptyList()
        }

        Timber.d(
            "getCandidates English: [$input] predictive=$predictiveSearchReading typoEnabled=$enableTypoCorrection typo=[${typoCorrection.map { it.yomi }}]"
        )

        val predictions = mutableListOf<Candidate>()

        // input 自体の3種は常に入れる
        predictions += Candidate(
            string = input,
            score = 500,
            type = defaultType,
            length = input.length.toUByte()
        )
        predictions += Candidate(
            string = input.replaceFirstChar { it.uppercaseChar() },
            score = if (input.length <= 3) 9000 else if (input.length <= 4) 12000 else 57000,
            type = defaultType,
            length = input.length.toUByte()
        )
        predictions += Candidate(
            string = input.uppercase(),
            score = if (input.length <= 3) 9001 else if (input.length <= 4) 22001 else 57001,
            type = defaultType,
            length = input.length.toUByte()
        )

        // ★ fallback は predictive も (typo有効時のtypoも) 空のときだけ
        if (predictiveSearchReading.isEmpty() && typoCorrection.isEmpty()) {
            return listOf(
                Candidate(
                    string = input,
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10000
                ),
                Candidate(
                    string = input.replaceFirstChar { it.uppercaseChar() },
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = if (input.first().isUpperCase()) 8500 else 10001
                ),
                Candidate(
                    string = input.uppercase(),
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10002
                )
            ).sortedBy { it.score }
        }
        // ============
        // 1) predictive（通常）
        // ============
        for (readingStr in predictiveSearchReading) {
            val nodeIndex = readingLOUDS.getNodeIndex(
                readingStr,
                succinctBitVector = succinctBitVectorLBSReading
            )
            if (nodeIndex <= 0) continue

            val termId = readingLOUDS.getTermId(
                nodeIndex,
                succinctBitVector = succinctBitVectorReadingIsLeaf
            )
            if (termId < 0) continue

            val listToken =
                tokenArray.getListDictionaryByYomiTermId(termId, succinctBitVectorTokenArray)

            val variants = listToken.flatMap { entry ->
                val base = when (entry.nodeId) {
                    -1 -> readingStr
                    else -> wordLOUDS.getLetter(
                        entry.nodeId,
                        succinctBitVector = succinctBitVectorLBSWord
                    )
                }

                listOf(
                    Candidate(
                        string = base,
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = entry.wordCost.toInt()
                    ),
                    Candidate(
                        string = base.replaceFirstChar { it.uppercaseChar() },
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = if (input.first().isUpperCase())
                            (entry.wordCost.toInt() + base.length * LENGTH_MULTIPLY - 8000).coerceAtLeast(
                                0
                            )
                        else
                            entry.wordCost.toInt() + 500 + base.length * LENGTH_MULTIPLY
                    ),
                    Candidate(
                        string = base.uppercase(),
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = if (input.first().isUpperCase())
                            entry.wordCost.toInt() + base.length * LENGTH_MULTIPLY
                        else
                            entry.wordCost.toInt() + 2000 + base.length * LENGTH_MULTIPLY
                    )
                )
            }

            predictions += variants
        }

        // ============
        // 2) typo（補正） - enableTypoCorrection のときだけ
        // ============
        if (enableTypoCorrection && typoCorrection.isNotEmpty()) {
            val predictiveSet = predictiveSearchReading.toHashSet()

            for (typo in typoCorrection) {
                val readingStr = typo.yomi
                if (!predictiveSet.add(readingStr)) continue

                val nodeIndex = readingLOUDS.getNodeIndex(
                    readingStr,
                    succinctBitVector = succinctBitVectorLBSReading
                )
                if (nodeIndex <= 0) continue

                val termId = readingLOUDS.getTermId(
                    nodeIndex,
                    succinctBitVector = succinctBitVectorReadingIsLeaf
                )
                if (termId < 0) continue

                val listToken =
                    tokenArray.getListDictionaryByYomiTermId(termId, succinctBitVectorTokenArray)

                val variants = listToken.flatMap { entry ->
                    val base = when (entry.nodeId) {
                        -1 -> readingStr
                        else -> wordLOUDS.getLetter(
                            entry.nodeId,
                            succinctBitVector = succinctBitVectorLBSWord
                        )
                    }

                    val baseLower = base.lowercase()
                    if (EnglishTypoScorer.editDistance(baseLower, lowerInput) != 1) {
                        return@flatMap emptyList<Candidate>()
                    }
                    val penalty = 2_000 + EnglishTypoScorer.rankingPenalty(lowerInput, baseLower)
                    val inputIsAllCaps = input.length > 1 && input.all(Char::isUpperCase)
                    val inputIsCapitalized = input.first().isUpperCase()
                    val lowerCasePenalty = when {
                        inputIsAllCaps -> 3_000
                        inputIsCapitalized -> 1_500
                        else -> 0
                    }
                    val capitalizedPenalty = when {
                        inputIsAllCaps -> 2_000
                        inputIsCapitalized -> 0
                        else -> 500 + base.length * LENGTH_MULTIPLY
                    }
                    val upperCasePenalty = if (inputIsAllCaps) {
                        0
                    } else {
                        2_000 + base.length * LENGTH_MULTIPLY
                    }

                    listOf(
                        Candidate(
                            base,
                            QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE,
                            input.length.toUByte(),
                            entry.wordCost.toInt() + penalty + lowerCasePenalty
                        ),
                        Candidate(
                            base.replaceFirstChar { it.uppercaseChar() },
                            QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE,
                            input.length.toUByte(),
                            entry.wordCost.toInt() + penalty + capitalizedPenalty
                        ),
                        Candidate(
                            base.uppercase(),
                            QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE,
                            input.length.toUByte(),
                            entry.wordCost.toInt() + penalty + upperCasePenalty
                        )
                    )
                }

                predictions += variants
            }
        }

        // 同一文字列は最小スコアのみ残す
        val deduped = (latinImeCandidates + predictions)
            .groupBy { it.string }
            .map { (_, list) ->
                val bestScore = list.minOf(Candidate::score)
                val latinImeMetadata = list.firstOrNull {
                    it.type == QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE ||
                        it.type == QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE ||
                        it.type == QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE ||
                        it.type == QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE
                }
                latinImeMetadata?.copy(score = bestScore) ?: list.minBy(Candidate::score)
            }

        return deduped.sortedBy { it.score }
    }

    /** Records the real touch geometry for the next LatinIME lookup. */
    fun recordLatinImeTap(
        input: String,
        previousText: String,
        tap: QwertyTapSample?,
        proximityInfo: QwertyKeyboardProximityInfo,
    ) {
        if (input.isEmpty() || proximityInfo.keys.isEmpty()) {
            latinImeInputSnapshot = null
            return
        }
        val geometry = LatinImeKeyboardGeometry(
            width = proximityInfo.keyboardWidth,
            height = proximityInfo.keyboardHeight,
            keys = proximityInfo.keys.map { key ->
                LatinImeKey(
                    codePoint = key.char.code,
                    x = (key.centerX - key.width / 2f).toInt(),
                    y = (key.centerY - key.height / 2f).toInt(),
                    width = key.width.toInt().coerceAtLeast(1),
                    height = key.height.toInt().coerceAtLeast(1),
                )
            },
        )
        val old = latinImeInputSnapshot
        val taps = if (
            tap != null && old != null &&
            input.length == old.input.length + 1 &&
            input.dropLast(1).equals(old.input, ignoreCase = true) &&
            old.geometry == geometry
        ) {
            old.taps + tap.toLatinImeTap(old.taps.size)
        } else if (tap != null && input.length == 1) {
            listOf(tap.toLatinImeTap(0))
        } else {
            emptyList()
        }
        latinImeInputSnapshot = LatinImeInputSnapshot(
            input = input,
            previousWord = previousText.extractLastEnglishWord(),
            taps = taps,
            geometry = geometry,
        )
    }

    fun clearLatinImeInputSnapshot() {
        latinImeInputSnapshot = null
    }

    private fun getLatinImeCandidates(input: String, defaultType: Byte): List<Candidate> {
        val engine = latinImeEngine ?: return emptyList()
        val snapshot = latinImeInputSnapshot?.takeIf { it.input.equals(input, ignoreCase = true) }
        val nativeSuggestions = runCatching {
            engine.suggest(
                typed = input,
                previousWord = snapshot?.previousWord,
                taps = snapshot?.taps.orEmpty(),
                geometry = snapshot?.geometry,
            )
        }.onFailure { error ->
            Timber.w(error, "AOSP LatinIME suggestion failed; using LOUDS fallback")
        }.getOrDefault(emptyList())

        return nativeSuggestions
            .sortedByDescending { it.score }
            .mapIndexedNotNull { rank, suggestion ->
                val rawWord = suggestion.word
                if (rawWord.isEmpty()) return@mapIndexedNotNull null
                val word = rawWord.matchCaseOf(input)
                val isExact = word.equals(input, ignoreCase = true)
                val isCompletion = word.lowercase().startsWith(input.lowercase())
                Candidate(
                    string = word,
                    type = when {
                        isExact && suggestion.isExactMatchWithIntentionalOmission ->
                            QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE
                        isExact -> QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE
                        isCompletion -> defaultType
                        suggestion.isExactMatchWithIntentionalOmission ->
                            QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE
                        suggestion.isAppropriateForAutoCorrection ->
                            QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE
                        else -> QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE
                    },
                    length = input.length.toUByte(),
                    score = when {
                        isExact -> 350
                        else -> 700 + rank * 220
                    },
                )
            }
    }

    private fun QwertyTapSample.toLatinImeTap(index: Int): LatinImeTap {
        return LatinImeTap(
            x = x,
            y = y,
            // Typing only needs monotonic relative timing; wall/uptime values must not overflow.
            timeMillis = index * 60,
        )
    }

    private fun String.extractLastEnglishWord(): String? =
        Regex("[A-Za-z']+").findAll(this).lastOrNull()?.value

    private fun String.matchCaseOf(input: String): String = when {
        input.length > 1 && input.all(Char::isUpperCase) -> uppercase()
        input.firstOrNull()?.isUpperCase() == true -> replaceFirstChar(Char::uppercaseChar)
        else -> lowercase().let { word ->
            // First-person contractions keep a capital I even when typed without punctuation.
            if (word.startsWith("i'")) "I${word.drop(1)}" else word
        }
    }

    private fun ensureDictionariesLoaded() {
        if (dictionariesReady) return
        synchronized(this) {
            if (dictionariesReady) return
            val loader = checkNotNull(dictionaryLoader) {
                "English dictionary loader is not configured"
            }
            installDictionaryData(loader())
            dictionaryLoader = null
            dictionariesReady = true
        }
    }

    private fun loadDictionaryData(reader: DictionaryBinaryReader): EnglishDictionaryData {
        val newReadingLOUDS = reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
        val newWordLOUDS = reader.loadEnglishWord(DictionaryFileKey.ENGLISH_WORD)
        val newTokenArray = reader.loadEnglishToken(DictionaryFileKey.ENGLISH_TOKEN)
        return EnglishDictionaryData(
            readingLOUDS = newReadingLOUDS,
            wordLOUDS = newWordLOUDS,
            tokenArray = newTokenArray,
            succinctBitVectorLBSReading = reader.loadEnglishReadingLbsIndex(newReadingLOUDS),
            succinctBitVectorLBSWord = reader.loadEnglishWordLbsIndex(newWordLOUDS),
            succinctBitVectorReadingIsLeaf = reader.loadEnglishReadingLeafIndex(newReadingLOUDS),
            succinctBitVectorTokenArray = reader.loadEnglishTokenIndex(newTokenArray),
        )
    }

    private fun installDictionaryData(data: EnglishDictionaryData) {
        readingLOUDS = data.readingLOUDS
        wordLOUDS = data.wordLOUDS
        tokenArray = data.tokenArray
        succinctBitVectorLBSReading = data.succinctBitVectorLBSReading
        succinctBitVectorLBSWord = data.succinctBitVectorLBSWord
        succinctBitVectorReadingIsLeaf = data.succinctBitVectorReadingIsLeaf
        succinctBitVectorTokenArray = data.succinctBitVectorTokenArray
    }

    private data class EnglishDictionaryData(
        val readingLOUDS: LOUDSWithTermId,
        val wordLOUDS: LOUDS,
        val tokenArray: TokenArray,
        val succinctBitVectorLBSReading: SuccinctBitVector,
        val succinctBitVectorLBSWord: SuccinctBitVector,
        val succinctBitVectorReadingIsLeaf: SuccinctBitVector,
        val succinctBitVectorTokenArray: SuccinctBitVector,
    )

}

private fun MutableMap<String, QwertyGlideDictionaryEntry>.mergeGlideEntry(
    word: String,
    wordCost: Int
) {
    val normalizedWord = word.lowercase()
    val current = this[normalizedWord]
    if (current == null || wordCost < current.wordCost) {
        this[normalizedWord] = QwertyGlideDictionaryEntry(normalizedWord, wordCost)
    }
}
