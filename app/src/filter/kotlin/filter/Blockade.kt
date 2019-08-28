package filter

import core.e
import core.v
import tunnel.*

internal class Blockade(
        private val doLoadRuleset: (FilterId) -> Result<Ruleset> = RulesPersistence.load,
        private val doSaveRuleset: (FilterId, Ruleset) -> Result<Any?> = RulesPersistence.save,
        private val doGetRulesetSize: (FilterId) -> Result<Int> = RulesPersistence.size,
        private val doGetMemoryLimit: () -> MemoryLimit = Memory.linesAvailable,
        private var denyRuleset: Ruleset = Ruleset(),
        private var allowRuleset: Ruleset = Ruleset()
) {

    fun build(deny: List<FilterId>, allow: List<FilterId>) {
        core.emit(Events.RULESET_BUILDING)
        denyRuleset.clear()
        denyRuleset = buildRuleset(deny)
        allowRuleset.clear()
        allowRuleset = buildRuleset(allow)
        core.emit(Events.RULESET_BUILT, denyRuleset.size to allowRuleset.size)
    }

    private fun buildRuleset(filters: List<FilterId>): Ruleset {
        var ruleset = Ruleset()
        if (filters.isEmpty()) return ruleset
        doLoadRuleset(filters.first()).fold(
                onSuccess = { firstRuleset ->
                    ruleset = firstRuleset
                    filters.drop(1).forEach { nextFilter ->
                        if (ruleset.size < doGetMemoryLimit()) {
                            doLoadRuleset(nextFilter).fold(
                                    onSuccess = { ruleset.addAll(it) },
                                    onFailure = { e("could not load ruleset", nextFilter, it) }
                            )
                        } else {
                            e("memory limit reached, skipping ruleset", nextFilter,
                                    doGetMemoryLimit(), ruleset.size)
                        }
                    }
                },
                onFailure = {
                    e("could not load first ruleset", filters.first(), it)
                }
        )
        return ruleset
    }

    fun set(id: FilterId, ruleset: Ruleset) {
        doSaveRuleset(id, ruleset).fold(
                onSuccess = { v("saved ruleset", id, ruleset.size) },
                onFailure = { e("failed to save ruleset", id, it) }
        )
    }

    fun denied(host: String): Boolean {
        return denyRuleset.contains(host)
    }

    fun allowed(host: String): Boolean {
        return allowRuleset.contains(host)
    }

}

