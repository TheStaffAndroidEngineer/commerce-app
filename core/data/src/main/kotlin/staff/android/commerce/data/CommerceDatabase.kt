package staff.android.commerce.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room3.*
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.Flow
import staff.android.commerce.model.Product

@Entity(tableName="product") data class ProductRow(@PrimaryKey val id:String,val name:String,val subtitle:String,
    val description:String,val category:String,val priceMinor:Long,val imageKey:String,val maxQuantity:Int,val badge:String?) {
    fun product()=Product(id,name,subtitle,description,category,priceMinor,imageKey,maxQuantity,badge)
}
@Entity(tableName="membership",primaryKeys=["representation","productId"],indices=[Index("productId")],foreignKeys=[ForeignKey(entity=ProductRow::class,parentColumns=["id"],childColumns=["productId"],onDelete=ForeignKey.RESTRICT)])
data class Membership(val representation:String,val productId:String,val position:Int)
@Entity(tableName="remote_key") data class RemoteKey(@PrimaryKey val representation:String,val nextCursor:String?,val snapshot:String,val count:Int,val generation:Long)
@Entity(tableName="shopping") data class ShoppingRow(@PrimaryKey val slot:Int=1,val saved:String="[]",val quantities:String="{}",val revision:Long=0,val receipt:String?=null)
@Entity(tableName="order_outbox") data class OrderOutbox(@PrimaryKey val requestId:String,val payload:String)

data class ShoppingView(@Embedded val shopping:ShoppingRow,val pendingId:String?,val pendingPayload:String?)

@Dao @DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface CommerceDao {
    @Query("SELECT p.* FROM product p JOIN membership m ON p.id=m.productId WHERE m.representation=:key ORDER BY m.position")
    fun paging(key:String):PagingSource<Int,ProductRow>
    @Query("SELECT * FROM product ORDER BY id") fun products():Flow<List<ProductRow>>
    @Query("SELECT * FROM product WHERE id=:id") suspend fun product(id:String):ProductRow?
    @Query("SELECT s.*,o.requestId AS pendingId,o.payload AS pendingPayload FROM shopping s LEFT JOIN order_outbox o ON 1=1 WHERE s.slot=1 LIMIT 1")
    fun shoppingView():Flow<ShoppingView?>
    @Query("SELECT * FROM shopping WHERE slot=1") fun shopping():Flow<ShoppingRow?>
    @Query("SELECT * FROM shopping WHERE slot=1") suspend fun readShopping():ShoppingRow?
    @Query("SELECT * FROM order_outbox LIMIT 1") fun pending():Flow<OrderOutbox?>
    @Query("SELECT * FROM order_outbox LIMIT 1") suspend fun readPending():OrderOutbox?
    @Query("SELECT * FROM remote_key WHERE representation=:key") suspend fun remoteKey(key:String):RemoteKey?
    @Query("SELECT productId FROM membership WHERE representation=:key ORDER BY position") suspend fun memberIds(key:String):List<String>
    @Query("DELETE FROM membership WHERE representation=:key") suspend fun clearMembership(key:String)
    @Query("DELETE FROM order_outbox WHERE requestId=:id") suspend fun acknowledge(id:String)
    @Upsert suspend fun putProducts(rows:List<ProductRow>)
    @Upsert suspend fun putMembership(rows:List<Membership>)
    @Upsert suspend fun putKey(key:RemoteKey)
    @Upsert suspend fun putShopping(row:ShoppingRow)
    @Insert suspend fun enqueue(row:OrderOutbox)
}
@Database(entities=[ProductRow::class,Membership::class,RemoteKey::class,ShoppingRow::class,OrderOutbox::class],version=1,exportSchema=true)
abstract class CommerceDatabase:RoomDatabase(){abstract fun shop():CommerceDao}
fun commerceDatabase(context:Context,name:String="common-goods-v2.db"):CommerceDatabase =
    Room.databaseBuilder<CommerceDatabase>(context.applicationContext,name).setDriver(BundledSQLiteDriver()).build()
