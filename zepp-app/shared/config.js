// Logica condivisa (pura, testabile con `npm test`): lettura impostazioni e
// costruzione della richiesta HTTP verso il bridge MQTT.

export const SLOTS = 14
export const ICONS = [
  'bulb', 'power', 'lock', 'door', 'garage', 'fan', 'bell',
  'home', 'music', 'flame', 'snow', 'bolt', 'dot',
]

const asBool = (v) => v === true || v === 'true' || v === '1'
const str = (v) => (v === undefined || v === null ? '' : String(v)).trim()

/** Legge i pulsanti da uno storage con getItem(key) (settingsStorage). */
export function readButtons(getItem) {
  const buttons = []
  for (let i = 1; i <= SLOTS; i++) {
    const label = str(getItem(`b${i}_label`))
    const topic = str(getItem(`b${i}_topic`))
    if (!label && !topic) continue
    if (!topic) continue
    let icon = str(getItem(`b${i}_icon`)).toLowerCase()
    if (!ICONS.includes(icon)) icon = 'dot'
    buttons.push({
      id: i,
      label: label || topic.split('/').pop(),
      icon,
      topic,
      payload: str(getItem(`b${i}_payload`)),
      retain: asBool(getItem(`b${i}_retain`)),
    })
  }
  return buttons
}

/** Legge le impostazioni del server/bridge. */
export function readServer(getItem) {
  let url = str(getItem('bridge_url'))
  const qos = parseInt(str(getItem('qos')), 10)
  return {
    url,
    auth: str(getItem('bridge_auth')),
    prefix: str(getItem('topic_prefix')).replace(/\/+$/, ''),
    qos: qos === 1 || qos === 2 ? qos : 0,
  }
}

/** Costruisce {url, method, headers, body} per il bridge. Lancia Error se manca la config. */
export function buildPublishRequest(server, btn) {
  if (!server.url) throw new Error('URL del bridge non impostato')
  if (!btn || !btn.topic) throw new Error('Topic mancante')
  const topic = server.prefix ? `${server.prefix}/${btn.topic.replace(/^\/+/, '')}` : btn.topic
  const headers = { 'Content-Type': 'application/json' }
  if (server.auth) headers.Authorization = server.auth
  return {
    url: server.url,
    method: 'POST',
    headers,
    body: JSON.stringify({
      topic,
      payload: btn.payload === undefined ? '' : String(btn.payload),
      qos: server.qos,
      retain: !!btn.retain,
    }),
  }
}
