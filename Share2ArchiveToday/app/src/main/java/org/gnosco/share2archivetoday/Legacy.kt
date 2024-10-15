package org.gnosco.share2archivetoday

import android.net.Uri
import java.util.Collections

fun Uri.legacyGetQueryParameterNames(): Set<String> {
    if (isOpaque()) {
        throw UnsupportedOperationException("This isn't a hierarchical URI.")
    }

    val query: String = getEncodedQuery()
        ?: return emptySet()

    val names: MutableSet<String> = LinkedHashSet()
    var start = 0
    do {
        val next = query.indexOf('&', start)
        val end = if ((next == -1)) query.length else next

        var separator = query.indexOf('=', start)
        if (separator > end || separator == -1) {
            separator = end
        }

        val name = query.substring(start, separator)
        names.add(Uri.decode(name))

        // Move start to end of name.
        start = end + 1
    } while (start < query.length)

    return Collections.unmodifiableSet(names)
}

fun Uri.Builder.legacyClearQuery(): Uri.Builder {
    return query(null)
}