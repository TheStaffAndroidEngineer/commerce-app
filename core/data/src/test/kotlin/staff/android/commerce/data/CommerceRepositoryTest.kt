package staff.android.commerce.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.commerce.model.CommandResult
import staff.android.commerce.model.OrderState

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommerceRepositoryTest {
    @Test fun `catalog has distinct offline images and exact minor-unit totals`() = runTest {
        val repository = LocalCommerceRepository.open(MemoryPersistence())
        val products = repository.state.value.products
        assertEquals(setOf("tea", "camera", "bag", "headphones", "mug", "lamp"), products.map { it.id }.toSet())
        assertEquals(products.size, products.map { it.imageKey }.toSet().size)
        repository.setQuantity("tea", 3)
        repository.addToCart("mug")
        assertEquals(4, repository.state.value.cartItemCount)
        assertEquals(229_600L, repository.state.value.subtotalMinor)
    }

    @Test fun `rejection preserves the exact accepted snapshot and durable quantity`() = runTest {
        val store = MemoryPersistence()
        val repository = LocalCommerceRepository.open(store)
        repository.setQuantity("tea", 4)
        val accepted = repository.state.value
        val writes = store.writes
        assertInstanceOf(CommandResult.Rejected::class.java, repository.addToCart("tea"))
        assertSame(accepted, repository.state.value)
        assertEquals(writes, store.writes)
        assertEquals(4, LocalCommerceRepository.open(store).state.value.quantityOf("tea"))
    }

    @Test fun `previously retained snapshots and nested lines remain unchanged`() = runTest {
        val repository = LocalCommerceRepository.open(MemoryPersistence())
        repository.addToCart("tea")
        val before = repository.state.value
        repository.addToCart("tea")
        repository.toggleSaved("tea")
        assertEquals(1, before.quantityOf("tea"))
        assertFalse(before.isSaved("tea"))
        assertEquals(2, repository.state.value.quantityOf("tea"))
        assertTrue(repository.state.value.isSaved("tea"))
        assertThrows(UnsupportedOperationException::class.java) {
            (before.cartLines as MutableList<*>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (repository.state.value.savedIds as MutableSet<*>).clear()
        }
    }

    @Test fun `only confirmed quantity reaches the repository and zero removes`() = runTest {
        val repository = LocalCommerceRepository.open(MemoryPersistence())
        repository.setQuantity("tea", 2)
        // An editor's draft is local. Cancel sends no command, preserving accepted state.
        val retained = repository.state.value
        var draft = repository.state.value.quantityOf("tea")
        draft += 1
        assertEquals(3, draft)
        assertSame(retained, repository.state.value)
        repository.setQuantity("tea", draft)
        assertEquals(3, repository.state.value.quantityOf("tea"))
        repository.setQuantity("tea", 0)
        assertTrue(repository.state.value.cartLines.isEmpty())
    }

    @Test fun `invalid ids and negative quantities do not persist or advance revision`() = runTest {
        val store = MemoryPersistence()
        val repository = LocalCommerceRepository.open(store)
        val start = repository.state.value
        assertInstanceOf(CommandResult.Rejected::class.java, repository.toggleSaved("missing"))
        assertInstanceOf(CommandResult.Rejected::class.java, repository.setQuantity("tea", -1))
        assertInstanceOf(CommandResult.Rejected::class.java, repository.addToCart("missing"))
        assertSame(start, repository.state.value)
        assertEquals(0, store.writes)
    }

    @Test fun `concurrent additions never lose an accepted increment or exceed stock`() = runTest {
        val repository = LocalCommerceRepository.open(MemoryPersistence())
        val results = List(40) { async(Dispatchers.Default) { repository.addToCart("tea") } }.awaitAll()
        assertEquals(4, results.count { it == CommandResult.Accepted })
        assertEquals(36, results.count { it is CommandResult.Rejected })
        assertEquals(4, repository.state.value.quantityOf("tea"))
        assertEquals(4L, repository.state.value.revision)
    }

    @Test fun `accepted cart and saved selections survive repository recreation`() = runTest {
        val store = MemoryPersistence()
        val first = LocalCommerceRepository.open(store)
        first.toggleSaved("bag")
        first.setQuantity("tea", 3)
        first.addToCart("camera")
        val restored = LocalCommerceRepository.open(store)
        assertEquals(first.state.value, restored.state.value)
    }

    @Test fun `disk failure leaves memory and existing durable state intact`() = runTest {
        val store = MemoryPersistence()
        val repository = LocalCommerceRepository.open(store)
        repository.addToCart("tea")
        val before = repository.state.value
        store.failNextWrite = true
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.addToCart("tea") }
        }
        assertSame(before, repository.state.value)
        assertEquals(1, LocalCommerceRepository.open(store).state.value.quantityOf("tea"))
    }

    @Test fun `submission freezes cart edits but preserves a concurrent saved change`() = runTest {
        val store = MemoryPersistence()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repository = LocalCommerceRepository.open(store, simulateSubmission = {
            entered.complete(Unit); finish.await()
        })
        repository.setQuantity("tea", 2)
        val checkout = async { repository.submitOrder() }
        entered.await()
        assertEquals(OrderState.Submitting, repository.state.value.orderState)
        assertInstanceOf(CommandResult.Rejected::class.java, repository.addToCart("tea"))
        assertInstanceOf(CommandResult.Rejected::class.java, repository.submitOrder())
        repository.toggleSaved("bag")
        finish.complete(Unit)
        assertEquals(CommandResult.Accepted, checkout.await())
        val placed = repository.state.value.orderState as OrderState.Placed
        assertEquals(99_800L, placed.receipt.totalMinor)
        assertEquals(2, placed.receipt.itemCount)
        assertTrue(repository.state.value.cartLines.isEmpty())
        assertTrue(repository.state.value.isSaved("bag"))
        assertEquals(repository.state.value, LocalCommerceRepository.open(store).state.value)
    }

    @Test fun `cancelled submission restores an editable cart and propagates cancellation`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val neverFinish = CompletableDeferred<Unit>()
        val repository = LocalCommerceRepository.open(MemoryPersistence(), simulateSubmission = {
            entered.complete(Unit); neverFinish.await()
        })
        repository.addToCart("tea")
        var returnedNormally = false
        val checkout = launch { repository.submitOrder(); returnedNormally = true }
        entered.await()
        checkout.cancelAndJoin()
        assertFalse(returnedNormally)
        assertEquals(OrderState.Idle, repository.state.value.orderState)
        assertEquals(1, repository.state.value.quantityOf("tea"))
        assertEquals(CommandResult.Accepted, repository.addToCart("tea"))
    }

    @Test fun `order persistence failure restores cart without a false receipt`() = runTest {
        val store = MemoryPersistence()
        val repository = LocalCommerceRepository.open(store, simulateSubmission = {})
        repository.addToCart("tea")
        store.failNextWrite = true
        try {
            repository.submitOrder()
            fail<Unit>("Expected failed persistence")
        } catch (_: IOException) { }
        assertEquals(OrderState.Idle, repository.state.value.orderState)
        assertEquals(1, repository.state.value.quantityOf("tea"))
        assertEquals(repository.state.value, LocalCommerceRepository.open(store).state.value)
    }

    @Test fun `local order delay is deterministic and receipt ids survive restart`() = runTest {
        val store = MemoryPersistence()
        val repository = LocalCommerceRepository.open(store)
        repository.addToCart("tea")
        val order = async { repository.submitOrder() }
        runCurrent()
        advanceTimeBy(899)
        assertFalse(order.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(CommandResult.Accepted, order.await())
        assertEquals("CG-00001", (repository.state.value.orderState as OrderState.Placed).receipt.id)
        repository.dismissOrder()
        val restored = LocalCommerceRepository.open(store, simulateSubmission = {})
        assertEquals(OrderState.Idle, restored.state.value.orderState)
        restored.addToCart("mug")
        restored.submitOrder()
        assertEquals("CG-00002", (restored.state.value.orderState as OrderState.Placed).receipt.id)
    }

    @Test fun `cancellation during a begun disk commit cannot split accepted memory from disk`() = runTest {
        val writing = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        var durable: StoredCommerce? = null
        val store = object : CommercePersistence {
            override suspend fun read() = durable
            override suspend fun write(value: StoredCommerce) {
                writing.complete(Unit)
                finishWrite.await()
                durable = value
            }
        }
        val repository = LocalCommerceRepository.open(store)
        val edit = launch { repository.addToCart("tea") }
        writing.await()
        edit.cancel()
        assertEquals(0, repository.state.value.quantityOf("tea"))
        finishWrite.complete(Unit)
        edit.join()
        assertEquals(1, repository.state.value.quantityOf("tea"))
        assertEquals(1, durable!!.quantities["tea"])
        assertEquals(repository.state.value, LocalCommerceRepository.open(store).state.value)
    }

    @Test fun `empty checkout makes no durable mutation`() = runTest {
        val store = MemoryPersistence()
        val repository = LocalCommerceRepository.open(store)
        assertInstanceOf(CommandResult.Rejected::class.java, repository.submitOrder())
        assertEquals(OrderState.Idle, repository.state.value.orderState)
        assertEquals(0, store.writes)
    }

    @Test fun `invalid stored quantities are reported and never silently reset`() = runTest {
        val store = MemoryPersistence(StoredCommerce(quantities = mapOf("tea" to 90)))
        try {
            LocalCommerceRepository.open(store)
            fail<Unit>("Expected invalid saved data")
        } catch (_: IOException) { }
        assertEquals(90, store.value!!.quantities["tea"])
        assertEquals(0, store.writes)
    }
}

private class MemoryPersistence(var value: StoredCommerce? = null) : CommercePersistence {
    var writes = 0
    var failNextWrite = false
    override suspend fun read(): StoredCommerce? = value
    override suspend fun write(value: StoredCommerce) {
        if (failNextWrite) {
            failNextWrite = false
            throw IOException("Controlled storage failure")
        }
        this.value = value
        writes++
    }
}
