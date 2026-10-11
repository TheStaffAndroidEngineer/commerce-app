package staff.android.commerce
import android.app.Application
import android.content.Context
import android.view.View
import staff.android.commerce.data.shopHttpClient
object DiagnosticTools {
    fun install(app:Application){}
    fun http(context:Context)=shopHttpClient()
    fun inspect(context:Context){}
    fun retainForLab(view:View){}
    fun repairRetention(){}
}
