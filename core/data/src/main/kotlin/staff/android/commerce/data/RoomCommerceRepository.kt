package staff.android.commerce.data

import android.content.Context
import androidx.room3.withWriteTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import staff.android.commerce.model.*

@Serializable private data class ReceiptLine(val product:WireProduct,val quantity:Int)
@Serializable private data class StoredReceipt(val id:String,val lines:List<ReceiptLine>)
private fun Product.wire()=WireProduct(id,name,subtitle,description,category,priceMinor,imageKey,maxQuantity,badge)

/** Durable local acceptance and a separate idempotent delivery obligation. */
class RoomCommerceRepository private constructor(private val db:CommerceDatabase,private val api:ShopApi,
    scope:CoroutineScope,initial:CommerceSnapshot):CommerceRepository {
    private val dao=db.shop()
    private val delivery=Mutex()
    private val sending=MutableStateFlow(false)
    override val state=combine(dao.products(),dao.shoppingView(),sending){products,view,busy->
        snapshot(products,view?.shopping?:ShoppingRow(),view?.pendingId!=null,busy)
    }.stateIn(scope,SharingStarted.Eagerly,initial)

    override suspend fun toggleSaved(productId:String):CommandResult=change { row ->
        if(dao.product(productId)==null)return@change null
        val ids=shopJson.decodeFromString<Set<String>>(row.saved).toMutableSet()
        if(!ids.add(productId))ids.remove(productId)
        row.copy(saved=shopJson.encodeToString(ids),revision=row.revision+1)
    }
    override suspend fun addToCart(productId:String)=quantity(productId,null)
    override suspend fun setQuantity(productId:String,quantity:Int)=quantity(productId,quantity)
    override suspend fun removeFromCart(productId:String)=quantity(productId,0)
    private suspend fun quantity(id:String,wanted:Int?):CommandResult {
        var reason="This item is no longer available."
        return change {
            if(dao.readPending()!=null){reason="Your demo order is awaiting confirmation. Retry it before changing the cart.";return@change null}
            val product=dao.product(id)?:return@change null
            val quantities=shopJson.decodeFromString<Map<String,Int>>(it.quantities).toMutableMap()
            val count=wanted?:(quantities[id]?:0)+1
            if(count !in 0..product.maxQuantity){reason="You can add up to ${product.maxQuantity} of ${product.name}.";return@change null}
            if(count==0)quantities.remove(id) else quantities[id]=count
            it.copy(quantities=shopJson.encodeToString(quantities),receipt=null,revision=it.revision+1)
        }.let { if(it is CommandResult.Rejected)CommandResult.Rejected(reason) else it }
    }
    private suspend fun change(block:suspend (ShoppingRow)->ShoppingRow?):CommandResult = db.withWriteTransaction {
        val next=block(dao.readShopping()?:ShoppingRow())?:return@withWriteTransaction CommandResult.Rejected("Item unavailable")
        dao.putShopping(next);CommandResult.Accepted
    }
    override suspend fun submitOrder():CommandResult {
        var rejection="Add something to your cart first."
        val accepted=db.withWriteTransaction {
            if(dao.readPending()!=null)return@withWriteTransaction true
            val quantities=shopJson.decodeFromString<Map<String,Int>>((dao.readShopping()?:ShoppingRow()).quantities)
            if(quantities.isEmpty())return@withWriteTransaction false
            val lines=quantities.map{(id,quantity)->
                val row=dao.product(id)
                if(row==null || quantity !in 1..row.maxQuantity) {
                    rejection="Availability changed. Review the quantities in your cart."
                    return@withWriteTransaction false
                }
                OrderLineWire(id,quantity,row.priceMinor)
            }
            val command=OrderCommand(UUID.randomUUID().toString(),lines)
            dao.enqueue(OrderOutbox(command.requestId,shopJson.encodeToString(command)))
            true
        }
        if(!accepted)return CommandResult.Rejected(rejection)
        return reconcile()
    }
    /** The same persisted identifier and payload survive process death and a lost acknowledgement. */
    suspend fun reconcile():CommandResult=delivery.withLock {
        val pending=dao.readPending()?:return@withLock CommandResult.Accepted
        sending.value=true
        try {
            val command=shopJson.decodeFromString<OrderCommand>(pending.payload)
            val reply=api.order(command.requestId,command)
            require(reply.requestId==command.requestId && Regex("CG-[0-9]{5,10}").matches(reply.orderId))
            db.withWriteTransaction {
                if(dao.readPending()!=pending)return@withWriteTransaction
                val lines=command.lines.map { line ->
                    val product=checkNotNull(dao.product(line.id)).product().copy(priceMinor=line.priceMinor)
                    ReceiptLine(product.wire(),line.quantity)
                }
                val before=dao.readShopping()?:ShoppingRow()
                dao.putShopping(before.copy(quantities="{}",receipt=shopJson.encodeToString(StoredReceipt(reply.orderId,lines)),revision=before.revision+1))
                dao.acknowledge(pending.requestId)
            }
            CommandResult.Accepted
        } catch(cancelled:CancellationException){throw cancelled}
          catch(rejected:retrofit2.HttpException) {
              if(rejected.code() in setOf(400,409)) {
                  db.withWriteTransaction {if(dao.readPending()==pending)dao.acknowledge(pending.requestId)}
                  CommandResult.Rejected("The server rejected this order before acceptance. Review prices and quantities, then try again.")
              } else CommandResult.Rejected("Order saved here. Retry when the demo server is available.")
          }
          catch(failure:Exception){CommandResult.Rejected("Order saved on this device. Connect to the demo server and retry to confirm it.")}
        finally {sending.value=false}
    }
    override suspend fun dismissOrder()=change{it.copy(receipt=null,revision=it.revision+1)}

    companion object {
        suspend fun open(context:Context,db:CommerceDatabase,api:ShopApi,scope:CoroutineScope):RoomCommerceRepository {
            // One-time import preserves the previous standalone local slice's accepted shopping.
            val old=if(db.shop().readShopping()==null)AtomicJsonCommercePersistence(File(context.filesDir,"shopping-state-v1.json")).read() else null
            db.withWriteTransaction {
                if(db.shop().readShopping()==null) {
                    db.shop().putProducts(seedCatalog().map{it.copy(imageKey="${it.imageKey}-v1").wire().row()})
                    val receipt=old?.receipt?.let{StoredReceipt(it.id,it.lines.map{line->ReceiptLine(line.product.wire(),line.quantity)})}
                    db.shop().putShopping(ShoppingRow(saved=shopJson.encodeToString(old?.savedIds?:emptySet<String>()),
                        quantities=shopJson.encodeToString(old?.quantities?:emptyMap<String,Int>()),revision=old?.revision?:0,
                        receipt=receipt?.let{shopJson.encodeToString(it)}))
                }
            }
            val initial=snapshot(db.shop().products().first(),db.shop().readShopping()!!,db.shop().readPending()!=null,false)
            return RoomCommerceRepository(db,api,scope,initial)
        }
        private fun snapshot(products:List<ProductRow>,row:ShoppingRow,pending:Boolean,busy:Boolean):CommerceSnapshot {
            val catalog=products.map{it.product()};val byId=catalog.associateBy{it.id}
            val receipt=row.receipt?.let{shopJson.decodeFromString<StoredReceipt>(it)}
            val lines=shopJson.decodeFromString<Map<String,Int>>(row.quantities).mapNotNull{(id,count)->byId[id]?.let{CartLine(it,count)}}
            return CommerceSnapshot(catalog,shopJson.decodeFromString(row.saved),lines,row.revision,
                if(busy)OrderState.Submitting else receipt?.let{OrderState.Placed(OrderReceipt(it.id,it.lines.map{line->CartLine(line.product.product(),line.quantity)}))}?:OrderState.Idle,pending)
        }
    }
}
