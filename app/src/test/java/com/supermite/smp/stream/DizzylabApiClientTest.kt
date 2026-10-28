package com.supermite.smp.stream

import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Synthetic responses only: these tests never use an account or contact Dizzylab. */
class DizzylabApiClientTest {
    private lateinit var server: MockWebServer
    private val fixtureToken = "synthetic-token-with-&-and-space "

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    private fun client(token: String = fixtureToken, cookie: String = "sessionid=synthetic-session") =
        DizzylabApiClient(token, cookie, server.url("/"))

    private fun respond(body: String, status: Int = 200, type: String = "application/json") {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", type).setBody(body))
    }

    private fun detail(price: Int = 0, owned: Boolean = false, media: String = "first") = """
        {"id":"fixture-disc","title":"Test album","price":$price,"onsell":true,
         "ihavethis":$owned,"ilikeit":false,"tracks":[
          {"id":"1","title":"Test track","authers":"Test artist",
           "url":"https://streaming.dizzylab.net/$media/track.mp3"}]}
    """.trimIndent()

    @Test fun privateLibraryEncodesTokenAndUsesRealPaginationWithoutCookie() {
        respond("""{"discs":[{"id":"fixture-disc","title":"Album"}],"canshowmore":true}""")
        val page = client().albums(DizzylabSection.PURCHASED, 24)
        assertEquals(48, page.nextOffset)
        assertTrue(page.hasMore)
        assertTrue(page.items.single().purchased)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/apis/getmydisc/", request.requestUrl!!.encodedPath)
        assertEquals("24", request.requestUrl!!.queryParameter("l"))
        assertEquals("48", request.requestUrl!!.queryParameter("r"))
        assertEquals(fixtureToken, request.requestUrl!!.queryParameter("token"))
        assertNull(request.getHeader("Cookie"))
    }

    @Test fun searchDoesNotTurnSpecialCharactersIntoMoreQueryParameters() {
        respond("""{"discs":[],"canshowmore":false}""")
        val query = "东方 & genre=ambient + ?"
        client().albums(DizzylabSection.SEARCH, 0, query)
        val request = server.takeRequest()!!
        assertEquals(query, request.requestUrl!!.queryParameter("keyword"))
        assertNull(request.requestUrl!!.queryParameter("genre"))
    }

    @Test fun missingTokenFailsBeforeSendingPrivateRequest() {
        assertThrows(IllegalStateException::class.java) { client(token = "").user() }
        assertEquals(0, server.requestCount)
    }

    @Test fun http200HtmlIsAnErrorAndNeverLeakedAsItsMessage() {
        respond("<html>synthetic-private-body</html>", type = "text/html; charset=utf-8")
        val error = assertThrows(IllegalStateException::class.java) { client().user() }
        assertFalse(error.message.orEmpty().contains("synthetic-private-body"))
        assertFalse(error.message.orEmpty().contains(fixtureToken))
    }

    @Test fun jsonWithMissingListIsAnErrorRatherThanAnEmptyLibrary() {
        respond("""{"error":"private upstream diagnostic"}""")
        val error = assertThrows(IllegalStateException::class.java) { client().albums(DizzylabSection.PURCHASED, 0) }
        assertFalse(error.message.orEmpty().contains("upstream"))
    }

    @Test fun trackIdentityDoesNotChangeWhenMediaSignatureChanges() {
        respond(detail(media = "signature-one"))
        respond(detail(media = "signature-two"))
        val first = client().details("fixture-disc").tracks.single()
        val second = client().details("fixture-disc").tracks.single()
        assertEquals(first.id, second.id)
        assertNotEquals(first.uri, second.uri)
        assertEquals(DizzylabTrackRef("fixture-disc", "1"), DizzylabTrackRef.from(first))
        assertNotEquals(first.id, DizzylabTrackRef("another-disc", "1").id)
    }

    @Test fun referenceRoundTripsAlbumIdsContainingSeparatorsAndUnicode() {
        val ref = DizzylabTrackRef("东方:demo / +", "1:bonus")
        respond(detail())
        val track = client().details("fixture-disc").tracks.single().copy(id = ref.id)
        assertEquals(ref, DizzylabTrackRef.from(track))
    }

    @Test fun webBridgeSendsCookieOnlyToWebPagesAndValidatesCurrentUser() {
        val token = "a".repeat(40) // Deliberately synthetic legacy token shape.
        respond("""<a href="/u/42"><i></i> 个人信息</a>""", type = "text/html")
        respond("""<script>const url='/apis/getthisdicsinfo/?discid=fixture&amp;token=$token';</script>""", type = "text/html")
        respond("""{"user":{"uid":42,"username":"Fixture","mail":"","cover":"","user_group":"test"}}""")
        val (value, user) = client(token = "").sessionFromWebsite()
        assertEquals(token, value)
        assertEquals("42", user.id)
        assertEquals("sessionid=synthetic-session", server.takeRequest()!!.getHeader("Cookie"))
        assertEquals("/u/42/music/", server.takeRequest()!!.path)
        val api = server.takeRequest()!!
        assertNull(api.getHeader("Cookie"))
        assertEquals(token, api.requestUrl!!.queryParameter("token"))
    }

    @Test fun webBridgeRefusesCrossOriginRedirectBeforeSendingCookie() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://127.0.0.1:1/foreign"))
        assertThrows(IllegalStateException::class.java) { client(token = "").sessionFromWebsite() }
        assertEquals(1, server.requestCount)
    }

    @Test fun freeClaimRechecksPriceAndNeverCreatesPaidCheckout() {
        respond(detail(price = 20))
        assertThrows(IllegalStateException::class.java) { client().claimFree("fixture-disc") }
        assertEquals(1, server.requestCount)
        assertEquals("/apis/getthisdicsinfo/", server.takeRequest()!!.requestUrl!!.encodedPath)
    }

    @Test fun alreadyOwnedClaimDoesNotSubmitAgain() {
        respond(detail(price = 20, owned = true))
        client().claimFree("fixture-disc")
        assertEquals(1, server.requestCount)
    }

    @Test fun freeClaimUsesSingleMultipartPostAfterFreeEntitlementCheck() {
        respond(detail())
        respond("""{"state":"ok"}""")
        client().claimFree("fixture-disc")
        assertEquals("GET", server.takeRequest()!!.method)
        val request = server.takeRequest()!!
        assertEquals("POST", request.method)
        assertEquals("/apis/unlockafreedisc/", request.path)
        assertNull(request.getHeader("Cookie"))
        assertTrue(request.body.readUtf8().contains("fixture-disc"))
        assertEquals(2, server.requestCount)
    }

    @Test fun redemptionPreflightDoesNotAutomaticallyConfirm() {
        respond("""{"res":4,"disc":{"id":"fixture-disc","title":"Preview"}}""")
        val preview = client().redeem(" SYNTHETIC-CDK ")
        assertEquals("4", preview.result)
        assertEquals("Preview", preview.album!!.title)
        val request = server.takeRequest()!!
        assertEquals("POST", request.method)
        assertEquals("/apis/redeem/", request.path)
        assertTrue(request.body.readUtf8().contains("SYNTHETIC-CDK"))
        assertEquals(1, server.requestCount)
    }

    @Test fun redemptionConfirmationRetainsServersOriginalPathSpelling() {
        respond("""{"res":"1"}""")
        assertEquals("1", client().redeem("SYNTHETIC-CDK", confirm = true).result)
        assertEquals("/apis/comfirm_redeem/", server.takeRequest()!!.path)
    }

    @Test fun toggleFailureIsNotAutomaticallyRetried() {
        respond("""{"error":"internal"}""", status = 500)
        assertThrows(IllegalStateException::class.java) { client().setAlbumLike("fixture-disc") }
        assertEquals(1, server.requestCount)
    }

    @Test fun followingResponseUsesSocialObjectsDespiteDiscsKey() {
        respond("""{"discs":[{"id":12,"title":"Fixture Circle","cover":"","wantsomemail":true}],"canshowmore":false}""")
        val page = client().circles(0, following = true)
        assertEquals("12", page.items.single().id)
        assertEquals("Fixture Circle", page.items.single().name)
        assertFalse(page.hasMore)
    }

    @Test fun mediaRejectsHostSpoofingUnsafeSchemesPortsAndUserInfo() {
        listOf("https://streaming.dizzylab.net.evil.example/a.mp3", "http://streaming.dizzylab.net/a.mp3",
            "https://streaming.dizzylab.net:444/a.mp3", "https://secret@streaming.dizzylab.net/a.mp3").forEach { url ->
            assertThrows(IllegalStateException::class.java) { DizzylabApiClient.mediaUrl(url) }
        }
        assertEquals("https://streaming.dizzylab.net/a.mp3", DizzylabApiClient.mediaUrl("https://streaming.dizzylab.net/a.mp3"))
    }
}
