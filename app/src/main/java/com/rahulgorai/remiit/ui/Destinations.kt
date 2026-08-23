package com.rahulgorai.remiit.ui

/** Navigation routes. Kept as plain strings — the graph is small and flat. */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"

    /** Rule builder. A blank id means "new rule". */
    const val BUILDER = "builder"
    const val BUILDER_ARG_RULE_ID = "ruleId"
    const val BUILDER_ROUTE = "$BUILDER?$BUILDER_ARG_RULE_ID={$BUILDER_ARG_RULE_ID}"

    fun builder(ruleId: String? = null): String =
        if (ruleId.isNullOrBlank()) "$BUILDER?" else "$BUILDER?$BUILDER_ARG_RULE_ID=$ruleId"
}
