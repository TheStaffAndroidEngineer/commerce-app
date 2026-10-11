package staff.android.commerce
import android.app.Application
import android.content.Context
import android.view.View
import com.chuckerteam.chucker.api.ChuckerInterceptor
import leakcanary.AppWatcher
import okhttp3.OkHttpClient
import staff.android.commerce.data.shopHttpClient
import timber.log.Timber

object DiagnosticTools {
    private var retained:View?=null
    fun install(app:Application){Timber.plant(Timber.DebugTree())}
    fun http(context:Context):OkHttpClient=shopHttpClient().newBuilder().addInterceptor(
        ChuckerInterceptor.Builder(context).redactHeaders("Authorization","Cookie","Set-Cookie","Idempotency-Key")
            .maxContentLength(0).alwaysReadResponseBody(false).build()).build()
    fun inspect(context:Context){context.startActivity(com.chuckerteam.chucker.api.Chucker.getLaunchIntent(context))}
    // Explicit debug exercise only. Detach the view, then ask the watcher to expect collection.
    fun retainForLab(view:View){leakcanary.LeakCanary.config=leakcanary.LeakCanary.config.copy(retainedVisibleThreshold=1);retained=view;AppWatcher.objectWatcher.expectWeaklyReachable(view,"Detached Common Goods lab view")}
    fun repairRetention(){retained=null}
}
