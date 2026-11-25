# MQTT Pad per Amazfit Balance (Zepp OS)

Tastierino di pulsanti con icone: un tocco sull'orologio pubblica un messaggio sul tuo broker MQTT.

```
Orologio ──BLE──> App Zepp (telefono) ──HTTP──> bridge.py ──MQTT──> broker
```

L'orologio non può aprire connessioni MQTT/TCP, quindi il telefono inoltra la richiesta a un piccolo
bridge HTTP→MQTT (`bridge/bridge.py`). Se usi EMQX puoi puntare direttamente a
`http://host:18083/api/v5/publish` (accetta lo stesso JSON) mettendo in *Authorization* l'header completo.

## 1. Bridge (sul server)

```bash
cd bridge && pip install -r requirements.txt
MQTT_HOST=127.0.0.1 BRIDGE_TOKEN=cambiami ALLOWED_PREFIX=casa/ python3 bridge.py
```

Variabili: `MQTT_HOST`, `MQTT_PORT`, `MQTT_USER`, `MQTT_PASS`, `MQTT_TLS=1`, `BRIDGE_TOKEN`,
`ALLOWED_PREFIX`, `PORT` (8787). Usa HTTPS (reverse proxy) se lo esponi fuori dalla LAN.

## 2. App

```bash
npm install
npm run icons    # rigenera le icone (Pillow)
npm test         # test della logica e del bridge
npx zeus login   # una tantum
npx zeus dev     # anteprima sull'orologio via QR code / simulatore
npx zeus build   # crea il pacchetto .zab
```

## 3. Configurazione (app Zepp → MQTT Pad → Impostazioni)

- **URL bridge**: es. `http://192.168.1.10:8787/publish`
- **Authorization**: es. `Bearer cambiami`
- **Prefisso topic** e **QoS** opzionali
- Fino a 14 pulsanti (7 per schermata, scorrimento verticale): etichetta, topic, payload, icona, retain.
  Icone: `bulb power lock door garage fan bell home music flame snow bolt dot`.

L'orologio sincronizza i pulsanti all'apertura e li tiene in cache.

## Note
- Non verificato su dispositivo reale: `app.json` usa `platforms: [{"st":"r"}]` (schermi tondi, 480 px).
  Se `zeus` chiede un dispositivo specifico, scegli Amazfit Balance.
- `appId` è un segnaposto: usa quello assegnato dal Zepp Developer Console per pubblicare.
