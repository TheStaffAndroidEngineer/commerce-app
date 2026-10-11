package staff.android.commerce.data

import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import staff.android.commerce.model.CartLine
import staff.android.commerce.model.CommandResult
import staff.android.commerce.model.CommerceRepository
import staff.android.commerce.model.CommerceSnapshot
import staff.android.commerce.model.OrderReceipt
import staff.android.commerce.model.OrderState
import staff.android.commerce.model.Product

internal data class StoredCommerce(
    val savedIds: Set<String> = emptySet(),
    val quantities: Map<String, Int> = emptyMap(),
    val revision: Long = 0,
    val nextOrderNumber: Long = 1,
    val receipt: OrderReceipt? = null,
)

/** This seam supports JVM verification without Android files or a device. */
internal interface CommercePersistence {
    suspend fun read(): StoredCommerce?
    suspend fun write(value: StoredCommerce)
}

internal class LocalCommerceRepository private constructor(
    products: List<Product>,
    initial: StoredCommerce,
    private val persistence: CommercePersistence,
    private val simulateSubmission: suspend () -> Unit,
) : CommerceRepository {
    private val products = immutableList(products)
    private val byId = this.products.associateBy(Product::id)
    private val mutex = Mutex()
    private var stored = detach(initial)
    private val mutableState = MutableStateFlow(snapshot(stored))
    override val state: StateFlow<CommerceSnapshot> = mutableState.asStateFlow()

    override suspend fun toggleSaved(productId: String): CommandResult = mutex.withLock {
        if (productId !in byId) return@withLock unknownProduct()
        val saved = stored.savedIds.toMutableSet()
        if (!saved.add(productId)) saved.remove(productId)
        commit(stored.copy(savedIds = saved, revision = stored.revision + 1), mutableState.value.orderState)
        CommandResult.Accepted
    }

    override suspend fun addToCart(productId: String): CommandResult = mutex.withLock {
        changeQuantity(productId, (stored.quantities[productId] ?: 0) + 1)
    }

    override suspend fun setQuantity(productId: String, quantity: Int): CommandResult = mutex.withLock {
        changeQuantity(productId, quantity)
    }

    override suspend fun removeFromCart(productId: String): CommandResult = setQuantity(productId, 0)

    private suspend fun changeQuantity(productId: String, quantity: Int): CommandResult {
        val product = byId[productId] ?: return unknownProduct()
        if (mutableState.value.orderState == OrderState.Submitting) {
            return CommandResult.Rejected("Your demo order is being placed. Please wait a moment.")
        }
        if (quantity < 0) return CommandResult.Rejected("Quantity cannot be less than zero.")
        if (quantity > product.maxQuantity) {
            return CommandResult.Rejected("You can add up to ${product.maxQuantity} of ${product.name}.")
        }
        if ((stored.quantities[productId] ?: 0) == quantity) return CommandResult.Accepted
        val quantities = stored.quantities.toMutableMap()
        if (quantity == 0) quantities.remove(productId) else quantities[productId] = quantity
        commit(stored.copy(quantities = quantities, revision = stored.revision + 1, receipt = null))
        return CommandResult.Accepted
    }

    override suspend fun submitOrder(): CommandResult {
        val checkoutLines = mutex.withLock {
            if (mutableState.value.orderState == OrderState.Submitting) {
                return CommandResult.Rejected("Your demo order is already being placed.")
            }
            if (stored.quantities.isEmpty()) return CommandResult.Rejected("Add something to your bag first.")
            val lines = mutableState.value.cartLines
            mutableState.value = snapshot(stored, OrderState.Submitting)
            lines
        }
        try {
            // A deterministic local delay is not evidence of network, payment, or server behaviour.
            simulateSubmission()
            return mutex.withLock {
                val receipt = OrderReceipt("CG-${stored.nextOrderNumber.toString().padStart(5, '0')}", checkoutLines)
                commit(stored.copy(
                    quantities = emptyMap(),
                    revision = stored.revision + 1,
                    nextOrderNumber = stored.nextOrderNumber + 1,
                    receipt = receipt,
                ))
                CommandResult.Accepted
            }
        } finally {
            // Cancellation or an IO failure must not strand the UI in Submitting.
            withContext(NonCancellable) {
                mutex.withLock {
                    if (mutableState.value.orderState == OrderState.Submitting) {
                        mutableState.value = snapshot(stored)
                    }
                }
            }
        }
    }

    override suspend fun dismissOrder(): CommandResult = mutex.withLock {
        if (mutableState.value.orderState == OrderState.Submitting) {
            return@withLock CommandResult.Rejected("Your demo order is being placed. Please wait a moment.")
        }
        if (stored.receipt != null) commit(stored.copy(receipt = null, revision = stored.revision + 1))
        CommandResult.Accepted
    }

    private suspend fun commit(next: StoredCommerce, transientState: OrderState? = null) {
        // Once persistence begins, complete the small local commit and its matching publication
        // together. A cancelled caller cannot leave disk ahead of the in-memory accepted snapshot.
        withContext(NonCancellable) {
            val owned = detach(next)
            persistence.write(owned)
            stored = owned
            mutableState.value = snapshot(owned, transientState)
        }
    }

    private fun snapshot(value: StoredCommerce, orderState: OrderState? = null): CommerceSnapshot = CommerceSnapshot(
        products = products,
        savedIds = immutableSet(value.savedIds),
        cartLines = immutableList(products.mapNotNull { product ->
            value.quantities[product.id]?.let { CartLine(product, it) }
        }),
        revision = value.revision,
        orderState = orderState ?: value.receipt?.let(OrderState::Placed) ?: OrderState.Idle,
    )

    companion object {
        suspend fun open(
            persistence: CommercePersistence,
            products: List<Product> = seedCatalog(),
            simulateSubmission: suspend () -> Unit = { delay(900) },
        ): LocalCommerceRepository {
            require(products.isNotEmpty() && products.map(Product::id).toSet().size == products.size)
            require(products.all { it.priceMinor >= 0 && it.maxQuantity in 1..100 })
            val stored = persistence.read() ?: StoredCommerce()
            validate(stored, products)
            return LocalCommerceRepository(products, stored, persistence, simulateSubmission)
        }
    }
}

private fun validate(value: StoredCommerce, products: List<Product>) {
    val byId = products.associateBy(Product::id)
    val valid = value.revision >= 0 && value.nextOrderNumber > 0 &&
        value.savedIds.all { it in byId } && value.quantities.all { (id, quantity) ->
            val product = byId[id]
            product != null && quantity in 1..product.maxQuantity
        } && (value.receipt == null || (
            value.receipt.id.isNotBlank() && value.receipt.lines.isNotEmpty() &&
            value.receipt.lines.map { it.product.id }.toSet().size == value.receipt.lines.size &&
            value.receipt.lines.all { it.quantity in 1..100 && it.product.priceMinor >= 0 }
        ))
    if (!valid) throw IOException("Saved shopping data is invalid; it has not been overwritten.")
}

private fun detach(value: StoredCommerce): StoredCommerce = value.copy(
    savedIds = immutableSet(value.savedIds),
    quantities = Collections.unmodifiableMap(LinkedHashMap(value.quantities)),
    receipt = value.receipt?.copy(lines = immutableList(value.receipt.lines)),
)

private fun unknownProduct() = CommandResult.Rejected("This item is no longer available.")
private fun <T> immutableList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun <T> immutableSet(values: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
