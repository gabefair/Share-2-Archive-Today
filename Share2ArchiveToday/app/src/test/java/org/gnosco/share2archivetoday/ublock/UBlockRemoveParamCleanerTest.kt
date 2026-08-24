package org.gnosco.share2archivetoday.ublock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UBlockRemoveParamCleanerTest {

    private val cleaner = UBlockRemoveParamCleaner(RuntimeEnvironment.getApplication())

    @Test
    fun rulesAreLoaded() {
        assertTrue("uBO removeparam rules should load from assets", cleaner.areRulesLoaded())
    }

    @Test
    fun genericRemoval_stripsFbclid() {
        val input = "https://example.com/page?fbclid=abc123&title=hello"
        val result = cleaner.cleanUrl(input)
        assertEquals("https://example.com/page?title=hello", result)
    }

    @Test
    fun genericRemoval_stripsTgclid() {
        val input = "https://example.com/article?id=1&tgclid=track123"
        val result = cleaner.cleanUrl(input)
        assertEquals("https://example.com/article?id=1", result)
    }

    @Test
    fun domainSpecific_removesEntryPointOnFifa() {
        val input = "https://plus.fifa.com/en/player/abc?catalogId=xyz&entryPoint=CTA"
        val result = cleaner.cleanUrl(input)
        assertFalse("entryPoint should be removed on plus.fifa.com", result.contains("entryPoint="))
        assertTrue("catalogId should be preserved", result.contains("catalogId=xyz"))
    }

    @Test
    fun domainSpecific_doesNotRemoveOnOtherDomains() {
        val input = "https://other-site.com/page?entryPoint=CTA&id=1"
        val result = cleaner.cleanUrl(input)
        assertEquals(input, result)
    }

    @Test
    fun regexRemoval_stripsAtCustomParams() {
        val input = "https://example.com/?at_custom1=foo&at_campaign=bar&keep=yes"
        val result = cleaner.cleanUrl(input)
        assertFalse(result.contains("at_custom1="))
        assertFalse(result.contains("at_campaign="))
        assertTrue(result.contains("keep=yes"))
    }

    @Test
    fun exception_preservesUtmTermOnMetabase() {
        val input = "https://metabase.com/docs?utm_term=search&article=1"
        val result = cleaner.cleanUrl(input)
        assertTrue("utm_term should be preserved on metabase.com", result.contains("utm_term=search"))
        assertTrue("non-tracking params should be preserved", result.contains("article=1"))
    }

    @Test
    fun toModifier_onlyAppliesOnListedDestinations() {
        val onRakuten = "https://item.rakuten.co.jp/shop/item?iasid=track123&item=1"
        val offRakuten = "https://example.com/page?iasid=track123&item=1"

        val rakutenResult = cleaner.cleanUrl(onRakuten)
        val otherResult = cleaner.cleanUrl(offRakuten)

        assertFalse("iasid should be removed on rakuten", rakutenResult.contains("iasid="))
        assertEquals("iasid should not be removed on unrelated sites", offRakuten, otherResult)
    }

    @Test
    fun removeAll_stripsQueryOnMatchingDomain() {
        val input = "https://777casino.top/landing?ref=abc&campaign=xyz"
        val result = cleaner.cleanUrl(input)
        assertEquals("https://777casino.top/landing", result)
    }

    @Test
    fun noOp_whenNoMatchingParams() {
        val input = "https://example.com/page?article=1&section=news"
        val result = cleaner.cleanUrl(input)
        assertEquals(input, result)
    }

    @Test
    fun noOp_whenNoQueryParams() {
        val input = "https://example.com/page"
        val result = cleaner.cleanUrl(input)
        assertEquals(input, result)
    }
}

class UBlockRemoveParamRuleParserTest {

    @Test
    fun parseGenericLiteralRule() {
        val rule = UBlockRemoveParamRuleParser.parseLine("\$removeparam=fbclid")
        assertNotNull(rule)
        assertFalse(rule!!.isException)
        assertTrue(rule.urlFilter is UrlFilter.Generic)
        assertEquals(ParamSpec.Literal("fbclid"), rule.paramSpec)
    }

    @Test
    fun parseGenericRegexRule() {
        val rule = UBlockRemoveParamRuleParser.parseLine("\$removeparam=/^cm_mmc/")
        assertNotNull(rule)
        assertTrue(rule!!.paramSpec is ParamSpec.RegexParam)
    }

    @Test
    fun parseDomainRule() {
        val rule = UBlockRemoveParamRuleParser.parseLine("||github.com^\$removeparam=email_source")
        assertNotNull(rule)
        val domain = rule!!.urlFilter as UrlFilter.Domain
        assertEquals("github.com", domain.domainPattern)
        assertEquals(ParamSpec.Literal("email_source"), rule.paramSpec)
    }

    @Test
    fun parseExceptionRule() {
        val rule = UBlockRemoveParamRuleParser.parseLine("@@||metabase.com^\$removeparam=utm_term")
        assertNotNull(rule)
        assertTrue(rule!!.isException)
    }

    @Test
    fun parseDomainModifierRule() {
        val rule = UBlockRemoveParamRuleParser.parseLine("\$domain=atlanticcouncil.org|digikey.com,removeparam=/^mkt_tok/")
        assertNotNull(rule)
        assertEquals(listOf("atlanticcouncil.org", "digikey.com"), rule!!.domainRestriction)
    }

    @Test
    fun parseToExclusionModifier() {
        val rule = UBlockRemoveParamRuleParser.parseLine(
            "\$removeparam=elqTrackId,to=~app.econnect.utexas.edu|~clk.texaslonghorns.com"
        )
        assertNotNull(rule)
        assertEquals(
            listOf("app.econnect.utexas.edu", "clk.texaslonghorns.com"),
            rule!!.toExclusions
        )
    }

    @Test
    fun skipsXhrOnlyRules() {
        val rule = UBlockRemoveParamRuleParser.parseLine("||edge.curalate.com/v1/media/\$xhr,removeparam=appId")
        assertEquals(null, rule)
    }

    @Test
    fun keepsDocumentRules() {
        val rule = UBlockRemoveParamRuleParser.parseLine("||soundcore.com^\$document,removeparam=ref")
        assertNotNull(rule)
        assertEquals(ParamSpec.Literal("ref"), rule!!.paramSpec)
    }

    @Test
    fun domainPatternMatchesSubdomains() {
        assertTrue(domainPatternMatches("github.com", "api.github.com"))
        assertTrue(domainPatternMatches("github.com", "github.com"))
        assertFalse(domainPatternMatches("github.com", "notgithub.com"))
    }

    @Test
    fun domainPatternMatchesWildcards() {
        assertTrue(domainPatternMatches("notebooklm.google.*", "notebooklm.google.com"))
        assertFalse(domainPatternMatches("notebooklm.google.*", "google.com"))
    }
}
