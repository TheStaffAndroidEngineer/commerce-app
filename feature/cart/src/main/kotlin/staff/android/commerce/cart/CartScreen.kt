package staff.android.commerce.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import staff.android.commerce.design.ProductPhoto
import staff.android.commerce.design.ShopIcon
import staff.android.commerce.design.ShopSymbol
import staff.android.commerce.design.formatPrice
import staff.android.commerce.model.CartLine
import staff.android.commerce.model.CommerceSnapshot
import staff.android.commerce.model.OrderReceipt
import staff.android.commerce.model.OrderState

/** Accepted values come from the snapshot. Editing never mutates a cart line directly. */
@Composable
fun CartScreen(
    snapshot: CommerceSnapshot,
    onOpen: (String) -> Unit,
    onSetQuantity: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
    onContinue: () -> Unit,
    onDismissOrder: () -> Unit,
    feedback: @Composable () -> Unit = {},
) {
    var editingProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewingOrder by rememberSaveable { mutableStateOf(false) }
    val submitting = snapshot.orderState is OrderState.Submitting
    val placed = snapshot.orderState as? OrderState.Placed
    LaunchedEffect(snapshot.cartLines, placed) {
        if (snapshot.cartLines.none { it.product.id == editingProductId }) editingProductId = null
        if (snapshot.cartLines.isEmpty() || placed != null) reviewingOrder = false
    }

    if (placed != null) {
        OrderReceiptScreen(placed.receipt) {
            onDismissOrder()
            onContinue()
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // In a short window or at large text, the footer can scroll without consuming the list.
        val footerMaxHeight = maxHeight * 0.45f
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("cart-screen"),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if(snapshot.pendingOrder) Text("Your demo order is saved here and awaiting confirmation. Retry checkout when the demo server is available.", style=MaterialTheme.typography.bodyMedium, modifier=Modifier.testTag("pending-order"))
                        Text("Your cart", style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(if (snapshot.cartLines.isEmpty()) "A little room for something good." else
                            "${snapshot.cartItemCount} ${if (snapshot.cartItemCount == 1) "item" else "items"}, picked by you.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (snapshot.cartLines.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            ProductPhoto("weekend", Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)))
                            Text("What will you find?", style = MaterialTheme.typography.headlineSmall)
                            Text("Explore the collection or bring a favorite over from Saved.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onContinue, enabled = !submitting, shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("cart-browse")) {
                                Text("Explore the shop", modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                } else {
                    items(snapshot.cartLines, key = { it.product.id }) { line ->
                        CartProductRow(line, submitting,
                            onOpen = { onOpen(line.product.id) },
                            onEdit = { editingProductId = line.product.id },
                            onRemove = { onRemove(line.product.id) })
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            OrderTotals(snapshot.subtotalMinor, snapshot.cartItemCount)
                            TextButton(onClick = onContinue, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                                Text("Continue shopping")
                            }
                        }
                    }
                }
            }
            feedback()
            if (snapshot.cartLines.isNotEmpty()) {
                CheckoutFooter(submitting, onReview = { reviewingOrder = true },
                    modifier = Modifier.fillMaxWidth().heightIn(max = footerMaxHeight))
            }
        }
    }

    val editingLine = snapshot.cartLines.firstOrNull { it.product.id == editingProductId }
    if (editingLine != null) {
        QuantityDialog(editingLine, submitting,
            onDismiss = { editingProductId = null },
            onConfirm = { quantity ->
                if (quantity == 0) onRemove(editingLine.product.id)
                else onSetQuantity(editingLine.product.id, quantity)
                editingProductId = null
            })
    }
    if (reviewingOrder && snapshot.cartLines.isNotEmpty()) {
        ReviewOrderDialog(snapshot, submitting,
            onDismiss = { reviewingOrder = false },
            onPlace = {
                reviewingOrder = false
                onCheckout()
            })
    }
}


@Composable
private fun CheckoutFooter(submitting: Boolean, onReview: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.testTag("cart-checkout-footer"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReview, enabled = !submitting, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("checkout")) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (submitting) "Placing demo order…" else "Review order",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center)
                }
                Text("A demo checkout. No payment or delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CartProductRow(
    line: CartLine,
    submitting: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val product = line.product
    val largeText = LocalDensity.current.fontScale >= 1.4f
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (largeText) {
            ProductPhoto(product.imageKey, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !submitting, role = Role.Button,
                    onClickLabel = "View ${product.name}", onClick = onOpen), product.name,
                contentScale = ContentScale.Fit)
            CartProductDetails(line, submitting, onOpen, onEdit, onRemove)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                ProductPhoto(product.imageKey, Modifier.size(112.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !submitting, role = Role.Button,
                        onClickLabel = "View ${product.name}", onClick = onOpen), product.name)
                Box(Modifier.weight(1f)) {
                    CartProductDetails(line, submitting, onOpen, onEdit, onRemove)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CartProductDetails(
    line: CartLine,
    submitting: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val product = line.product
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.clickable(enabled = !submitting, role = Role.Button,
            onClickLabel = "View ${product.name}", onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(product.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${formatPrice(product.priceMinor)} each", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onEdit, enabled = !submitting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f).testTag("cart-quantity-${product.id}")
                    .semantics {
                        contentDescription = "Edit quantity for ${product.name}"
                        stateDescription = "Quantity ${line.quantity}"
                    }) {
                Text("Qty ${line.quantity}")
            }
            IconButton(onClick = onRemove, enabled = !submitting,
                modifier = Modifier.testTag("cart-remove-${product.id}")
                    .semantics { contentDescription = "Remove ${product.name} from cart" }) {
                ShopIcon(ShopSymbol.Trash, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(formatPrice(line.lineTotalMinor), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("cart-line-total-${product.id}"))
    }
}

@Composable
private fun OrderTotals(totalMinor: Long, itemCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Order summary", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        SummaryLine("Subtotal · $itemCount ${if (itemCount == 1) "item" else "items"}", formatPrice(totalMinor))
        SummaryLine("Delivery", "Demo — no delivery")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text("Total", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(formatPrice(totalMinor), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("cart-total"))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun QuantityDialog(line: CartLine, submitting: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var draft by rememberSaveable(line.product.id) { mutableStateOf(line.quantity.toString()) }
    val parsed = draft.toIntOrNull()
    val valid = parsed != null && parsed in 0..line.product.maxQuantity
    val help = when {
        draft.isBlank() -> "Enter a quantity. An empty value does not remove the item."
        parsed == null -> "Enter a whole number."
        parsed == 0 -> "Confirm removes this item from your cart."
        !valid -> "Choose 1–${line.product.maxQuantity}, or enter 0 to remove this item."
        else -> "Choose 1–${line.product.maxQuantity}, or enter 0 to remove this item."
    }
    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // A bounded window keeps the actions above the IME while the body can scroll.
        Box(Modifier.fillMaxSize()
            .pointerInput(submitting, onDismiss) { detectTapGestures { if (!submitting) onDismiss() } }
            .systemBarsPadding().imePadding().padding(24.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { /* Taps on the sheet do not dismiss it. */ } },
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Change quantity", style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                        Text("Currently ${line.quantity} in your cart. The quantity changes only when you confirm.",
                            style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            enabled = !submitting,
                            label = { Text("Quantity") },
                            singleLine = true,
                            isError = !valid,
                            supportingText = { Text(help) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("quantity-draft"),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !submitting,
                            modifier = Modifier.testTag("quantity-cancel")) { Text("Cancel") }
                        TextButton(onClick = { if (valid && parsed != null) onConfirm(parsed) },
                            enabled = valid && !submitting, modifier = Modifier.testTag("quantity-confirm")) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewOrderDialog(
    snapshot: CommerceSnapshot,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onPlace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Review your order", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                snapshot.cartLines.forEach { line ->
                    SummaryLine("${line.quantity} × ${line.product.name}", formatPrice(line.lineTotalMinor))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Total ${formatPrice(snapshot.subtotalMinor)}", style = MaterialTheme.typography.titleLarge)
                Text("This is a local demo order. No payment is taken and nothing will be shipped.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onPlace, enabled = !submitting,
                modifier = Modifier.testTag("place-order")) { Text("Place demo order") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting,
                modifier = Modifier.testTag("review-order-cancel")) { Text("Back to cart") }
        },
    )
}

@Composable
private fun OrderReceiptScreen(receipt: OrderReceipt, onContinue: () -> Unit) {
    LazyColumn(
        modifier = Modifier.testTag("order-receipt"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        ShopIcon(ShopSymbol.Check, Modifier.size(32.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text("Demo order placed", style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
                Text("Your picks, together.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Order ${receipt.id}", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("order-id"))
            }
        }
        items(receipt.lines, key = { it.product.id }) { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductPhoto(line.product.imageKey, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(line.product.name, style = MaterialTheme.typography.titleMedium)
                    Text("Quantity ${line.quantity}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPrice(line.lineTotalMinor), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryLine("Demo order total", formatPrice(receipt.totalMinor))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                    Text("This is a local demonstration. No payment was taken and nothing will be shipped.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
                }
                Button(onClick = onContinue, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("order-continue")) {
                    Text("Continue shopping", modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}
