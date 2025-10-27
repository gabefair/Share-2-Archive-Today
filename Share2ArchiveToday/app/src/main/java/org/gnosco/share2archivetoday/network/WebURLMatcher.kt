package org.gnosco.share2archivetoday.network

import org.gnosco.share2archivetoday.network.*

import java.util.regex.Matcher
import java.util.regex.Pattern

object WebURLMatcher {
    private val IP_ADDRESS
            : Pattern = Pattern.compile(
        "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                + "|[1-9][0-9]|[0-9]))"
    )

    /**
     * Valid UCS characters defined in RFC 3987. Excludes space characters.
     */
    private val UCS_CHAR = ("[" +
            "\u00A0-\uD7FF" +
            "\uF900-\uFDCF" +
            "\uFDF0-\uFFEF" +
            "\uD800\uDC00-\uD83F\uDFFD" +
            "\uD840\uDC00-\uD87F\uDFFD" +
            "\uD880\uDC00-\uD8BF\uDFFD" +
            "\uD8C0\uDC00-\uD8FF\uDFFD" +
            "\uD900\uDC00-\uD93F\uDFFD" +
            "\uD940\uDC00-\uD97F\uDFFD" +
            "\uD980\uDC00-\uD9BF\uDFFD" +
            "\uD9C0\uDC00-\uD9FF\uDFFD" +
            "\uDA00\uDC00-\uDA3F\uDFFD" +
            "\uDA40\uDC00-\uDA7F\uDFFD" +
            "\uDA80\uDC00-\uDABF\uDFFD" +
            "\uDAC0\uDC00-\uDAFF\uDFFD" +
            "\uDB00\uDC00-\uDB3F\uDFFD" +
            "\uDB44\uDC00-\uDB7F\uDFFD" +
            "&&[^\u00A0[\u2000-\u200A]\u2028\u2029\u202F\u3000]]")

    /**
     * Valid characters for IRI label defined in RFC 3987.
     */
    private val LABEL_CHAR = "a-zA-Z0-9" + UCS_CHAR

    /**
     * Valid characters for IRI TLD defined in RFC 3987.
     */
    private val TLD_CHAR = "a-zA-Z" + UCS_CHAR

    /**
     * RFC 1035 Section 2.3.4 limits the labels to a maximum 63 octets.
     */
    private val IRI_LABEL =
        "[" + LABEL_CHAR + "](?:[" + LABEL_CHAR + "_\\-]{0,61}[" + LABEL_CHAR + "]){0,1}"

    /**
     * RFC 3492 references RFC 1034 and limits Punycode algorithm output to 63 characters.
     */
    private val PUNYCODE_TLD = "xn\\-\\-[\\w\\-]{0,58}\\w"
    private val TLD = "(" + PUNYCODE_TLD + "|" + "[" + TLD_CHAR + "]{2,63}" + ")"
    private val HOST_NAME = "(" + IRI_LABEL + "\\.)+" + TLD
    val DOMAIN_NAME
            : Pattern = Pattern.compile("(" + HOST_NAME + "|" + IP_ADDRESS + ")")
    private val PROTOCOL = "(?i:http|https|rtsp)://"

    /* A word boundary or end of input.  This is to stop foo.sure from matching as foo.su */
    private val WORD_BOUNDARY = "(?:\\b|$|^)"
    private val USER_INFO = (("(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
            + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
            + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@"))
    private val PORT_NUMBER = "\\:\\d{1,5}"
    private val PATH_AND_QUERY = ("[/\\?](?:(?:[" + LABEL_CHAR
            + ";/\\?:@&=#~" // plus optional query params
            + "\\-\\.\\+!\\*'\\(\\),_\\$])|(?:%[a-fA-F0-9]{2}))*")

    /**
     * Regular expression pattern to match most part of RFC 3987
     * Internationalized URLs, aka IRIs.
     */
    private val WEB_URL: Pattern = Pattern.compile(
        (("("
                + "("
                + "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")?"
                + "(?:" + DOMAIN_NAME + ")"
                + "(?:" + PORT_NUMBER + ")?"
                + ")"
                + "(" + PATH_AND_QUERY + ")?"
                + WORD_BOUNDARY
                + ")"))
    )

    fun matcher(text: String): Matcher {
        return WEB_URL.matcher(text)
    }
}