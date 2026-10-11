import json,tempfile,threading,unittest,urllib.request,urllib.error,http.client
from pathlib import Path
from server import create_server
class ServerContractTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.server=create_server(0,str(Path(self.tmp.name)/'orders.db'))
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();self.url=f'http://127.0.0.1:{self.server.server_port}'
    def tearDown(self):self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
    def request(self,path,body=None,key=None):
        headers={'Content-Type':'application/json'}
        if key:headers['Idempotency-Key']=key
        request=urllib.request.Request(self.url+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
        with urllib.request.urlopen(request,timeout=3) as response:return json.load(response)
    def test_pagination_and_representation_identity(self):
        first=self.request('/catalog?q=&category=All');self.assertEqual(2,len(first['items']))
        more=self.request('/catalog?cursor='+urllib.parse.quote(first['nextCursor']));self.assertNotEqual(first['items'][0]['id'],more['items'][0]['id'])
        with self.assertRaises(urllib.error.HTTPError) as failure:self.request('/catalog?q=tea&cursor='+urllib.parse.quote(first['nextCursor']))
        self.assertEqual(400,failure.exception.code)
        self.assertEqual(['tea'],[x['id'] for x in self.request('/catalog?q=tea')['items']])
    def test_lost_response_reuses_durable_receipt(self):
        command={'requestId':'request-one','lines':[{'id':'tea','quantity':2,'priceMinor':49900}]}
        self.request('/__control',{'dropOrderResponse':True})
        with self.assertRaises((http.client.RemoteDisconnected,urllib.error.URLError)):self.request('/orders',command,'request-one')
        self.request('/__control',{})
        receipt=self.request('/orders',command,'request-one');self.assertEqual('CG-00001',receipt['orderId'])
        self.assertEqual(1,self.request('/__control')['orders'])
        command['lines'][0]['quantity']=3
        with self.assertRaises(urllib.error.HTTPError) as failure:self.request('/orders',command,'request-one')
        self.assertEqual(409,failure.exception.code)
    def test_rejected_order_never_creates_receipt(self):
        command={'requestId':'rejected','lines':[{'id':'tea','quantity':99,'priceMinor':49900}]}
        with self.assertRaises(urllib.error.HTTPError) as failure:self.request('/orders',command,'rejected')
        self.assertEqual(409,failure.exception.code);self.assertEqual(0,self.request('/__control')['orders'])
if __name__=='__main__':unittest.main()
