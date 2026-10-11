package staff.android.commerce.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import staff.android.commerce.model.CartLine
import staff.android.commerce.model.CommerceRepository
import staff.android.commerce.model.OrderReceipt
import staff.android.commerce.model.Product

/** One instance per application process; call from the composition root, then inject the interface. */
suspend fun openCommerceRepository(context: Context): CommerceRepository = LocalCommerceRepository.open(
    AtomicJsonCommercePersistence(File(context.applicationContext.filesDir, "shopping-state-v1.json")),
)

/**
 * A deliberately small, versioned local store for this six-product slice. AtomicFile supplies
 * interrupted-write recovery; this is not a replacement for Room when the data/query model grows.
 */
internal class AtomicJsonCommercePersistence(private val file: File) : CommercePersistence {
    private val atomicFile = AtomicFile(file)

    override suspend fun read(): StoredCommerce? = withContext(Dispatchers.IO) {
        if (!file.exists() && !File(file.path + ".bak").exists()) return@withContext null
        try {
            decode(JSONObject(atomicFile.readFully().toString(Charsets.UTF_8)))
        } catch (error: JSONException) {
            throw IOException("Saved shopping data could not be read; it has not been overwritten.", error)
        }
    }

    override suspend fun write(value: StoredCommerce): Unit = withContext(Dispatchers.IO) {
        val bytes = encode(value).toString().toByteArray(Charsets.UTF_8)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }
}

private fun encode(value: StoredCommerce): JSONObject = JSONObject().apply {
    put("schemaVersion", 1)
    put("savedIds", JSONArray(value.savedIds.toList()))
    put("quantities", JSONArray().apply {
        value.quantities.forEach { (id, quantity) ->
            put(JSONObject().put("id", id).put("quantity", quantity))
        }
    })
    put("revision", value.revision)
    put("nextOrderNumber", value.nextOrderNumber)
    value.receipt?.let { receipt ->
        put("receipt", JSONObject().put("id", receipt.id).put("lines", JSONArray().apply {
            receipt.lines.forEach { line ->
                put(JSONObject().put("quantity", line.quantity).put("product", JSONObject().apply {
                    with(line.product) {
                        put("id", id); put("name", name); put("subtitle", subtitle)
                        put("description", description); put("category", category)
                        put("priceMinor", priceMinor); put("imageKey", imageKey)
                        put("maxQuantity", maxQuantity); badge?.let { put("badge", it) }
                    }
                }))
            }
        }))
    }
}

private fun decode(json: JSONObject): StoredCommerce {
    if (json.getInt("schemaVersion") != 1) throw IOException("This saved shopping format is not supported.")
    val saved = json.getJSONArray("savedIds")
    val quantities = json.getJSONArray("quantities")
    val quantityMap = linkedMapOf<String, Int>()
    repeat(quantities.length()) { index ->
        val entry = quantities.getJSONObject(index)
        val id = entry.getString("id")
        if (id in quantityMap) throw IOException("Saved shopping data has a duplicate item.")
        quantityMap[id] = entry.getInt("quantity")
    }
    val receipt = json.optJSONObject("receipt")?.let { order ->
        val lines = order.getJSONArray("lines")
        OrderReceipt(order.getString("id"), List(lines.length()) { index ->
            val line = lines.getJSONObject(index)
            val product = line.getJSONObject("product")
            CartLine(Product(
                id = product.getString("id"),
                name = product.getString("name"),
                subtitle = product.getString("subtitle"),
                description = product.getString("description"),
                category = product.getString("category"),
                priceMinor = product.getLong("priceMinor"),
                imageKey = product.getString("imageKey"),
                maxQuantity = product.getInt("maxQuantity"),
                badge = product.optString("badge").takeIf(String::isNotEmpty),
            ), line.getInt("quantity"))
        })
    }
    return StoredCommerce(
        savedIds = List(saved.length()) { saved.getString(it) }.toSet(),
        quantities = quantityMap,
        revision = json.getLong("revision"),
        nextOrderNumber = json.getLong("nextOrderNumber"),
        receipt = receipt,
    )
}
