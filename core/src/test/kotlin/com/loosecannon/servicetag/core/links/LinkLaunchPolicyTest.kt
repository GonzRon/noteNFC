package com.loosecannon.servicetag.core.links

import com.loosecannon.servicetag.core.model.LinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LinkLaunchPolicyTest {
    private val joplin = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"

    @Test fun extractsTheFirstUriFromShareText() {
        assertEquals(joplin, LinkLaunchPolicy.extractUri("Pool chemistry log\n$joplin"))
        assertEquals(joplin, LinkLaunchPolicy.extractUri("$joplin (shared from Joplin)"))
        assertEquals("https://a.example/x", LinkLaunchPolicy.extractUri("see https://a.example/x and https://b.example/y"))
    }
    @Test fun trailingPunctuationIsNotPartOfTheUri() {
        assertEquals("https://a.example/x", LinkLaunchPolicy.extractUri("Look at https://a.example/x."))
        assertEquals("https://a.example/x", LinkLaunchPolicy.extractUri("(https://a.example/x)"))
    }
    @Test fun noUriMeansNull() {
        assertNull(LinkLaunchPolicy.extractUri("just some words"))
        assertNull(LinkLaunchPolicy.extractUri(null))
        assertNull(LinkLaunchPolicy.extractUri(""))
    }
    @Test fun allowedSchemesAreAcceptedWithTheirKind() {
        assertEquals(LinkCheck.Accepted(LinkKind.JOPLIN, joplin), LinkLaunchPolicy.check(joplin))
        assertEquals(LinkKind.OBSIDIAN, (LinkLaunchPolicy.check("obsidian://open?vault=v&file=f") as LinkCheck.Accepted).kind)
        assertEquals(LinkKind.LOGSEQ, (LinkLaunchPolicy.check("logseq://graph/g?page=p") as LinkCheck.Accepted).kind)
        assertEquals(LinkKind.WEB, (LinkLaunchPolicy.check("http://a.example/") as LinkCheck.Accepted).kind)
        assertEquals(LinkKind.WEB, (LinkLaunchPolicy.check("https://a.example/") as LinkCheck.Accepted).kind)
    }
    @Test fun schemeMatchingIsCaseInsensitive() {
        assertEquals(LinkKind.WEB, (LinkLaunchPolicy.check("HTTPS://a.example/") as LinkCheck.Accepted).kind)
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("JavaScript:alert(1)"))
    }
    @Test fun everyBlockedSchemeIsRejected() {
        for (s in listOf("javascript", "file", "content", "intent", "android-app", "tel", "sms", "mailto")) {
            assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("$s:whatever"), "scheme $s")
            assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("$s://whatever"), "scheme $s")
        }
    }
    @Test fun unknownSchemeNeedsConfirmation() {
        assertEquals(LinkCheck.NeedsConfirmation("bear", "bear://x-callback-url/open-note?id=1"), LinkLaunchPolicy.check("bear://x-callback-url/open-note?id=1"))
    }
    @Test fun surroundingWhitespaceIsTrimmedButInnerWhitespaceRejects() {
        assertEquals(LinkCheck.Accepted(LinkKind.WEB, "https://a.example/"), LinkLaunchPolicy.check("  https://a.example/ \n"))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("https://a.example/ b"))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("https://a.example/\u0000"))
    }
    @Test fun noSchemeRejects() {
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("a.example/path"))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check(""))
        assertIs<LinkCheck.Rejected>(LinkLaunchPolicy.check("1http://a.example/"))
    }
}
