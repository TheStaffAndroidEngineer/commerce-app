package staff.android.commerce.model

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/** Public catalogue representation; saved/cart data is never refreshed by this query. */
data class CatalogQuery(val text: String = "", val category: String = "All") {
    init { require(text.length <= 100); require(category in setOf("All","Home","Carry","Tech","Pantry","Weekend")) }
    val key: String get() = "INR|$category|${text.trim().lowercase(java.util.Locale.ROOT)}"
}
interface PagedCatalog {
    fun pages(query: CatalogQuery): Flow<PagingData<Product>>
}
