package staff.android.commerce.model

import kotlinx.coroutines.flow.StateFlow

/** INR values are integer paise. No floating-point rounding enters cart arithmetic. */
data class Product(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val category: String,
    val priceMinor: Long,
    val imageKey: String,
    val maxQuantity: Int,
    val badge: String? = null,
)

data class CartLine(val product: Product, val quantity: Int) {
    val lineTotalMinor: Long get() = product.priceMinor * quantity
}

data class OrderReceipt(
    val id: String,
    val lines: List<CartLine>,
) {
    val totalMinor: Long get() = lines.sumOf(CartLine::lineTotalMinor)
    val itemCount: Int get() = lines.sumOf(CartLine::quantity)
}

sealed interface OrderState {
    data object Idle : OrderState
    data object Submitting : OrderState
    data class Placed(val receipt: OrderReceipt) : OrderState
}

/** Repository-owned snapshots never mutate after publication. Quantities are accepted values. */
data class CommerceSnapshot(
    val products: List<Product>,
    val savedIds: Set<String>,
    val cartLines: List<CartLine>,
    val revision: Long,
    val orderState: OrderState = OrderState.Idle,
    val pendingOrder: Boolean = false,
) {
    val cartItemCount: Int get() = cartLines.sumOf(CartLine::quantity)
    val subtotalMinor: Long get() = cartLines.sumOf(CartLine::lineTotalMinor)
    fun isSaved(productId: String): Boolean = productId in savedIds
    fun quantityOf(productId: String): Int = cartLines.firstOrNull { it.product.id == productId }?.quantity ?: 0
}

sealed interface CommandResult {
    data object Accepted : CommandResult
    data class Rejected(val message: String) : CommandResult
}

/**
 * A single app-scoped repository owns accepted values. UI quantity drafts stay in the UI until
 * setQuantity confirms them. Commands are serialized; successful persistence precedes publication.
 * IO failures propagate to the caller and leave the accepted snapshot unchanged.
 */
interface CommerceRepository {
    val state: StateFlow<CommerceSnapshot>
    suspend fun toggleSaved(productId: String): CommandResult
    suspend fun addToCart(productId: String): CommandResult
    suspend fun setQuantity(productId: String, quantity: Int): CommandResult
    suspend fun removeFromCart(productId: String): CommandResult
    /** Simulated order only: the integrated checkpoint uses a local HTTP server; no payment or shipment. */
    suspend fun submitOrder(): CommandResult
    suspend fun dismissOrder(): CommandResult
}
