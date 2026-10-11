package staff.android.commerce.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import staff.android.commerce.design.ProductPhoto
import staff.android.commerce.design.ShopIcon
import staff.android.commerce.design.ShopSymbol
import staff.android.commerce.design.formatPrice
import staff.android.commerce.model.CommerceSnapshot
import staff.android.commerce.model.OrderState
import staff.android.commerce.model.Product

/** Stateless shopping surface. The repository owns the saved set and accepted quantities. */
@Composable
fun SavedScreen(
    snapshot: CommerceSnapshot,
    onOpen: (String) -> Unit,
    onSave: (String) -> Unit,
    onAdd: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    val products = snapshot.products.filter { snapshot.isSaved(it.id) }
    val submitting = snapshot.orderState is OrderState.Submitting
    LazyColumn(
        modifier = Modifier.testTag("saved-screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved", style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
                Text(
                    if (products.isEmpty()) "Keep the things you like close." else
                        "${products.size} ${if (products.size == 1) "good find" else "good finds"}, all in one place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (products.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ProductPhoto("weekend", Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)))
                    Text("Make a little wish list.", style = MaterialTheme.typography.headlineSmall)
                    Text("Tap the heart on a product to save it here. Come back when you’re ready.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBrowse, enabled = !submitting, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("saved-browse")) {
                        Text("Explore the shop", modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        } else {
            items(products, key = Product::id) { product ->
                SavedProductRow(product, snapshot.quantityOf(product.id), submitting,
                    onOpen = { onOpen(product.id) },
                    onRemoveSaved = { onSave(product.id) },
                    onAdd = { onAdd(product.id) })
            }
            item {
                OutlinedButton(onClick = onBrowse, enabled = !submitting, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Keep exploring", modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SavedProductRow(
    product: Product,
    cartQuantity: Int,
    submitting: Boolean,
    onOpen: () -> Unit,
    onRemoveSaved: () -> Unit,
    onAdd: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.4f
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (largeText) {
            ProductPhoto(product.imageKey, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !submitting, role = Role.Button,
                    onClickLabel = "View ${product.name}", onClick = onOpen), product.name,
                contentScale = ContentScale.Fit)
            SavedProductDetails(product, cartQuantity, submitting, onOpen, onRemoveSaved, onAdd)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                ProductPhoto(product.imageKey, Modifier.size(112.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !submitting, role = Role.Button,
                        onClickLabel = "View ${product.name}", onClick = onOpen), product.name)
                Box(Modifier.weight(1f)) {
                    SavedProductDetails(product, cartQuantity, submitting, onOpen, onRemoveSaved, onAdd)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SavedProductDetails(
    product: Product,
    cartQuantity: Int,
    submitting: Boolean,
    onOpen: () -> Unit,
    onRemoveSaved: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).clickable(enabled = !submitting, role = Role.Button,
                onClickLabel = "View ${product.name}", onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(product.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemoveSaved, enabled = !submitting,
                modifier = Modifier.size(48.dp).testTag("saved-remove-${product.id}")
                    .semantics { contentDescription = "Remove ${product.name} from saved" }) {
                ShopIcon(ShopSymbol.HeartFilled, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(formatPrice(product.priceMinor), style = MaterialTheme.typography.titleMedium)
        if (cartQuantity > 0) {
            Text("$cartQuantity in your cart", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onAdd, enabled = !submitting, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("saved-add-${product.id}")
                .semantics { contentDescription = "Add ${product.name} to cart" }) {
            Text("Add to cart")
        }
    }
}
