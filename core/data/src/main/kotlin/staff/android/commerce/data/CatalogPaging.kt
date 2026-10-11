package staff.android.commerce.data

import androidx.paging.*
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import staff.android.commerce.model.*

/** Network-only first step; the integrated app uses the database-backed pager below. */
class NetworkCatalogSource(private val api:ShopApi,private val query:CatalogQuery):PagingSource<String,Product>() {
    override suspend fun load(params:LoadParams<String>):LoadResult<String,Product> = try {
        val page=api.catalog(query.text.trim(),query.category,params.key).validate()
        require(page.nextCursor!=params.key || params.key==null){"Cursor loop"}
        LoadResult.Page(page.items.map{it.product()},prevKey=null,nextKey=page.nextCursor)
    } catch(cancelled:CancellationException){throw cancelled}
      catch(failure:Exception){LoadResult.Error(failure)}
    // Opaque forward cursor: refresh begins at the first page.
    override fun getRefreshKey(state:PagingState<String,Product>):String?=null
}

@OptIn(ExperimentalPagingApi::class)
class CatalogMediator(private val db:CommerceDatabase,private val api:ShopApi,private val query:CatalogQuery):RemoteMediator<Int,ProductRow>() {
    override suspend fun load(loadType:LoadType,state:PagingState<Int,ProductRow>):MediatorResult {
        if(loadType==LoadType.PREPEND) return MediatorResult.Success(true)
        val dao=db.shop();val key=query.key
        try {
            val before=dao.remoteKey(key)
            if(loadType==LoadType.APPEND && before?.nextCursor==null) return MediatorResult.Success(true)
            val cursor=if(loadType==LoadType.REFRESH)null else before?.nextCursor
            val batch=api.catalog(query.text.trim(),query.category,cursor).validate()
            db.withWriteTransaction {
                val now=dao.remoteKey(key)
                // A later refresh may have committed while this network request was in flight.
                check(now==before){"Catalog generation changed; retry with current key"}
                val oldIds=if(loadType==LoadType.REFRESH)emptyList() else dao.memberIds(key)
                if(cursor!=null) {
                    check(batch.snapshot==before?.snapshot){"Snapshot changed; refresh required"}
                    check(batch.nextCursor!=cursor){"Cursor loop"}
                    check(batch.items.none{it.id in oldIds}){"Duplicate across pages"}
                }
                check(oldIds.size+batch.items.size<=1000){"Catalog bound exceeded"}
                if(loadType==LoadType.REFRESH) dao.clearMembership(key)
                dao.putProducts(batch.items.map{it.row()})
                dao.putMembership(batch.items.mapIndexed{i,p->Membership(key,p.id,oldIds.size+i)})
                dao.putKey(RemoteKey(key,batch.nextCursor,batch.snapshot,oldIds.size+batch.items.size,(before?.generation?:0)+1))
            }
            return MediatorResult.Success(batch.nextCursor==null)
        } catch(cancelled:CancellationException){throw cancelled}
          catch(failure:Exception){return MediatorResult.Error(failure)}
    }
}
class RoomCatalog(private val db:CommerceDatabase,private val api:ShopApi):PagedCatalog {
    @OptIn(ExperimentalPagingApi::class)
    // Keep Paging's default three-page refresh window. A single two-item page
    // can reproduce the same visible snapshot after an append retry, leaving
    // this small grid without another item-access hint for the new generation.
    override fun pages(query:CatalogQuery)=Pager(PagingConfig(pageSize=2,prefetchDistance=1,enablePlaceholders=false),
        remoteMediator=CatalogMediator(db,api,query),pagingSourceFactory={db.shop().paging(query.key)}).flow.map{data->data.map{it.product()}}
}
