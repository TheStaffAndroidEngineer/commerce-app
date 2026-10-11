package staff.android.commerce

import android.os.Bundle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import staff.android.commerce.catalog.*
import staff.android.commerce.cart.CartScreen
import staff.android.commerce.saved.SavedScreen
import staff.android.commerce.design.*

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private val model: ShopViewModel by viewModels()
    private var networkAllowed by mutableStateOf(false)
    private var continueOffline by mutableStateOf(false)
    private val localNetwork = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        networkAllowed=granted
        continueOffline=!granted
    }
    private fun requestLocalNetwork(){if(android.os.Build.VERSION.SDK_INT>=37)localNetwork.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK)}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        networkAllowed=android.os.Build.VERSION.SDK_INT<37 || checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK)==android.content.pm.PackageManager.PERMISSION_GRANTED
        continueOffline=savedInstanceState?.getBoolean("continueOffline")?:false
        setContent { CommonTheme {
            if(networkAllowed || continueOffline) CommerceApp(model,::requestLocalNetwork)
            else Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.Center) {
                    Text("Connect to Common Goods",style=MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(20.dp))
                    Text("This learning app gets its catalogue and photographs from a demo server on your computer. Android asks for nearby-device access to reach that local network.",style=MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick=::requestLocalNetwork){Text("Connect to the demo server")}
                    TextButton(onClick={continueOffline=true}){Text("Continue with saved shopping")}
                }
            }
        } }
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("continueOffline",continueOffline);super.onSaveInstanceState(outState)}
}

@Composable private fun CommerceApp(model:ShopViewModel,onEnableNetwork:()->Unit) {
    val context=LocalContext.current
    val paged=model.catalog.collectAsLazyPagingItems()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val failed by model.failed.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Shop") }
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    val snackbars=remember { SnackbarHostState() }
    val routes=rememberSaveableStateHolder()
    LaunchedEffect(model) { model.events.collectLatest { message -> snackbars.currentSnackbarData?.dismiss();snackbars.showSnackbar(message,duration=SnackbarDuration.Short) } }
    BackHandler(productId!=null) { productId=null }
    BackHandler(productId==null && tab!="Shop") { tab="Shop" }
    val state=snapshot
    Surface(Modifier.fillMaxSize()) {
        if(state==null) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
                Column(Modifier.padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("Common Goods",style=MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    if(failed) {Text("We couldn't open your saved shopping.",style=MaterialTheme.typography.bodyLarge);TextButton(onClick=model::retryLoad){Text("Try again")}}
                    else CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp)
                }
            }
        } else Scaffold(
            modifier=Modifier.fillMaxSize(),
            topBar={ if(productId==null) ShopTopBar(onAbout={about=true}) },
            bottomBar={ if(productId==null) {
                Column {
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                        listOf("Shop" to ShopSymbol.Home,"Search" to ShopSymbol.Search,"Saved" to ShopSymbol.Heart,"Cart" to ShopSymbol.Bag).forEach { (name,symbol) ->
                            NavigationBarItem(selected=tab==name,onClick={tab=name},modifier=Modifier.testTag("nav-${name.lowercase()}"),
                                label={Text(name,style=MaterialTheme.typography.labelSmall)},
                                icon={BadgedBox(badge={if(name=="Cart" && state.cartItemCount>0) Badge {Text(state.cartItemCount.toString())}}) {ShopIcon(symbol,Modifier.size(22.dp))}},
                                colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer,selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            } },
            snackbarHost={if(productId==null && tab!="Cart") SnackbarHost(snackbars)},
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),contentAlignment=Alignment.TopCenter) {
                Box(Modifier.widthIn(max=1100.dp).fillMaxSize()) {
                    val product=state.products.firstOrNull {it.id==productId}
                    if(product!=null) ProductScreen(product,state.isSaved(product.id),state.quantityOf(product.id),{model.save(product.id)},{model.add(product.id)},{productId=null},{productId=null;tab="Cart"},feedback={SnackbarHost(snackbars)})
                    else routes.SaveableStateProvider(tab) { when(tab) {
                        "Search" -> SearchScreen(state,{productId=it},model::save,paged,model::search)
                        "Saved" -> SavedScreen(state,{productId=it},model::save,model::add,{tab="Shop"})
                        "Cart" -> CartScreen(state,{productId=it},model::quantity,model::remove,model::checkout,{tab="Shop"},model::dismissOrder,feedback={SnackbarHost(snackbars)})
                        else -> ShopScreen(state,{productId=it},model::save,{tab="Search"},paged,model::search)
                    } }
                }
            }
        }
    }
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("Made for learning.")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Common Goods is the shopping companion to The Staff Android Engineer. Explore the collection, keep a list and try a demo order.",style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp));Text("Your saved items and cart stay on this device. Checkout uses the local demo server. Orders awaiting confirmation remain on this device; no payment is taken and nothing is shipped.",style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp));Text("The product images and catalogue are original sample content.",style=MaterialTheme.typography.bodySmall)
            if(state!=null) {Spacer(Modifier.height(24.dp));HorizontalDivider();Spacer(Modifier.height(16.dp));Text("Learning tools",style=MaterialTheme.typography.titleMedium);Text("Accepted state revision: ${state.revision}\nProducts: ${state.products.size} · Cart items: ${state.cartItemCount}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))}
            if(Reports.configured) {
                var consent by remember {mutableStateOf(Reports.enabled())}
                Spacer(Modifier.height(20.dp))
                Text("Diagnostic reports",style=MaterialTheme.typography.titleMedium)
                Text("Optionally send crash diagnostics to this app's configured Firebase project. Reports include device/app details and stacks; handled failures use bounded categories.",style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Share diagnostic reports",modifier=Modifier.weight(1f))
                    Switch(checked=consent,onCheckedChange={consent=it;Reports.consent(context,it)})
                }
            }
            if(BuildConfig.DEBUG) {
                TextButton(onClick={DiagnosticTools.inspect(context)}){Text("Inspect HTTP requests")}
                TextButton(onClick={DiagnosticTools.retainForLab(android.view.View(context))}){Text("Retain a detached lab view")}
                TextButton(onClick={DiagnosticTools.repairRetention()}){Text("Release the lab view")}
                TextButton(onClick=onEnableNetwork){Text("Allow local demo-server access")}
                TextButton(onClick={paged.refresh()}){Text("Refresh catalogue")}
                TextButton(onClick={ReportPolicy(Reports).failure("save_cart",java.io.IOException("private sample detail"))}){Text("Record a sanitized test failure")}
            }
        }
    },confirmButton={TextButton(onClick={about=false}){Text("Back to the shop")}})
}

@Composable private fun ShopTopBar(onAbout:()->Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=20.dp,end=10.dp,top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary,CircleShape))
            Spacer(Modifier.width(9.dp))
            Text("common goods",style=MaterialTheme.typography.headlineSmall.copy(letterSpacing=(-.6).sp,fontWeight=FontWeight.Bold))
        }
        IconAction(ShopSymbol.Info,"About Common Goods and learning tools",onAbout,Modifier.testTag("about-shop"))
    }
}
