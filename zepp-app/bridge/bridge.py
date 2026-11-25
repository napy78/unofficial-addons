#!/usr/bin/env python3
"""Bridge HTTP -> MQTT per l'app MQTT Pad (Amazfit Balance).

POST /publish  {"topic": "...", "payload": "...", "qos": 0, "retain": false}
Header opzionale:  Authorization: Bearer <BRIDGE_TOKEN>

Variabili d'ambiente:
  MQTT_HOST (127.0.0.1)  MQTT_PORT (1883)  MQTT_USER  MQTT_PASS  MQTT_TLS (0/1)
  BRIDGE_TOKEN   se impostato, richiede "Authorization: Bearer <token>"
  ALLOWED_PREFIX se impostato, accetta solo topic che iniziano con questo prefisso
  BIND (0.0.0.0)  PORT (8787)
"""
import hmac
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 8 * 1024


class MqttPublisher:
    def __init__(self):
        import paho.mqtt.client as mqtt

        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        user = os.environ.get("MQTT_USER")
        if user:
            self.client.username_pw_set(user, os.environ.get("MQTT_PASS", ""))
        if os.environ.get("MQTT_TLS", "0") == "1":
            self.client.tls_set()
        self.client.reconnect_delay_set(1, 30)
        self.client.connect_async(
            os.environ.get("MQTT_HOST", "127.0.0.1"), int(os.environ.get("MQTT_PORT", "1883")), 30
        )
        self.client.loop_start()

    def publish(self, topic, payload, qos, retain):
        info = self.client.publish(topic, payload, qos=qos, retain=retain)
        info.wait_for_publish(timeout=5)
        if not info.is_published():
            raise RuntimeError("broker MQTT non raggiungibile")


def validate(body, allowed_prefix=""):
    """Restituisce (topic, payload, qos, retain) o lancia ValueError."""
    if not isinstance(body, dict):
        raise ValueError("JSON non valido")
    topic = body.get("topic")
    if not isinstance(topic, str) or not topic or "+" in topic or "#" in topic or "\0" in topic:
        raise ValueError("topic non valido")
    if allowed_prefix and not topic.startswith(allowed_prefix):
        raise ValueError("topic non consentito")
    payload = body.get("payload", "")
    if not isinstance(payload, str):
        payload = json.dumps(payload)
    qos = body.get("qos", 0)
    if qos not in (0, 1, 2):
        raise ValueError("qos non valido")
    return topic, payload, qos, bool(body.get("retain", False))


def make_handler(publisher, token="", allowed_prefix=""):
    class Handler(BaseHTTPRequestHandler):
        def _reply(self, code, obj):
            data = json.dumps(obj).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if self.path == "/health":
                return self._reply(200, {"ok": True})
            self._reply(404, {"ok": False, "error": "not found"})

        def do_POST(self):
            if self.path.rstrip("/") not in ("/publish", "/api/v5/publish"):
                return self._reply(404, {"ok": False, "error": "not found"})
            if token:
                sent = self.headers.get("Authorization", "")
                if not hmac.compare_digest(sent, f"Bearer {token}"):
                    return self._reply(401, {"ok": False, "error": "unauthorized"})
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length > MAX_BODY:
                    return self._reply(413, {"ok": False, "error": "body troppo grande"})
                topic, payload, qos, retain = validate(json.loads(self.rfile.read(length) or b"{}"), allowed_prefix)
            except (ValueError, json.JSONDecodeError) as e:
                return self._reply(400, {"ok": False, "error": str(e)})
            try:
                publisher.publish(topic, payload, qos, retain)
            except Exception as e:  # noqa: BLE001
                return self._reply(502, {"ok": False, "error": str(e)})
            self._reply(200, {"ok": True})

        def log_message(self, fmt, *args):
            sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    return Handler


def main():
    server = ThreadingHTTPServer(
        (os.environ.get("BIND", "0.0.0.0"), int(os.environ.get("PORT", "8787"))),
        make_handler(MqttPublisher(), os.environ.get("BRIDGE_TOKEN", ""), os.environ.get("ALLOWED_PREFIX", "")),
    )
    print("bridge in ascolto su porta", server.server_port, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
