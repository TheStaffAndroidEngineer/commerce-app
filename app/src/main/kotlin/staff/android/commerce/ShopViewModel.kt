package staff.android.commerce

import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import staff.android.commerce.model.*

@HiltViewModel class ShopViewModel @Inject constructor(private val holder: RepositoryHolder) : ViewModel() {
    private val query=MutableStateFlow(CatalogQuery())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val catalog=query.flatMapLatest{holder.catalog.pages(it)}.cachedIn(viewModelScope)
    fun search(text:String,category:String="All"){query.value=CatalogQuery(text.take(100),category)}
    private val reports=ReportPolicy(Reports)
    private val current = MutableStateFlow<CommerceSnapshot?>(null)
    val snapshot = current.asStateFlow()
    private val loadFailure = MutableStateFlow(false)
    val failed = loadFailure.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var repository: CommerceRepository? = null
    private var load: Job? = null
    init { retryLoad() }
    fun retryLoad() {
        if(load?.isActive == true) return
        loadFailure.value=false
        load=viewModelScope.launch {
            try {
                val owner=holder.get();repository=owner
                owner.state.collect { current.value=it }
            } catch(cancelled:CancellationException) { throw cancelled }
              catch(failure:Exception) { reports.failure("open_store",failure);loadFailure.value=true }
        }
    }
    private fun command(success: String? = null, operation:String="save_cart", block: suspend CommerceRepository.() -> CommandResult) {
        val owner=repository ?: return
        viewModelScope.launch {
            try {
                when(val result=owner.block()) {
                    CommandResult.Accepted -> if(success!=null) messages.send(success)
                    is CommandResult.Rejected -> messages.send(result.message)
                }
            } catch(cancelled:CancellationException) { throw cancelled }
              catch(failure:Exception) { reports.failure(operation,failure); messages.send("Your change wasn't saved. Please try again.") }
        }
    }
    fun save(id:String) = command("Saved list updated") {toggleSaved(id)}
    fun add(id:String) = command("Added to your cart") {addToCart(id)}
    fun quantity(id:String,quantity:Int) = command(if(quantity==0) "Removed from cart" else "Quantity updated") {setQuantity(id,quantity)}
    fun remove(id:String) = command("Removed from cart") {removeFromCart(id)}
    fun checkout() = command(operation="submit_order") {submitOrder()}
    fun dismissOrder() = command {dismissOrder()}
}
