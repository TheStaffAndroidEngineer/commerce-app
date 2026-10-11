package staff.android.commerce

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Real Room, Paging presenter, grid and HTTP fixture. Preserves Saved/Cart. */
@RunWith(AndroidJUnit4::class)
class PagingRetryTest {
    @get:Rule(order=0) val nearby = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
    @get:Rule(order=1) val fault = object:ExternalResource() {
        override fun before() { control("{\"appendError\":true}") }
        override fun after() { control("{}") }
    }
    @get:Rule(order=2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun retryPublishesCommittedRowsWithoutNavigationOrRefresh() {
        waitFor("nav-search")
        compose.onNodeWithTag("nav-search").performClick()
        waitFor("append-retry")
        compose.onNodeWithTag("product-bag").assertIsDisplayed()
        compose.onNodeWithTag("product-headphones").assertIsDisplayed()
        compose.onNodeWithTag("product-tea").assertDoesNotExist()
        val before=history()
        control("{}")
        compose.onNodeWithTag("append-retry").performClick()
        // No scroll, navigation or refresh may provide an incidental access hint.
        waitFor("product-tea")
        compose.onNodeWithTag("product-tea").assertIsDisplayed()
        compose.onNodeWithTag("product-bag").assertIsDisplayed()
        val appended=history().drop(before.size).filter { it.startsWith("/catalog?") }
        assertEquals(0,appended.count { "cursor=" !in it })
        val failedCursor=before.last { it.startsWith("/catalog?") && "cursor=" in it }
        assertEquals(failedCursor,appended.first())
    }
    private fun waitFor(tag:String) = compose.waitUntil(timeoutMillis=20_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun control(body:String) {
        val connection=URL("http://10.0.2.2:8788/__control").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=5000;connection.readTimeout=5000
            connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            check(connection.responseCode==200)
            connection.inputStream.use { it.readBytes() }
        } finally { connection.disconnect() }
    }
    private fun history():List<String> {
        val connection=URL("http://10.0.2.2:8788/__control").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout=5000;connection.readTimeout=5000
            val json=JSONObject(connection.inputStream.use { it.bufferedReader().readText() })
            val requests=json.getJSONArray("requests")
            (0 until requests.length()).map { requests.getString(it) }
        } finally { connection.disconnect() }
    }
}
