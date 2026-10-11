package staff.android.commerce
import android.content.Context
import timber.log.Timber
object Reports:ReportSink {
    const val configured=false
    fun enabled():Boolean=false
    fun initialize(context:Context){}
    fun consent(context:Context,granted:Boolean){}
    override fun record(operation:String,category:String){Timber.tag("CommonGoods").w("operation=%s category=%s",operation,category)}
}
