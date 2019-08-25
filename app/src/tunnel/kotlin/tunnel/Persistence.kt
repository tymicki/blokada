package tunnel

import android.content.Context
import com.github.michaelbull.result.*
import core.*
import core.Result
import kotlinx.coroutines.runBlocking

class Persistence {
    companion object {
        val rules = RulesPersistence()
        val filters = FiltersPersistence()
        val request = RequestPersistence()
        val blocka = BlockaConfigPersistence()
    }
}

class RulesPersistence {
    val load = { id: FilterId ->
        Result.of { loadPersistence("rules:set:$id", { Ruleset() }) }
    }
    val save = { id: FilterId, ruleset: Ruleset ->
        Result.of { savePersistence("rules:set:$id", ruleset) }
        Result.of { savePersistence("rules:size:$id", ruleset.size) }
    }
    val size = { id: FilterId ->
        Result.of { loadPersistence("rules:size:$id", { 0 }) }
    }
}

class FiltersPersistence {
    val load = { ktx: AndroidKontext ->
        loadLegacy34(ktx)
                .or { loadLegacy35(ktx) }
                .or {
                    Result.of { loadPersistence("filters2", { FilterStore() }) }
                            .orElse { ex ->
                                if (isCustomPersistencePath()) {
                                    w("failed loading from a custom path, resetting")
                                    setPersistencePath("")
                                    Result.of { loadPersistence("filters2", { FilterStore() }) }
                                } else Err(Exception("failed loading from default path", ex))
                            }
                }
    }

    val save = { filterStore: FilterStore ->
        Result.of { savePersistence("filters2", filterStore) }
    }

    private fun loadLegacy34(ktx: AndroidKontext) = {
        if (isCustomPersistencePath())
            Err(Exception("custom persistence path detected, skipping legacy import"))
        else {
            val prefs = ktx.ctx.getSharedPreferences("filters", Context.MODE_PRIVATE)
            val legacy = prefs.getString("filters", "").split("^")
            prefs.edit().putString("filters", "").apply()
            val old = FilterSerializer().deserialise(legacy)
            if (old.isNotEmpty()) {
                v("loaded from legacy 3.4 persistence", old.size)
                Result.of { FilterStore(old, lastFetch = 0) }
            } else Err(Exception("no legacy found"))
        }
    }()

    private fun loadLegacy35(ktx: AndroidKontext) = {
        Result.of { loadPersistence("filters2", { FiltersCache() }) }
                .andThen {
                    if (it.cache.isEmpty()) Err(Exception("no 3.5 legacy persistence found"))
                    else {
                        v("loaded from legacy 3.5 persistence")
                        Ok(FilterStore(
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
                        ))
                    }
                }
    }()
}

class RequestPersistence(
        val load: (Int) -> Result<List<Request>> = { batch: Int ->
            Result.of { loadPersistence("requests:$batch", { emptyList<Request>() }) }
        },
        val saveBatch: (Int, List<Request>) -> Any = { batch: Int, requests: List<Request> ->
            Result.of { savePersistence("requests:$batch", requests) }
        },
        val batch_sizes: List<Int> = listOf(10, 100, 1000)
) {

    private val batch0 = mutableListOf<Request>()

    val batches = listOf(
            { batch0 },
            { load(1).getOr { emptyList() } },
            { load(2).getOr { emptyList() } }
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

class BlockaConfigPersistence {
    val load = { ktx: Kontext ->
        Result.of {
            runBlocking { BlockaConfig().loadFromPersistence() }
        }
                .mapBoth(
                        success = { it },
                        failure = { ex ->
                            w("failed loading BlockaConfig, reverting to empty", ex)
                            BlockaConfig()
                        }
                )
    }

    val save = { config: BlockaConfig ->
        Result.of { savePersistence("blocka:config", config) }
    }
}
