// Pagina impostazioni (app Zepp sul telefono).
const SLOTS = 14
const ICON_HELP =
  'Icone: bulb, power, lock, door, garage, fan, bell, home, music, flame, snow, bolt, dot'

function slot(i) {
  return Section({}, [
    Text({ bold: true, paragraph: true }, `Pulsante ${i}`),
    TextInput({ label: 'Etichetta', settingsKey: `b${i}_label`, placeholder: 'es. Luce' }),
    TextInput({ label: 'Topic', settingsKey: `b${i}_topic`, placeholder: 'es. casa/salotto/luce/set' }),
    TextInput({ label: 'Payload', settingsKey: `b${i}_payload`, placeholder: 'es. ON' }),
    TextInput({ label: 'Icona', settingsKey: `b${i}_icon`, placeholder: 'bulb' }),
    Toggle({ label: 'Retain', settingsKey: `b${i}_retain` }),
  ])
}

AppSettingsPage({
  build() {
    const items = [
      Section({}, [
        Text({ bold: true, paragraph: true }, 'Server (bridge HTTP -> MQTT)'),
        TextInput({
          label: 'URL bridge',
          settingsKey: 'bridge_url',
          placeholder: 'http://192.168.1.10:8787/publish',
        }),
        TextInput({
          label: 'Authorization (opzionale)',
          settingsKey: 'bridge_auth',
          placeholder: 'Bearer il-tuo-token',
        }),
        TextInput({
          label: 'Prefisso topic (opzionale)',
          settingsKey: 'topic_prefix',
          placeholder: 'es. casa',
        }),
        TextInput({ label: 'QoS (0, 1 o 2)', settingsKey: 'qos', placeholder: '0' }),
        Text({ paragraph: true }, ICON_HELP),
      ]),
    ]
    for (let i = 1; i <= SLOTS; i++) items.push(slot(i))
    return View({ style: { padding: '12px 20px' } }, items)
  },
})
