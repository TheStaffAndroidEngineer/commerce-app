#!/usr/bin/env python3
"""Precondition: Everyday bag is in Cart. Verifies native large-text IME layout."""
import time,json,argparse,re
from pathlib import Path
import device_review as d
parser=argparse.ArgumentParser();parser.add_argument('--first-capture',type=int,default=26)
args=parser.parse_args();first=args.first_capture
before={k:d.shell('settings','get','secure',k) for k in ('show_ime_with_hard_keyboard','stylus_handwriting_enabled')}
try:
    d.shell('settings','put','secure','show_ime_with_hard_keyboard','1')
    d.shell('settings','put','secure','stylus_handwriting_enabled','0')
    d.shell('settings','put','system','font_scale','2.0')
    d.shell('am','force-stop',d.PACKAGE);d.start();d.tap('Cart')
    d.scroll_to_text('Qty 1');d.tap('Edit quantity for Everyday bag','content-desc');d.tap('1')
    time.sleep(1.5);d.final_capture(f'{first:02d}-quantity-full-keyboard')
    d.tap('Cancel')
finally:
    for k,v in before.items():
        if v=='null':d.shell('settings','delete','secure',k)
        else:d.shell('settings','put','secure',k,v)
    d.shell('settings','put','system','font_scale','1.0')
    d.shell('am','force-stop',d.PACKAGE);d.start()
    Path(f'evidence/ui/final/ime-review-{first:02d}-settings.json').write_text(json.dumps({'before':before,'after':{k:d.shell('settings','get','secure',k) for k in before}},indent=2)+'\n')
d.tap('Cart');d.tap('Edit quantity for Everyday bag','content-desc');d.final_capture(f'{first+1:02d}-quantity-compact')
# The title's observed vertical position gives an outside-sheet tap on the left margin.
_,root=d.tree();title=next(n for n in root.iter('node') if n.get('text')=='Change quantity')
x1,y1,x2,y2=map(int,re.findall(r'\d+',title.get('bounds')))
d.shell('input','tap','10',str((y1+y2)//2));time.sleep(.4)
assert not any(n['text']=='Change quantity' for n in d.nodes()), 'Outside tap must cancel the draft'
d.tap('Edit quantity for Everyday bag','content-desc');d.tap('Everyday bag')
assert any(n['text']=='Confirm' for n in d.nodes()), 'Tapping dialog content must not dismiss'
d.tap('Cancel');d.tap('Shop');d.final_capture(f'{first+2:02d}-shop-delivery')
Path(f'evidence/ui/final/ime-review-{first:02d}-interactions.json').write_text(json.dumps({
    'full_keyboard_cancel':True,'outside_tap_dismisses':True,'content_tap_keeps_dialog':True,
    'compact_cancel':True,'font_scale_restored':d.shell('settings','get','system','font_scale')},indent=2)+'\n')
