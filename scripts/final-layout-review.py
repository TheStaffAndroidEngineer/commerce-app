#!/usr/bin/env python3
"""Continue the documented final host review from the two-line CG-00002 receipt."""
import json,time
from pathlib import Path
import device_review as d
out=d.ROOT/'evidence/ui/final'
steps=[]
def shot(name):
    d.final_capture(name);steps.append(name)
try:
    d.tap('Continue shopping');d.tap('Cart');shot('11-cart-empty')
    d.tap('Saved');d.tap('Add Everyday bag to cart','content-desc');d.tap('Add Tea to cart','content-desc');d.tap('Cart')
    d.shell('cmd','uimode','night','yes');time.sleep(1);shot('12-cart-dark')
    d.tap('Shop');shot('13-shop-dark')
    d.shell('cmd','uimode','night','no');d.shell('settings','put','system','font_scale','2.0');time.sleep(1);shot('14-shop-large-text')
    d.tap('Cart');shot('15-cart-large-text')
    d.tap('Edit quantity for Everyday bag','content-desc');d.tap('1');time.sleep(1);shot('16-quantity-large-text-keyboard')
    d.tap('Cancel');d.scroll_to_text('Continue shopping');shot('17-cart-large-text-summary')
    d.shell('settings','put','system','font_scale','1.0');d.shell('wm','size','2160x1600');d.shell('am','force-stop',d.PACKAGE);d.start();time.sleep(1);shot('18-shop-wide')
    d.tap('Search');d.tap('View Tea','content-desc');shot('19-product-wide')
    d.shell('wm','size','945x2048');d.shell('am','force-stop',d.PACKAGE);d.start();time.sleep(1.5);shot('20-shop-narrow')
    d.scroll_to_text('A small collection. A little more considered.');shot('21-shop-narrow-last-products')
finally:
    d.shell('wm','size','reset');d.shell('settings','put','system','font_scale','1.0');d.shell('cmd','uimode','night','no')
    d.shell('am','force-stop',d.PACKAGE);d.start()
    (out/'layout-run.json').write_text(json.dumps({'completed_captures':steps,'restored':{'size':d.shell('wm','size'),'density':d.shell('wm','density'),'font_scale':d.shell('settings','get','system','font_scale'),'night':d.shell('cmd','uimode','night')}},indent=2)+'\n')
