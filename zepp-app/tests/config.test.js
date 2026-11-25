import test from 'node:test'
import assert from 'node:assert/strict'
import { readButtons, readServer, buildPublishRequest } from '../shared/config.js'

const store = (o) => (k) => o[k]

test('readButtons salta slot vuoti e normalizza icona/retain', () => {
  const b = readButtons(store({
    b1_label: 'Luce', b1_topic: 'casa/luce', b1_payload: 'ON', b1_icon: 'BULB', b1_retain: 'true',
    b2_label: 'solo etichetta',
    b3_topic: 'casa/x', b3_icon: 'inesistente',
  }))
  assert.equal(b.length, 2)
  assert.deepEqual(b[0], { id: 1, label: 'Luce', icon: 'bulb', topic: 'casa/luce', payload: 'ON', retain: true })
  assert.equal(b[1].icon, 'dot')
  assert.equal(b[1].label, 'x')
})

test('buildPublishRequest applica prefisso, auth e qos', () => {
  const server = readServer(store({ bridge_url: 'http://h/publish', bridge_auth: 'Bearer t', topic_prefix: 'casa/', qos: '1' }))
  const r = buildPublishRequest(server, { topic: '/luce', payload: 'ON', retain: true })
  assert.equal(r.headers.Authorization, 'Bearer t')
  assert.deepEqual(JSON.parse(r.body), { topic: 'casa/luce', payload: 'ON', qos: 1, retain: true })
})

test('buildPublishRequest senza URL lancia errore', () => {
  assert.throws(() => buildPublishRequest(readServer(store({})), { topic: 'a' }), /URL/)
})
