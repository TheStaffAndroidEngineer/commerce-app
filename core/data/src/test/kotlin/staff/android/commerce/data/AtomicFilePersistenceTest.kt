package staff.android.commerce.data
import android.util.AtomicFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric checks the Android AtomicFile API boundary; no device fsync or process-kill claim. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[37],manifest=Config.NONE)
class AtomicFilePersistenceTest {
    @Test fun unfinishedReplacementDoesNotBecomeAcceptedShopping()=runBlocking {
        val file=File(RuntimeEnvironment.getApplication().cacheDir,"atomic-${java.util.UUID.randomUUID()}.json")
        val store=AtomicJsonCommercePersistence(file)
        val accepted=StoredCommerce(savedIds=setOf("tea"),quantities=mapOf("tea" to 2),revision=4)
        store.write(accepted)
        val interrupted=AtomicFile(file).startWrite()
        interrupted.write("unfinished replacement".toByteArray());interrupted.close()
        assertEquals(accepted,store.read())
    }
    @Test fun corruptAcceptedFileIsReportedWithoutOverwritingIt()=runBlocking {
        val file=File(RuntimeEnvironment.getApplication().cacheDir,"corrupt-${java.util.UUID.randomUUID()}.json")
        file.writeText("not JSON")
        try {AtomicJsonCommercePersistence(file).read();fail("corruption must be reported")}
        catch(_:java.io.IOException){}
        assertEquals("not JSON",file.readText())
    }
}
