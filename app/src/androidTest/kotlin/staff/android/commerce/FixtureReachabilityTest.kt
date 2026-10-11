package staff.android.commerce
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import staff.android.commerce.data.shopApi
@RunWith(AndroidJUnit4::class)
class FixtureReachabilityTest {
    @get:org.junit.Rule(order=0) val nearby = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
    @Test fun typedCatalogueReachesLocalServerThroughConfiguredClient()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val api=shopApi("http://10.0.2.2:8788/",DiagnosticTools.http(context))
        val page=api.catalog("tea","All",null)
        assertEquals("tea",page.items.single().id)
        // Traverse with the same client: one successful first page does not
        // prove the fixture's connection lifecycle supports a later append.
        val first=api.catalog("","All",null)
        val second=api.catalog("","All",first.nextCursor)
        val third=api.catalog("","All",second.nextCursor)
        assertEquals(first.snapshot,second.snapshot)
        assertEquals(first.snapshot,third.snapshot)
        assertEquals(listOf("bag","headphones","tea","camera","lamp","mug"),
            (first.items+second.items+third.items).map{it.id})
        assertNull(third.nextCursor)
    }
    @Test fun reportingPolicyOmitsRawFailureContent(){
        val captured=mutableListOf<Pair<String,String>>()
        val policy=ReportPolicy(object:ReportSink{override fun record(operation:String,category:String){captured+=operation to category}})
        policy.failure("customer@example.com",java.io.IOException("Bearer private-token"))
        assertEquals(listOf("other" to "io"),captured)
    }
}
