package tunnel

import android.content.Context
import core.*
import filter.FilterSerializer
import kotlinx.coroutines.runBlocking

class Persistence {
    companion object {
        val filters = FiltersPersistence()
        val request = RequestPersistence()
    }
}

object RulesPersistence {
    val load = { id: FilterId ->
        blockingResult { Register.get<Ruleset>("rules:set", id) }
    }
    val save = { id: FilterId, ruleset: Ruleset ->
        blockingResult {
            Register.set(ruleset, "rules:set", id, skipMemory = true)
            Register.set(ruleset.size, "rules:size", id)
        }
    }
    val size = { id: FilterId ->
        blockingResult { Register.get<Int>("rules:size", id) }
    }
}

class FiltersPersistence {
    val load = {
        val ok = runCatching { loadLegacy34() }
            .recoverCatching { loadLegacy35() }
            .recoverCatching { loadCurrent() }
        ok.getOrNull()
    }

    val save = { filterStore: FilterStore ->
        runCatching { filterStore.update(FilterStore::class.java) }
    }

    private fun loadLegacy34() = {
        if (isCustomPersistencePath())
            throw Exception("custom persistence path detected, skipping legacy import")
        else {
            val ctx = runBlocking { getApplicationContext()!! }
            val prefs = ctx.getSharedPreferences("filters", Context.MODE_PRIVATE)
            val legacy = prefs.getString("filters", "").split("^")
            prefs.edit().putString("filters", "").apply()
            val old = FilterSerializer().deserialise(legacy)
            if (old.isNotEmpty()) {
                v("loaded from legacy 3.4 persistence", old.size)
                FilterStore(old, lastFetch = 0)
            } else throw Exception("no legacy found")
        }
    }()

    private fun loadLegacy35() = {
        val it = get(FiltersCache::class.java)
        if (it.cache.isEmpty()) throw Exception("no 3.5 legacy persistence found")
        else {
            v("loaded from legacy 3.5 persistence")
            FilterStore(
                    cache = it.cache.map {
                        Filter(
                                id = it.id,
                                source = FilterSourceDescriptor(it.source.id, it.source.source),
                                whitelist = it.whitelist,
                                active = it.active,
                                hidden = it.hidden,
                                priority = it.priority,
                                credit = it.credit,
                                customName = it.customName,
                                customComment = it.customComment
                        )
                    }.toSet()
            )
        }
    }()

    private fun loadCurrent() = {
        try { get(FilterStore::class.java) }
        catch (ex: Exception) {
            if (isCustomPersistencePath()) {
                w("failed loading from a custom path, resetting")
                setPersistencePath("")
                get(FilterStore::class.java)
            } else throw Exception("failed loading from default path", ex)
        }
    }()
}

class RequestPersistence(
        val load: (Int) -> Result<List<Request>> = { batch: Int ->
            blockingResult { Register.get<List<Request>>("requests", batch.toString()) }
        },
        val saveBatch: (Int, List<Request>) -> Any = { batch: Int, requests: List<Request> ->
            blockingResult { Register.set(requests, "requests", batch.toString()) }
        },
        val batch_sizes: List<Int> = listOf(10, 100, 1000)
) {

    private val batch0 = mutableListOf<Request>()

    val batches = listOf(
            { batch0 },
            { load(1).getOrElse { emptyList() } },
            { load(2).getOrElse { emptyList() } }
    )

    val save = { request: Request ->
        batch0.add(0, request)
        saveBatch(0, batch0)
        rollIfNeeded()
    }

    fun rollIfNeeded() {
        for (i in 0 until batches.size) {
            val size = batch_sizes[i]
            val batch = batches[i]()

            if (batch.size > size) {
                if (i < batches.size - 1) {
                    val nextBatch = batches[i + 1]()
                    saveBatch(i + 1, batch + nextBatch)
                    saveBatch(i, emptyList())
                    (batch as? MutableList)?.clear()
                } else {
                    saveBatch(i, batch.subList(0, size))
                }
            } else break
        }
    }

    fun clear(){
        for (i in 0 until batches.size) {
            saveBatch(i, emptyList())
        }
    }
}

