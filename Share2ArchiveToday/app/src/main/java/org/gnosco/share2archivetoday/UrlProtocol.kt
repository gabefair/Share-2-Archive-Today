package org.gnosco.share2archivetoday

private val HTTP_PROTOCOL_REGEX = Regex("""https?://""", RegexOption.IGNORE_CASE)
private val HTTP_PROTOCOL_PREFIX_REGEX = Regex("""^https?://""", RegexOption.IGNORE_CASE)

internal fun findFirstHttpProtocolStart(text: String): Int? {
    return HTTP_PROTOCOL_REGEX.find(text)?.range?.first
}

internal fun findLastHttpProtocolStart(text: String): Int? {
    return HTTP_PROTOCOL_REGEX.findAll(text).lastOrNull()?.range?.first
}

internal fun hasHttpProtocol(url: String): Boolean {
    return HTTP_PROTOCOL_PREFIX_REGEX.containsMatchIn(url)
}

internal fun normalizeUrlProtocol(url: String): String {
    val match = Regex("""^(https?)://(.*)$""", RegexOption.IGNORE_CASE).find(url) ?: return url
    val protocol = match.groupValues[1].lowercase()
    val remainder = match.groupValues[2]
    return "$protocol://$remainder"
}
