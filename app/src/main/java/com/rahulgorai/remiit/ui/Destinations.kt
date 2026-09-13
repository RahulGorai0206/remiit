package com.rahulgorai.remiit.ui

import android.net.Uri

/** Navigation routes. Kept as plain strings — the graph is small and flat. */
object Routes {
    const val HOME = "home"
    const val AUTOMATION = "automation"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"

    /** Rule builder. A blank id means "new rule". */
    const val BUILDER = "builder"
    const val BUILDER_ARG_RULE_ID = "ruleId"

    /**
     * The rule's name, passed purely so the editor's heading is correct on its
     * very first frame. Reading the rule from the database takes a frame or two,
     * and without this the heading shows a placeholder until it lands.
     */
    const val BUILDER_ARG_TITLE = "title"

    const val BUILDER_ROUTE =
        "$BUILDER?$BUILDER_ARG_RULE_ID={$BUILDER_ARG_RULE_ID}&$BUILDER_ARG_TITLE={$BUILDER_ARG_TITLE}"

    fun builder(ruleId: String? = null, title: String? = null): String {
        if (ruleId.isNullOrBlank()) return "$BUILDER?"
        // Encoded because a task name is free text and routes are URIs — an
        // unescaped "/" or "?" in a title would corrupt the destination.
        val encoded = Uri.encode(title.orEmpty())
        return "$BUILDER?$BUILDER_ARG_RULE_ID=$ruleId&$BUILDER_ARG_TITLE=$encoded"
    }

    /** Automation editor. A blank id means "new automation". */
    const val AUTOMATION_EDITOR = "automation_editor"
    const val AUTOMATION_ARG_ID = "automationId"
    const val AUTOMATION_ARG_NAME = "name"

    const val AUTOMATION_EDITOR_ROUTE =
        "$AUTOMATION_EDITOR?$AUTOMATION_ARG_ID={$AUTOMATION_ARG_ID}" +
            "&$AUTOMATION_ARG_NAME={$AUTOMATION_ARG_NAME}"

    fun automationEditor(automationId: String? = null, name: String? = null): String {
        if (automationId.isNullOrBlank()) return "$AUTOMATION_EDITOR?"
        val encoded = Uri.encode(name.orEmpty())
        return "$AUTOMATION_EDITOR?$AUTOMATION_ARG_ID=$automationId" +
            "&$AUTOMATION_ARG_NAME=$encoded"
    }
}
