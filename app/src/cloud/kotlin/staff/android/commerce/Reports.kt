package staff.android.commerce
import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
object Reports:ReportSink {
    const val configured=true
    fun enabled():Boolean=allowed
    @Volatile private var allowed=false
    fun initialize(context:Context){
        allowed=context.getSharedPreferences("report-consent",Context.MODE_PRIVATE).getBoolean("allowed",false)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(allowed)
        if(!allowed)FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }
    fun consent(context:Context,granted:Boolean){
        context.getSharedPreferences("report-consent",Context.MODE_PRIVATE).edit().putBoolean("allowed",granted).apply()
        allowed=granted
        val sdk=FirebaseCrashlytics.getInstance();sdk.setCrashlyticsCollectionEnabled(granted)
        if(!granted)sdk.deleteUnsentReports()
    }
    override fun record(operation:String,category:String){
        if(!allowed)return
        val sdk=FirebaseCrashlytics.getInstance()
        sdk.setCustomKey("operation",operation.takeIf{it in setOf("open_store","save_cart","submit_order")}?:"other")
        sdk.setCustomKey("category",category.takeIf{it in setOf("io","contract","unexpected")}?:"unexpected")
        // The raw throwable is deliberately absent; crash stacks are a separate collection policy.
        sdk.recordException(IllegalStateException("Common Goods handled failure"))
    }
}
