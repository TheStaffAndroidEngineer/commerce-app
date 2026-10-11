package staff.android.commerce
/** Small allowlist: neither raw URLs, search text, product IDs, tokens nor exception messages. */
interface ReportSink { fun record(operation:String,category:String) }
class ReportPolicy(private val sink:ReportSink) {
    fun failure(operation:String,error:Exception){
        val safeOperation=operation.takeIf{it in setOf("open_store","save_cart","submit_order")}?:"other"
        val category=when(error){is java.io.IOException->"io";is IllegalArgumentException->"contract";else->"unexpected"}
        sink.record(safeOperation,category)
    }
}
