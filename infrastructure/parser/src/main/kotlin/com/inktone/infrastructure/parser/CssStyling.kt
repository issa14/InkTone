package com.inktone.infrastructure.parser

import org.jsoup.nodes.Element

/**
 * Effet du CSS sur un élément, limité à ce que le modèle de texte sait rendre : italique, gras et
 * contenu masqué (audit UX D16). `null` = non précisé.
 */
internal data class CssEffect(val italic: Boolean? = null, val bold: Boolean? = null, val hidden: Boolean? = null) {
    /** [other] l'emporte là où il précise quelque chose. */
    fun overriddenBy(other: CssEffect) = CssEffect(
        italic = other.italic ?: italic,
        bold = other.bold ?: bold,
        hidden = other.hidden ?: hidden,
    )

    val isEmpty get() = italic == null && bold == null && hidden == null
}

private data class CssRule(val tag: String?, val classes: Set<String>, val id: String?, val effect: CssEffect)

/** Feuille de style réduite aux sélecteurs simples ; tout autre sélecteur est ignoré. */
internal class StyleSheet private constructor(private val rules: List<CssRule>) {

    fun effectFor(element: Element): CssEffect {
        var result = CssEffect()
        for (rule in rules) {
            if (rule.tag != null && rule.tag != element.normalName()) continue
            if (rule.id != null && rule.id != element.id()) continue
            if (!element.classNames().containsAll(rule.classes)) continue
            result = result.overriddenBy(rule.effect)
        }
        return result
    }

    companion object {
        val EMPTY = StyleSheet(emptyList())

        fun parse(css: String): StyleSheet {
            val rules = mutableListOf<CssRule>()
            var rest = stripAtRules(css.replace(COMMENT, ""))
            while (true) {
                val open = rest.indexOf('{')
                if (open < 0) break
                val close = rest.indexOf('}', open)
                if (close < 0) break
                val effect = parseDeclarations(rest.substring(open + 1, close))
                if (!effect.isEmpty) {
                    for (selector in rest.substring(0, open).split(',')) {
                        simpleRule(selector.trim(), effect)?.let(rules::add)
                    }
                }
                rest = rest.substring(close + 1)
            }
            return StyleSheet(rules)
        }

        private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val SIMPLE = Regex("^([a-zA-Z][a-zA-Z0-9]*)?((?:\\.[\\w-]+)*)(?:#([\\w-]+))?$")

        private fun simpleRule(selector: String, effect: CssEffect): CssRule? {
            if (selector.isEmpty()) return null
            val match = SIMPLE.matchEntire(selector) ?: return null
            val tag = match.groupValues[1].lowercase().ifEmpty { null }
            val classes = match.groupValues[2].split('.').filter { it.isNotEmpty() }.toSet()
            val id = match.groupValues[3].ifEmpty { null }
            if (tag == null && classes.isEmpty() && id == null) return null
            return CssRule(tag, classes, id, effect)
        }

        /** Retire les règles `@...` (`@media`, `@font-face`, `@page`), accolades imbriquées comprises. */
        private fun stripAtRules(css: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < css.length) {
                if (css[i] == '@') {
                    val open = css.indexOf('{', i)
                    val semi = css.indexOf(';', i)
                    if (open < 0 || (semi in 0 until open)) {
                        i = if (semi < 0) css.length else semi + 1
                        continue
                    }
                    var depth = 0
                    var j = open
                    while (j < css.length) {
                        if (css[j] == '{') depth++
                        if (css[j] == '}') { depth--; if (depth == 0) break }
                        j++
                    }
                    i = j + 1
                } else {
                    out.append(css[i]); i++
                }
            }
            return out.toString()
        }
    }
}

/** Déclarations CSS (`font-style: italic; ...`), du `style` d'un élément ou du corps d'une règle. */
internal fun parseDeclarations(block: String): CssEffect {
    var effect = CssEffect()
    for (declaration in block.split(';')) {
        val name = declaration.substringBefore(':', "").trim().lowercase()
        val value = declaration.substringAfter(':', "").substringBefore("!important").trim().lowercase()
        if (name.isEmpty() || value.isEmpty()) continue
        effect = effect.overriddenBy(
            when (name) {
                "font-style" -> when {
                    value.startsWith("italic") || value.startsWith("oblique") -> CssEffect(italic = true)
                    value == "normal" -> CssEffect(italic = false)
                    else -> CssEffect()
                }
                "font-weight" -> when {
                    value == "bold" || value == "bolder" || (value.toIntOrNull() ?: 0) >= 600 -> CssEffect(bold = true)
                    value == "normal" || value == "lighter" || (value.toIntOrNull() ?: 1000) < 600 -> CssEffect(bold = false)
                    else -> CssEffect()
                }
                "display" -> if (value == "none") CssEffect(hidden = true) else CssEffect(hidden = false)
                "visibility" -> if (value == "hidden") CssEffect(hidden = true) else CssEffect()
                else -> CssEffect()
            },
        )
    }
    return effect
}
