package staff.android.commerce.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import staff.android.commerce.model.Product
import java.io.IOException
import java.util.concurrent.TimeUnit

internal val shopJson = Json { ignoreUnknownKeys = true }
@Serializable data class WireProduct(val id:String,val name:String,val subtitle:String,val description:String,
    val category:String,val priceMinor:Long,val imageKey:String,val maxQuantity:Int,val badge:String?=null) {
    fun product() = Product(id,name,subtitle,description,category,priceMinor,imageKey,maxQuantity,badge)
    fun row() = ProductRow(id,name,subtitle,description,category,priceMinor,imageKey,maxQuantity,badge)
}
@Serializable data class CatalogBatch(val items:List<WireProduct>,val nextCursor:String?,val snapshot:String)
@Serializable data class OrderLineWire(val id:String,val quantity:Int,val priceMinor:Long)
@Serializable data class OrderCommand(val requestId:String,val lines:List<OrderLineWire>)
@Serializable data class OrderReply(val requestId:String,val orderId:String)
interface ShopApi {
    @GET("catalog") suspend fun catalog(@Query("q") query:String,@Query("category") category:String,
        @Query("cursor") cursor:String?):CatalogBatch
    @POST("orders") suspend fun order(@Header("Idempotency-Key") requestId:String,@Body command:OrderCommand):OrderReply
}
fun shopHttpClient():OkHttpClient = OkHttpClient.Builder().connectTimeout(2,TimeUnit.SECONDS)
    .readTimeout(3,TimeUnit.SECONDS).callTimeout(5,TimeUnit.SECONDS).retryOnConnectionFailure(false)
    .followRedirects(false).followSslRedirects(false).build()
fun shopApi(baseUrl:String,client:OkHttpClient=shopHttpClient()):ShopApi = Retrofit.Builder()
    .baseUrl(baseUrl).client(client.newBuilder().addInterceptor { chain ->
        val response=chain.proceed(chain.request())
        try {
            val body=response.body
            if(body.contentLength()>65536) throw IOException("Response exceeds 64KiB")
            val source=body.source();source.request(65537)
            if(source.buffer.size>65536) throw IOException("Response exceeds 64KiB")
            val bytes=source.readByteArray();body.close()
            if(response.isSuccessful) {
                if(body.contentType()?.let{it.type=="application" && it.subtype=="json"} != true) throw IOException("Expected JSON")
                bytes.decodeToString(throwOnInvalidSequence=true)
            }
            response.newBuilder().body(bytes.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
        } catch(error:Exception) {response.close();throw if(error is IOException) error else IOException("Invalid representation",error)}
    }.build()).addConverterFactory(shopJson.asConverterFactory("application/json".toMediaType())).build().create(ShopApi::class.java)

internal fun CatalogBatch.validate():CatalogBatch {
    require(items.size<=100 && snapshot.isNotBlank() && snapshot.length<=100)
    require(nextCursor==null || nextCursor.isNotBlank() && nextCursor.length<=200)
    require(items.map{it.id}.distinct().size==items.size)
    items.forEach { p ->
        require(Regex("[a-z0-9-]{1,64}").matches(p.id) && p.name.isNotBlank() && p.name.length<=200)
        require(p.priceMinor in 0..100_000_000L && p.maxQuantity in 1..100)
        require(p.category in setOf("Home","Carry","Tech","Pantry"))
        // Only fixture-owned versioned keys become URLs; no server-provided arbitrary URI is loaded.
        require(Regex("[a-z0-9-]{1,80}").matches(p.imageKey))
    }
    return this
}
