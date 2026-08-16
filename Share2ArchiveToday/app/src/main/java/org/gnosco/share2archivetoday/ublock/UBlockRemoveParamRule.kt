package org.gnosco.share2archivetoday.ublock

import android.util.Log
/**
 * Parsed uBlock Origin $removeparam rule from privacy-removeparam.txt
 */
internal data class UBlockRemoveParamRule(
    val isException: Boolean,
    val urlFilter: UrlFilter,
    val paramSpec: ParamSpec,
    val domainRestriction: List<String>? = null,
    val toInclusions: List<String>? = null,
    val toExclusions: List<String>? = null,
) {
    fun appliesToHost(host: String): Boolean {
        domainRestriction?.let { domains ->
            if (!domains.any { domainPatternMatches(it, host) }) {
                return false
            }
        }
        toInclusions?.let { inclusions ->
            if (!inclusions.any { domainPatternMatches(it, host) }) {
                return false
            }
        }
        toExclusions?.let { exclusions ->
            if (exclusions.any { domainPatternMatches(it, host) }) {
                return false
            }
        }
        return true
    }

    fun matchesUrl(url: String, host: String, path: String): Boolean {
        if (!appliesToHost(host)) return false
        return urlFilter.matches(url, host, path)
    }

    fun matchesParam(paramName: String): Boolean = paramSpec.matches(paramName)
}

internal sealed class ParamSpec {
    data object RemoveAll : ParamSpec()
    data class Literal(val name: String) : ParamSpec()
    data class RegexParam(val pattern: Regex) : ParamSpec()

    fun matches(paramName: String): Boolean = when (this) {
        RemoveAll -> true
        is Literal -> paramName == name
        is RegexParam -> pattern.matches(paramName)
    }
}

internal sealed class UrlFilter {
    data object Generic : UrlFilter()

    data class Domain(
        val domainPattern: String,
        val pathPrefix: String? = null,
    ) : UrlFilter()

    data class Pattern(val regex: Regex) : UrlFilter()

    fun matches(url: String, host: String, path: String): Boolean = when (this) {
        Generic -> true
        is Domain -> {
            if (!domainPatternMatches(domainPattern, host)) return false
            pathPrefix == null || path.startsWith(pathPrefix)
        }
        is Pattern -> regex.containsMatchIn(url)
    }
}

internal object UBlockRemoveParamRuleParser {
    private const val TAG = "UBlockRemoveParamParser"
    private val RESOURCE_ONLY_MODIFIERS = Regex("""\$?(?:xhr|script|image)\b|,xhr\b|,script\b|,image\b""")

    fun parseLine(line: String): UBlockRemoveParamRule? {
        var text = line.trim()
        if (text.isEmpty() || text.startsWith("!")) return null

        val isException = text.startsWith("@@")
        if (isException) text = text.substring(2)

        if (RESOURCE_ONLY_MODIFIERS.containsMatchIn(text) &&
            !text.contains("\$document") && !text.contains("\$doc") && !text.contains(",doc")
        ) {
            return null
        }

        val removeParamClause = extractRemoveParamClause(text) ?: return null
        val urlPart = text.substring(0, removeParamClause.startIndex).trim()
        val paramSpec = parseParamSpec(removeParamClause.value) ?: return null

        val allModifiers = urlPart + removeParamClause.trailingModifiers
        val modifiers = parseModifiers(allModifiers)

        val domainRestriction = modifiers["domain"]?.split("|")?.filter { it.isNotEmpty() }
        val (toInclusions, toExclusions) = parseToModifier(modifiers["to"])

        val urlFilter = parseUrlFilter(urlPart)
            ?: if (domainRestriction != null) UrlFilter.Generic else return null

        return UBlockRemoveParamRule(
            isException = isException,
            urlFilter = urlFilter,
            paramSpec = paramSpec,
            domainRestriction = domainRestriction,
            toInclusions = toInclusions,
            toExclusions = toExclusions,
        )
    }

    fun parseAll(lines: Sequence<String>): List<UBlockRemoveParamRule> {
        return lines.mapNotNull { line ->
            try {
                parseLine(line)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse rule: $line", e)
                null
            }
        }.toList()
    }

    private data class RemoveParamClause(
        val startIndex: Int,
        val value: String?,
        val trailingModifiers: String,
    )

    private fun extractRemoveParamClause(text: String): RemoveParamClause? {
        val patterns = listOf(
            Regex("${'$'}removeparam=([^,${'$'}]+)"),
            Regex(",removeparam=([^,${'$'}]+)"),
            Regex("${'$'}removeparam${'$'}"),
            Regex(",removeparam${'$'}"),
        )

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groups[1]?.value
            val trailingStart = match.range.last + 1
            val trailing = if (trailingStart < text.length) text.substring(trailingStart) else ""
            return RemoveParamClause(match.range.first, value, trailing)
        }
        return null
    }

    private fun parseParamSpec(value: String?): ParamSpec? {
        if (value == null) return ParamSpec.RemoveAll
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ParamSpec.RemoveAll

        if (trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2) {
            val regexBody = trimmed.substring(1, trimmed.length - 1)
            return try {
                ParamSpec.RegexParam(Regex(regexBody))
            } catch (e: Exception) {
                Log.w(TAG, "Invalid regex in removeparam: $trimmed", e)
                null
            }
        }

        return ParamSpec.Literal(trimmed)
    }

    private fun parseModifiers(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val modifierPattern = Regex("""(?:^|[,\s\$])(domain|to)=([^,\$]+)""")
        for (match in modifierPattern.findAll(text)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            result[key] = value
        }
        return result
    }

    private fun parseToModifier(toValue: String?): Pair<List<String>?, List<String>?> {
        if (toValue.isNullOrBlank()) return null to null
        val parts = toValue.split("|").filter { it.isNotEmpty() }
        val exclusions = parts.filter { it.startsWith("~") }.map { it.removePrefix("~") }
        val inclusions = parts.filter { !it.startsWith("~") }
        return when {
            inclusions.isNotEmpty() && exclusions.isEmpty() -> inclusions to null
            exclusions.isNotEmpty() && inclusions.isEmpty() -> null to exclusions
            inclusions.isNotEmpty() -> inclusions to exclusions.ifEmpty { null }
            else -> null to null
        }
    }

    private fun parseUrlFilter(urlPart: String): UrlFilter? {
        val part = urlPart.trim()
        if (part.isEmpty() || part == "*") return UrlFilter.Generic

        if (part.startsWith("\$domain=")) return UrlFilter.Generic

        if (part.startsWith("||")) {
            val withoutAnchor = part.substring(2)
            val separatorIndex = findDomainSeparator(withoutAnchor)
            val domainPattern = if (separatorIndex == -1) {
                withoutAnchor.trimEnd('^', '/')
            } else {
                withoutAnchor.substring(0, separatorIndex).trimEnd('^')
            }
            val remainder = if (separatorIndex == -1) "" else withoutAnchor.substring(separatorIndex)
            val pathPrefix = remainder.trimStart('^', '/').trimEnd('$').takeIf { it.isNotEmpty() }?.let { "/$it" }
            return UrlFilter.Domain(domainPattern, pathPrefix)
        }

        return try {
            UrlFilter.Pattern(uboUrlFilterToRegex(part))
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse URL filter: $part", e)
            null
        }
    }

    private fun findDomainSeparator(filter: String): Int {
        val pathIndex = filter.indexOf('/')
        val anchorIndex = filter.indexOf('^')
        return when {
            pathIndex == -1 -> anchorIndex
            anchorIndex == -1 -> pathIndex
            else -> minOf(pathIndex, anchorIndex)
        }
    }

    private fun uboUrlFilterToRegex(filter: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < filter.length) {
            when (val ch = filter[i]) {
                '*' -> sb.append(".*")
                '^' -> { /* separator, ignore in regex body */ }
                '|' -> sb.append('|')
                '.', '?', '+', '(', ')', '[', ']', '{', '}', '\\', '$' -> {
                    sb.append('\\').append(ch)
                }
                else -> sb.append(ch)
            }
            i++
        }
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}

internal fun domainPatternMatches(pattern: String, host: String): Boolean {
    val normalizedHost = host.lowercase()
    val normalizedPattern = pattern.lowercase().trimEnd('^', '/')

    if (normalizedPattern.contains("*")) {
        val regexPattern = normalizedPattern
            .replace(".", "\\.")
            .replace("*", ".*")
        return Regex("^$regexPattern$", RegexOption.IGNORE_CASE).matches(normalizedHost)
    }

    return normalizedHost == normalizedPattern ||
        normalizedHost.endsWith(".$normalizedPattern")
}
