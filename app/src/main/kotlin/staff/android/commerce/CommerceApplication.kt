package staff.android.commerce

import android.app.Application
import android.content.Context
import okio.Path.Companion.toPath
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import staff.android.commerce.data.*
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp class CommerceApplication : Application(),SingletonImageLoader.Factory {
    override fun onCreate(){super.onCreate();DiagnosticTools.install(this);Reports.initialize(this)}
    override fun newImageLoader(context:Context):ImageLoader = ImageLoader.Builder(context)
        .components{add(OkHttpNetworkFetcherFactory(callFactory={shopHttpClient()}))}
        .memoryCache{MemoryCache.Builder().maxSizePercent(context,.15).build()}
        .diskCache{DiskCache.Builder().directory(context.cacheDir.resolve("public-product-images").absolutePath.toPath()).maxSizeBytes(64L*1024*1024).build()}
        .build()
}
@Singleton class RepositoryHolder @Inject constructor(@ApplicationContext private val context:Context) {
    private val lock=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val database by lazy{commerceDatabase(context)}
    val api by lazy{shopApi("http://10.0.2.2:8788/",DiagnosticTools.http(context))}
    val catalog by lazy{RoomCatalog(database,api)}
    private var loaded:RoomCommerceRepository?=null
    suspend fun get():RoomCommerceRepository=lock.withLock {
        loaded?:RoomCommerceRepository.open(context,database,api,scope).also{owner->loaded=owner;scope.launch{owner.reconcile()}}
    }
}
