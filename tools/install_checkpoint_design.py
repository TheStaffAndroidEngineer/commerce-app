#!/usr/bin/env python3
"""Vendor the approved Common Goods presentation into an existing teaching module.

No Gradle edge or runtime owner is added. Module-local resources keep checkpoints
independently runnable; later modularization remains an explicit teaching step.
"""
from pathlib import Path
import argparse,hashlib,json,shutil,re
ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'projects/commerce-app/core/designsystem/src/main'
HELPERS='''

@Composable
fun CommonGoodsHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.cg_mark), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text("Common Goods", style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun ProductPhoto(id: String, modifier: Modifier = Modifier) {
    val asset = when (id.lowercase()) {
        "tea" -> R.drawable.cg_tea
        "bag" -> R.drawable.cg_bag
        "headphones" -> R.drawable.cg_headphones
        "camera" -> R.drawable.cg_camera
        else -> null
    }
    if (asset == null) {
        ShopMessage("Product photo unavailable", modifier)
        return
    }
    Image(painterResource(asset), contentDescription = null, contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow))
}

@Composable
fun ProductIdentity(id: String, title: String, subtitle: String = "Saved for later") {
    ProductPhoto(id)
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ShopMessage(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LabControls(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("lab_controls").semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
            Text(if (expanded) "Close lab controls" else "Lab controls")
        }
        if (expanded) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow,
            RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
'''
def install(module: Path, namespace: str, include_tripod=False, replace_existing=False):
    main=module/'src/main';target=main/'kotlin'/Path(*namespace.split('.'))/'CommonGoods.kt'
    if target.exists() and not replace_existing:
        raise ValueError(f'Refusing to replace the reviewed checkpoint helper: {target}. Use --replace-existing only after reviewing its local adaptations.')
    target.parent.mkdir(parents=True,exist_ok=True)
    theme=(BASE/'kotlin/staff/android/commerce/design/CommonTheme.kt').read_text().replace('package staff.android.commerce.design',f'package {namespace}').replace('CommonTheme(', 'CommonGoodsTheme(')
    extra='''import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
'''
    theme=theme.replace('import androidx.compose.foundation.isSystemInDarkTheme',extra+'import androidx.compose.foundation.isSystemInDarkTheme')
    for font in ['google_sans_regular','google_sans_bold','sora_bold']:theme=theme.replace(f'R.font.{font}',f'R.font.cg_{font}')
    helpers=HELPERS
    if include_tripod:
        helpers=helpers.replace('"camera" -> R.drawable.cg_camera', '"camera" -> R.drawable.cg_camera\n        "tripod" -> R.drawable.cg_tripod')
    target.write_text(theme+helpers)
    sources=[]
    for font in ['google_sans_regular','google_sans_bold','sora_bold']:
        sources.append((BASE/f'res/font/{font}.ttf',main/f'res/font/cg_{font}.ttf'))
    for asset in ['camera','tea','bag','headphones']:sources.append((BASE/f'assets/{asset}.png',main/f'res/drawable-nodpi/cg_{asset}.png'))
    if include_tripod:sources.append((BASE/'assets/tripod.png',main/'res/drawable-nodpi/cg_tripod.png'))
    for font in ['sora','google-sans']:sources.append((BASE/f'assets/licenses/{font}-OFL.txt',main/f'assets/licenses/{font}-OFL.txt'))
    sources.append((ROOT/'projects/compose-migration/app/src/main/res/drawable/ic_common_goods.xml',main/'res/drawable/cg_mark.xml'))
    records=[]
    for source,dest in sources:
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
        records.append({'source':str(source.relative_to(ROOT)),'destination':str(dest.relative_to(ROOT)),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
    record={'date':'2026-10-07','identity':'Common Goods','upstream_provenance':'projects/commerce-app/assets/provenance.json','files':records}
    if include_tripod:record['supplemental_provenance']=['projects/commerce-app/assets/tripod-provenance.json']
    provenance=module/'common-goods-assets.json';provenance.write_text(json.dumps(record,indent=2)+'\n')
    print(module.relative_to(ROOT),namespace)
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('module');parser.add_argument('namespace')
    parser.add_argument('--include-tripod',action='store_true')
    parser.add_argument('--replace-existing',action='store_true')
    args=parser.parse_args();install(ROOT/args.module,args.namespace,args.include_tripod,args.replace_existing)
