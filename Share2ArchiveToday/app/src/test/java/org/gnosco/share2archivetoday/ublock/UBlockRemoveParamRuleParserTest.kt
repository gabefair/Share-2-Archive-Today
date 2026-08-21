package org.gnosco.share2archivetoday.ublock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UBlockRemoveParamRuleParserTest {

    @Test
    fun bareRemoveparam_doesNotThrow() {
        val rule = UBlockRemoveParamRuleParser.parseLine("||example.org^\$removeparam")
        assertNotNull(rule)
        assertTrue(rule!!.paramSpec is ParamSpec.RemoveAll)
    }

    @Test
    fun stylesheetOnlyRule_isSkipped() {
        assertNull(
            UBlockRemoveParamRuleParser.parseLine("||p.typekit.net^\$stylesheet,removeparam")
        )
    }

    @Test
    fun fontOnlyRule_isSkipped() {
        assertNull(
            UBlockRemoveParamRuleParser.parseLine(
                "||use.typekit.net^\$font,removeparam=~/^(primer|subset_id)=/,domain=~fonts.adobe.com"
            )
        )
    }

    @Test
    fun valuedRemoveparam_parsesLiteral() {
        val rule = UBlockRemoveParamRuleParser.parseLine("\$removeparam=utm_id")
        assertNotNull(rule)
        assertEquals("utm_id", (rule!!.paramSpec as ParamSpec.Literal).name)
    }

    @Test
    fun domainAnchoredValued_parses() {
        val rule = UBlockRemoveParamRuleParser.parseLine("||raycast.com^\$removeparam=via")
        assertNotNull(rule)
        assertTrue(rule!!.urlFilter is UrlFilter.Domain)
    }
}
