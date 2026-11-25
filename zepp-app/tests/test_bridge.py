import json, sys, threading, unittest, urllib.request, urllib.error
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "bridge"))
import bridge  # noqa: E402


class Fake:
    def __init__(self): self.calls = []
    def publish(self, *a): self.calls.append(a)


class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.pub = Fake()
        self.srv = ThreadingHTTPServer(("127.0.0.1", 0), bridge.make_handler(self.pub, "tok", "casa/"))
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.url = f"http://127.0.0.1:{self.srv.server_port}/publish"

    def tearDown(self): self.srv.shutdown()

    def post(self, body, auth="Bearer tok"):
        req = urllib.request.Request(self.url, json.dumps(body).encode(), {"Authorization": auth, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as r: return r.status
        except urllib.error.HTTPError as e: return e.code

    def test_ok(self):
        self.assertEqual(self.post({"topic": "casa/luce", "payload": "ON", "qos": 1, "retain": True}), 200)
        self.assertEqual(self.pub.calls, [("casa/luce", "ON", 1, True)])

    def test_auth(self): self.assertEqual(self.post({"topic": "casa/a"}, "Bearer x"), 401)
    def test_prefix(self): self.assertEqual(self.post({"topic": "altro/a"}), 400)
    def test_wildcard(self): self.assertEqual(self.post({"topic": "casa/#"}), 400)
    def test_qos(self): self.assertEqual(self.post({"topic": "casa/a", "qos": 5}), 400)


if __name__ == "__main__":
    unittest.main()
