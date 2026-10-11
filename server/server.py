#!/usr/bin/env python3
"""Local-only catalogue/images/idempotent orders. No account, payment or external service."""
import argparse, hashlib, json, pathlib, socket, sqlite3, threading, time
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
ROOT=pathlib.Path(__file__).resolve().parents[1]
CATALOG=json.loads((pathlib.Path(__file__).with_name('catalog.json')).read_text())
class Fixture:
    def __init__(self,path):
        self.lock=threading.Lock();self.mode={};self.requests=[];self.path=path
        with sqlite3.connect(path) as db: db.execute('CREATE TABLE IF NOT EXISTS orders (request_id TEXT PRIMARY KEY,payload TEXT NOT NULL,order_id TEXT NOT NULL)')
    def accept(self,request_id,command):
        canonical=json.dumps(command,sort_keys=True,separators=(',',':'))
        with self.lock, sqlite3.connect(self.path) as db:
            old=db.execute('SELECT payload,order_id FROM orders WHERE request_id=?',(request_id,)).fetchone()
            if old:
                if old[0]!=canonical: return 409,{'error':'Idempotency key reused with different payload'}
                return 200,{'requestId':request_id,'orderId':old[1]}
            products={p['id']:p for p in CATALOG};lines=command.get('lines',[])
            if not lines or len(lines)>100 or len({x.get('id') for x in lines})!=len(lines):return 400,{'error':'Invalid lines'}
            for line in lines:
                p=products.get(line.get('id'));q=line.get('quantity')
                if not p or type(q)!=int or not 1<=q<=p['maxQuantity'] or line.get('priceMinor')!=p['priceMinor']:return 409,{'error':'Order no longer matches catalogue'}
            ordinal=db.execute('SELECT COUNT(*) FROM orders').fetchone()[0]+1;order_id=f'CG-{ordinal:05}'
            db.execute('INSERT INTO orders VALUES(?,?,?)',(request_id,canonical,order_id))
            return 200,{'requestId':request_id,'orderId':order_id}
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def send(self,status,body,media='application/json'):
        data=json.dumps(body).encode() if media=='application/json' else body
        self.send_response(status)
        # This HTTP/1.0 fixture closes after every reply. Tell pooled clients
        # before they race to reuse a socket whose peer has already closed it.
        self.send_header('Connection','close')
        self.send_header('Content-Type',media);self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store' if media=='application/json' else 'public, max-age=31536000, immutable');self.end_headers()
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError):pass
    def do_GET(self):
        parsed=urlparse(self.path);q=parse_qs(parsed.query);f=self.server.fixture
        with f.lock:f.requests.append(self.path);mode=dict(f.mode)
        if parsed.path=='/__control':
            with sqlite3.connect(f.path) as db: count=db.execute('SELECT COUNT(*) FROM orders').fetchone()[0]
            return self.send(200,{'mode':mode,'requests':f.requests,'orders':count})
        if parsed.path=='/catalog':
            if mode.get('catalogDelayMs'):time.sleep(min(mode['catalogDelayMs'],10000)/1000)
            if mode.get('catalogError') or (mode.get('appendError') and 'cursor' in q):return self.send(503,{'error':'Controlled outage'})
            query=q.get('q',[''])[0].strip().lower();category=q.get('category',['All'])[0]
            representation=f'INR|{category}|{query}';prefix=hashlib.sha256(representation.encode()).hexdigest()[:12]
            cursor=q.get('cursor',[None])[0];offset=0
            if cursor:
                try:
                    signature,position=cursor.split(':');offset=int(position)
                    if signature!=prefix or offset<0 or offset>1000:raise ValueError()
                except ValueError:return self.send(400,{'error':'Cursor belongs to another representation'})
            items=[p for p in CATALOG if (category=='All' or p['category']==category or category=='Weekend' and p['category'] in ('Carry','Tech')) and query in (' '.join((p['name'],p['category'],p['subtitle']))).lower()]
            page=[dict(p) for p in items[offset:offset+2]]
            if mode.get('imageVersion')==2:
                for p in page:
                    if p['id']=='bag':p['imageKey']='bag-v2'
            next_cursor=f'{prefix}:{offset+2}' if offset+2<len(items) else None
            return self.send(200,{'items':page,'nextCursor':next_cursor,'snapshot':f'{prefix}-s1'})
        if parsed.path.startswith('/images/'):
            key=pathlib.PurePosixPath(parsed.path).name
            if mode.get('imageError'):return self.send(503,{'error':'Controlled image outage'})
            if mode.get('imageDelayMs'):time.sleep(min(mode['imageDelayMs'],10000)/1000)
            # Only known authored assets; v2 intentionally serves another picture for identity labs.
            valid={p['imageKey']+'.png' for p in CATALOG}|{'bag-v2.png'}
            if key not in valid:return self.send(404,{'error':'Unknown image'})
            source=('camera' if key=='bag-v2.png' else key.removesuffix('-v1.png'))+'.png'
            return self.send(200,(ROOT/'core/designsystem/src/main/assets'/source).read_bytes(),'image/png')
        self.send(404,{'error':'Unknown route'})
    def do_POST(self):
        size=int(self.headers.get('Content-Length','0'))
        if not 0<size<=65536:return self.send(413,{'error':'Bounded JSON body required'})
        try:body=json.loads(self.rfile.read(size))
        except (ValueError,UnicodeError):return self.send(400,{'error':'Invalid JSON'})
        f=self.server.fixture
        if self.path=='/__control':
            with f.lock:f.mode=body
            return self.send(200,{'mode':body})
        if self.path!='/orders':return self.send(404,{'error':'Unknown route'})
        key=self.headers.get('Idempotency-Key')
        if not key or len(key)>64 or body.get('requestId')!=key:return self.send(400,{'error':'Invalid request identity'})
        with f.lock:mode=dict(f.mode)
        if mode.get('orderError'):return self.send(503,{'error':'Controlled order outage'})
        status,reply=f.accept(key,body)
        if mode.get('dropOrderResponse') and status==200:
            self.close_connection=True
            try:self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:pass
            return
        self.send(status,reply)
def create_server(port=8788,database=None):
    server=ThreadingHTTPServer(('127.0.0.1',port),Handler)
    server.fixture=Fixture(database or str(pathlib.Path(__file__).with_name('orders.sqlite')))
    return server
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8788);args=parser.parse_args()
    server=create_server(args.port);print(f'Common Goods fixture on http://127.0.0.1:{server.server_port}',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()
