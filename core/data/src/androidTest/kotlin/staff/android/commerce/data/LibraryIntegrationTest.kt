package staff.android.commerce.data

import androidx.paging.*
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import staff.android.commerce.model.*

/** Device boundary test: real Room3 generated DAO + bundled SQLite, not a mock database. */
@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryIntegrationTest {
    private fun product(id:String="bag")=WireProduct(id,"Everyday bag","Carry less","Canvas bag","Carry",249900,"bag-v1",6)
    private val state=PagingState<Int,ProductRow>(emptyList(),null,PagingConfig(2),0)
    private fun database():CommerceDatabase {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-${java.util.UUID.randomUUID()}.db"
        return commerceDatabase(context,name)
    }
    @Test fun rowMembershipAndCursorCommitTogetherAndFailedAppendRetainsThem()= runBlocking {
        val db=database()
        try {
            var fail=false
            val api=object:ShopApi {
                override suspend fun catalog(query:String,category:String,cursor:String?):CatalogBatch {
                    if(fail)throw java.io.IOException("Controlled outage")
                    return CatalogBatch(listOf(product()),"next","s1")
                }
                override suspend fun order(requestId:String,command:OrderCommand)=error("not used")
            }
            val mediator=CatalogMediator(db,api,CatalogQuery())
            assertTrue(mediator.load(LoadType.REFRESH,state) is RemoteMediator.MediatorResult.Success)
            val before=db.shop().remoteKey(CatalogQuery().key)
            fail=true
            assertTrue(mediator.load(LoadType.APPEND,state) is RemoteMediator.MediatorResult.Error)
            assertEquals(before,db.shop().remoteKey(CatalogQuery().key))
            assertEquals(listOf("bag"),db.shop().memberIds(CatalogQuery().key))
            assertEquals(emptyList<String>(),db.shop().memberIds(CatalogQuery("tea").key))
            // Failure after deleting membership must roll back both rows and the continuation.
            try {db.withWriteTransaction {
                db.shop().clearMembership(CatalogQuery().key)
                db.shop().putProducts(listOf(product().copy(priceMinor=1).row()))
                db.shop().putKey(RemoteKey(CatalogQuery().key,null,"uncommitted",0,99))
                error("Interrupted transaction")
            }}
            catch(_:IllegalStateException){}
            assertEquals(listOf("bag"),db.shop().memberIds(CatalogQuery().key))
            assertEquals(before,db.shop().remoteKey(CatalogQuery().key))
            assertEquals(249900L,db.shop().product("bag")!!.priceMinor)
        } finally {db.close()}
    }
    @Test fun sourceInvalidatesAfterSuccessfulTransaction()=runBlocking {
        val db=database()
        try {
            val query=CatalogQuery();val source=db.shop().paging(query.key)
            source.load(PagingSource.LoadParams.Refresh(null,2,false))
            db.withWriteTransaction {
                db.shop().putProducts(listOf(product().row()))
                db.shop().putMembership(listOf(Membership(query.key,"bag",0)))
                db.shop().putKey(RemoteKey(query.key,null,"s1",1,1))
            }
            withTimeout(5000){while(!source.invalid)delay(10)}
            val fresh=db.shop().paging(query.key).load(PagingSource.LoadParams.Refresh(null,2,false)) as PagingSource.LoadResult.Page
            assertEquals("bag",fresh.data.single().id)
        } finally {db.close()}
    }
    @Test fun durableOrderReusesIdentityAfterLostReplyAndReopen()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName="reopen-${java.util.UUID.randomUUID()}.db"
        var db=commerceDatabase(context,databaseName);var calls=0;val accepted=mutableMapOf<String,OrderCommand>()
        val api=object:ShopApi {
            override suspend fun catalog(query:String,category:String,cursor:String?)=CatalogBatch(listOf(product()),null,"s1")
            override suspend fun order(requestId:String,command:OrderCommand):OrderReply {
                accepted[requestId]?.let{assertEquals(it,command)}?:run{accepted[requestId]=command}
                calls++;if(calls==1)throw java.io.IOException("Server accepted; acknowledgement lost")
                return OrderReply(requestId,"CG-00001")
            }
        }
        val firstScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val secondScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val repo=RoomCommerceRepository.open(InstrumentationRegistry.getInstrumentation().targetContext,db,api,firstScope)
            assertEquals(CommandResult.Accepted,repo.addToCart("bag"))
            assertTrue(repo.submitOrder() is CommandResult.Rejected)
            val pending=db.shop().readPending()!!
            assertTrue(repo.setQuantity("bag",2) is CommandResult.Rejected)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            db.close();db=commerceDatabase(context,databaseName)
            val reopened=RoomCommerceRepository.open(InstrumentationRegistry.getInstrumentation().targetContext,db,api,secondScope)
            assertEquals(CommandResult.Accepted,reopened.reconcile())
            assertEquals(1,accepted.size);assertEquals(pending.requestId,accepted.keys.single())
            assertNull(db.shop().readPending())
            val placed=withTimeout(5000){reopened.state.first{it.orderState is OrderState.Placed}}
            assertEquals(1,(placed.orderState as OrderState.Placed).receipt.itemCount)
            assertEquals(0,placed.cartItemCount)
        } finally {firstScope.cancel();secondScope.cancel();db.close()}
    }
}
