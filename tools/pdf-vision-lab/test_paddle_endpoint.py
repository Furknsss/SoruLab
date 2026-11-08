import base64
import http.server
import json
import socketserver
import threading
import unittest
from importlib.machinery import SourceFileLoader
from pathlib import Path

PADDLE = SourceFileLoader(
    "paddle_smoke", str(Path(__file__).with_name("smoke_paddle_endpoint.py"))
).load_module()

class PaddleContractTests(unittest.TestCase):
    def test_synthetic_image_is_a_real_png(self):
        sample = PADDLE.demo_png()
        self.assertTrue(sample.startswith(b"\x89PNG\r\n\x1a\n"))
        self.assertGreater(len(sample), 200)

    def test_accepts_official_one_page_layout_response(self):
        payload = {"result":{"layoutParsingResults":[{"prunedResult":{
            "parsing_res_list":[{"block_bbox":[100,100,300,180],
                                  "block_label":"text","block_content":"TEST 1"}]
        }}]}}
        self.assertEqual(1, PADDLE.validate_live_response(payload))

    def test_rejects_missing_or_malformed_model_output(self):
        for payload in [
            {"result":{"layoutParsingResults":[]}},
            {"result":{"layoutParsingResults":[{"prunedResult":{}}]}},
            {"result":{"layoutParsingResults":[{"prunedResult":{
                "parsing_res_list":[{"block_bbox":[1,2,3], "block_label":"text"}]
            }}]}}
        ]:
            with self.assertRaises((ValueError,KeyError,TypeError)):
                PADDLE.validate_live_response(payload)

    def test_never_sends_page_to_untrusted_remote_url(self):
        with self.assertRaises(ValueError):
            PADDLE.run("https://not-our-gpu.example")

    def test_local_gateway_round_trip_contract(self):
        seen={}
        class FakePaddle(http.server.BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_GET(self):
                if self.path!="/health/ready":
                    self.send_error(404);return
                self.send_response(200);self.end_headers();self.wfile.write(b'{"ready":true}')
            def do_POST(self):
                self.send_error(404) if self.path!="/layout-parsing" else None
                if self.path!="/layout-parsing":return
                req=json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                seen["fileType"]=req.get("fileType")
                seen["png"]=base64.b64decode(req.get("file","")).startswith(b"\x89PNG\r\n\x1a\n")
                payload={"result":{"layoutParsingResults":[{"prunedResult":{"parsing_res_list":[]}}]}}
                data=json.dumps(payload).encode()
                self.send_response(200)
                self.send_header("Content-Type","application/json")
                self.send_header("Content-Length",str(len(data)))
                self.end_headers();self.wfile.write(data)
        server=http.server.ThreadingHTTPServer(("127.0.0.1",0),FakePaddle)
        thread=threading.Thread(target=server.serve_forever,daemon=True)
        try:
            thread.start()
            PADDLE.run("http://127.0.0.1:%d" % server.server_port)
        finally:
            server.shutdown()
            server.server_close()
        self.assertEqual(1,seen["fileType"])
        self.assertTrue(seen["png"])

if __name__=="__main__":
    unittest.main()
