package staff.android.commerce.catalog

import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import staff.android.commerce.design.*
import staff.android.commerce.model.*

@Composable fun ShopScreen(snapshot: CommerceSnapshot, onOpen: (String) -> Unit, onSave: (String) -> Unit, onSearch: () -> Unit, paged: LazyPagingItems<Product>? = null, onQuery: (String,String)->Unit = {_,_->}) {
    var category by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(category) { onQuery("",category) }
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val products = snapshot.products.filter { category == "All" || it.category == category || (category == "Weekend" && it.category in setOf("Carry", "Tech")) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (largeText) 220.dp else 152.dp), modifier = Modifier.fillMaxSize().testTag("catalog-grid"),
        contentPadding = PaddingValues(start=20.dp,end=20.dp,bottom=28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(onClick=onSearch, shape=RoundedCornerShape(14.dp), color=MaterialTheme.colorScheme.surfaceVariant,
                modifier=Modifier.fillMaxWidth().testTag("open-search")) {
                Row(Modifier.padding(horizontal=16.dp, vertical=17.dp),verticalAlignment=Alignment.CenterVertically) {
                    ShopIcon(ShopSymbol.Search,Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp));Text("Find your next good thing",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Considered goods. Everyday living.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                    val heroHeight = (maxWidth / 1.95f).coerceAtMost(300.dp)
                    if (largeText) {
                        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                            ProductPhoto("weekend", Modifier.fillMaxWidth().height(heroHeight))
                            Column(Modifier.padding(20.dp)) { WeekendCopy { category = "Weekend" } }
                        }
                    } else if (maxWidth >= 700.dp) {
                        Row(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(.45f).padding(24.dp)) { WeekendCopy { category = "Weekend" } }
                            ProductPhoto("weekend",Modifier.weight(.55f).fillMaxHeight())
                        }
                    } else {
                        ProductPhoto("weekend",Modifier.fillMaxWidth().height(heroHeight))
                        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.50f).padding(start=20.dp,end=4.dp)) {
                        Text("THE WEEKEND EDIT",fontSize=9.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold,color=Color(0xFF263542))
                        Spacer(Modifier.height(10.dp))
                        Text("Take the\nlong way.",style=MaterialTheme.typography.headlineMedium,color=Color(0xFF192327))
                        Spacer(Modifier.height(16.dp))
                        Surface(onClick={category="Weekend"},shape=RoundedCornerShape(50),color=Color(0xFF3156D3),contentColor=Color.White) {
                            Row(Modifier.padding(horizontal=13.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("Explore",style=MaterialTheme.typography.labelLarge);Spacer(Modifier.width(9.dp));ShopIcon(ShopSymbol.ArrowRight,Modifier.size(17.dp))
                            }
                        }
                    }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(if(category=="Weekend") listOf("Weekend","All","Home","Carry","Tech","Pantry") else listOf("All","Home","Carry","Tech","Pantry")) { value ->
                        FilterChip(selected=category==value,onClick={category=value},label={Text(value)},shape=RoundedCornerShape(24.dp),
                            colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.onSurface,selectedLabelColor=MaterialTheme.colorScheme.surface),
                            modifier=Modifier.testTag("category-${value.lowercase()}"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(category=="All") "Good things, picked well" else if(category=="Weekend") "The weekend edit" else "$category essentials",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    if (!largeText) { Spacer(Modifier.width(10.dp));Text(if(paged!=null) "${paged.itemCount} loaded" else "${products.size} goods",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (largeText) Text(if(paged!=null) "${paged.itemCount} loaded" else "${products.size} goods",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(paged==null) items(products, key={it.id}) { product ->
            ProductCard(product,snapshot.isSaved(product.id),{onOpen(product.id)},{onSave(product.id)})
        } else {
            item(span={GridItemSpan(maxLineSpan)}) { CatalogLoadStatus(paged,refresh=true) }
            items(count=paged.itemCount,key=paged.itemKey{it.id}) { index -> paged[index]?.let { product ->
                ProductCard(product,snapshot.isSaved(product.id),{onOpen(product.id)},{onSave(product.id)})
            } }
            item(span={GridItemSpan(maxLineSpan)}) { CatalogLoadStatus(paged,refresh=false) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text("A small collection. A little more considered.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun WeekendCopy(onExplore: () -> Unit) {
    Text("THE WEEKEND EDIT", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Text("Take the long way.", style=MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Button(onClick=onExplore) { Text("Explore");Spacer(Modifier.width(9.dp));ShopIcon(ShopSymbol.ArrowRight,Modifier.size(18.dp)) }
}

@Composable fun ProductCard(product: Product, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(1.05f).clip(RoundedCornerShape(14.dp))) {
            Box(Modifier.fillMaxSize().clickable(onClick=onOpen).testTag("product-${product.id}").semantics { contentDescription="View ${product.name}";role=Role.Button }) {
                ProductPhoto(product.imageKey,Modifier.fillMaxSize(),product.name)
            }
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.surface.copy(alpha=.94f),modifier=Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                IconAction(ShopSymbol.Heart,if(saved) "Unsave ${product.name}" else "Save ${product.name}",onSave,Modifier.testTag("save-${product.id}"),saved)
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(product.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.clickable(onClick=onOpen))
        Text(product.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        Text(formatPrice(product.priceMinor),style=MaterialTheme.typography.titleSmall)
    }
}

@Composable fun SearchScreen(snapshot: CommerceSnapshot, onOpen: (String) -> Unit, onSave: (String) -> Unit, paged: LazyPagingItems<Product>? = null, onQuery: (String,String)->Unit = {_,_->}) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {onQuery(query,"All")}
    val focus=LocalFocusManager.current
    val results=snapshot.products.filter { query.isBlank() || (it.name+" "+it.category+" "+it.subtitle).contains(query.trim(),ignoreCase=true) }
    val header: @Composable (Boolean)->Unit = { inGrid ->
        Column(Modifier.padding(horizontal=if(inGrid) 0.dp else 20.dp)) {
            Text("Find something good.",style=MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().testTag("search-field"),singleLine=true,
                placeholder={Text("Search tea, bags, home…",style=MaterialTheme.typography.bodyMedium)},
                leadingIcon={ShopIcon(ShopSymbol.Search)},trailingIcon={if(query.isNotEmpty()) IconAction(ShopSymbol.Close,"Clear search",{query=""})},
                shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}))
            Spacer(Modifier.height(18.dp))
            Text(if(query.isBlank()) "Explore the collection" else if(paged!=null) "Results for “${query.trim()}”" else "${results.size} ${if(results.size==1) "result" else "results"} for “${query.trim()}”",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
    val grid: @Composable (Boolean)->Unit = { scrollHeader ->
        LazyVerticalGrid(GridCells.Adaptive(if (LocalDensity.current.fontScale >= 1.4f) 220.dp else 152.dp),
            contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),
            horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(24.dp),
            modifier=Modifier.fillMaxSize().testTag("search-results")) {
            if(scrollHeader) item(key="search-header",span={GridItemSpan(maxLineSpan)}) {header(true)}
            if(paged==null && results.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    ShopIcon(ShopSymbol.Search,Modifier.size(40.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp));Text("No good match yet",style=MaterialTheme.typography.titleLarge)
                    Text("Try a product name or a category like Home or Tech.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                    TextButton(onClick={query=""}) {Text("Browse everything")}
                }
            } else if(paged==null) items(results,key={it.id}) { product ->
                ProductCard(product,snapshot.isSaved(product.id),{onOpen(product.id)},{onSave(product.id)})
            } else {
                item(span={GridItemSpan(maxLineSpan)}) {CatalogLoadStatus(paged,true)}
                items(count=paged.itemCount,key=paged.itemKey{it.id}){index->paged[index]?.let{product->ProductCard(product,snapshot.isSaved(product.id),{onOpen(product.id)},{onSave(product.id)})}}
                item(span={GridItemSpan(maxLineSpan)}) {CatalogLoadStatus(paged,false)}
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // In a short window, let the search header leave the viewport so a
        // product's photograph, name and price can be read together.
        if(maxHeight<360.dp) grid(true)
        else Column(Modifier.fillMaxSize()) {header(false);grid(false)}
    }
}

@Composable
fun ProductScreen(
    product: Product,
    saved: Boolean,
    quantityInCart: Int,
    onSave: () -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onCart: () -> Unit,
    feedback: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(ShopSymbol.ArrowBack, "Back to products", onBack, Modifier.testTag("product-back"))
            Text(product.category, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconAction(ShopSymbol.Heart, if (saved) "Unsave ${product.name}" else "Save ${product.name}",
                onSave, Modifier.testTag("detail-save-${product.id}"), saved)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val expanded = maxWidth >= 700.dp && LocalDensity.current.fontScale < 1.6f
            if (expanded) {
                Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    ProductPhoto(product.imageKey, Modifier.weight(1f).aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp)), product.name)
                    Column(Modifier.weight(1f)) { ProductDescription(product) }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    ProductPhoto(product.imageKey, Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .aspectRatio(1.06f).clip(RoundedCornerShape(18.dp)), product.name)
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Spacer(Modifier.height(24.dp))
                        ProductDescription(product)
                    }
                }
            }
        }
        // Feedback owns space above the action bar. It cannot intercept Add to cart.
        feedback()
        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    if (quantityInCart > 0) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("$quantityInCart in your cart", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).testTag("detail-quantity"))
                            TextButton(onClick = onCart) { Text("View cart") }
                        }
                    }
                    Button(onClick = onAdd, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("detail-add-${product.id}")) {
                        ShopIcon(ShopSymbol.Plus, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Add to cart", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDescription(product: Product) {
    product.badge?.let {
        Text(it.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Spacer(Modifier.height(7.dp))
    }
    Text(product.name, style = MaterialTheme.typography.headlineLarge)
    Text(product.subtitle, style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
    Text(formatPrice(product.priceMinor), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp))
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(22.dp))
    Text("A little more about it", style = MaterialTheme.typography.titleMedium)
    Text(product.description, style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("Available · Up to ${product.maxQuantity} per order", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun CatalogLoadStatus(paged:LazyPagingItems<Product>,refresh:Boolean) {
    val state=if(refresh)paged.loadState.refresh else paged.loadState.append
    when(state) {
        is LoadState.Loading -> if(!refresh || paged.itemCount==0) Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)}
        is LoadState.Error -> Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(refresh && paged.itemCount>0) "Showing saved products. Couldn’t refresh." else if(refresh) "The collection is unavailable. Connect to the demo server and retry." else "Couldn’t load more products.",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={paged.retry()},modifier=Modifier.testTag(if(refresh)"catalog-retry" else "append-retry")){Text("Retry")}
        }
        is LoadState.NotLoading -> if(refresh && paged.itemCount==0) Text("No goods found. Try another search.",style=MaterialTheme.typography.bodyLarge)
    }
}
