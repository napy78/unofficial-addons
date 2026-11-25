import { BaseSideService } from '@zeppos/zml/base-side'
import { readButtons, readServer, buildPublishRequest } from '../shared/config'

// Gira sul telefono (app Zepp). Riceve le richieste dall'orologio e le inoltra
// al bridge HTTP->MQTT. L'orologio non puo' parlare MQTT direttamente.
AppSideService(
  BaseSideService({
    onInit() {
      this.log('MQTT Pad side service avviato')
    },

    onRequest(req, res) {
      const get = (k) => this.settings.getItem(k)

      if (req.method === 'GET_CONFIG') {
        return res(null, { buttons: readButtons(get) })
      }

      if (req.method === 'PUBLISH') {
        let request
        try {
          const btn = readButtons(get).find((b) => b.id === req.params.id)
          request = buildPublishRequest(readServer(get), btn)
        } catch (e) {
          return res(null, { ok: false, error: e.message })
        }

        return this.fetch({ ...request, timeout: 8000 })
          .then((r) => {
            const ok = r.status >= 200 && r.status < 300
            res(null, { ok, status: r.status, error: ok ? '' : `HTTP ${r.status}` })
          })
          .catch((e) => res(null, { ok: false, error: (e && e.message) || 'Rete non raggiungibile' }))
      }

      res('metodo sconosciuto')
    },
  }),
)
