import { BasePage } from '@zeppos/zml/base-page'
import * as hmUI from '@zos/ui'
import { replace } from '@zos/router'
import { LocalStorage } from '@zos/storage'
import { Vibrator, VIBRATOR_SCENE_SHORT_LIGHT } from '@zos/sensor'

const W = 480
const TILE = 120
const PER_PAGE = 7
// Layout "a esagono" pensato per il display tondo: righe da 2-3-2 pulsanti.
const SLOTS = [
  { x: 114, y: 48 }, { x: 246, y: 48 },
  { x: 54, y: 180 }, { x: 180, y: 180 }, { x: 306, y: 180 },
  { x: 114, y: 312 }, { x: 246, y: 312 },
]

const store = new LocalStorage()
const readCache = () => {
  try {
    return JSON.parse(store.getItem('buttons') || '[]')
  } catch (e) {
    return []
  }
}

function buzz() {
  try {
    const v = new Vibrator()
    v.setMode(VIBRATOR_SCENE_SHORT_LIGHT)
    v.start()
  } catch (e) {}
}

Page(
  BasePage({
    state: { sending: false },

    build() {
      const buttons = readCache()
      this.render(buttons)
      this.syncConfig(buttons)
    },

    render(buttons) {
      hmUI.setStatusBarVisible(false)

      if (!buttons.length) {
        hmUI.createWidget(hmUI.widget.FILL_RECT, { x: 0, y: 0, w: W, h: W, color: 0x0b0e17 })
        hmUI.createWidget(hmUI.widget.TEXT, {
          x: 60, y: 130, w: 360, h: 220, color: 0xdde3f0, text_size: 28,
          align_h: hmUI.align.CENTER_H, align_v: hmUI.align.CENTER_V,
          text_style: hmUI.text_style.WRAP,
          text: 'Nessun pulsante.\nApri l\'app Zepp > MQTT Pad > Impostazioni e configura server e pulsanti.',
        })
        return
      }

      const pages = Math.ceil(buttons.length / PER_PAGE)
      if (pages > 1) {
        try { hmUI.setScrollView(true, W, pages, true) } catch (e) {}
      }

      for (let p = 0; p < pages; p++) {
        const oy = p * W
        hmUI.createWidget(hmUI.widget.FILL_RECT, { x: 0, y: oy, w: W, h: W, color: 0x0b0e17 })
        // anello decorativo
        hmUI.createWidget(hmUI.widget.CIRCLE, {
          center_x: 240, center_y: oy + 240, radius: 236, color: 0x1a2038, alpha: 255,
        })
        hmUI.createWidget(hmUI.widget.CIRCLE, {
          center_x: 240, center_y: oy + 240, radius: 232, color: 0x0b0e17, alpha: 255,
        })

        buttons.slice(p * PER_PAGE, (p + 1) * PER_PAGE).forEach((b, i) => {
          const pos = SLOTS[i]
          const y = oy + pos.y
          hmUI.createWidget(hmUI.widget.BUTTON, {
            x: pos.x, y, w: TILE, h: TILE,
            normal_src: `${b.icon}.png`,
            press_src: `${b.icon}_p.png`,
            click_func: () => this.send(b),
          })
          hmUI.createWidget(hmUI.widget.TEXT, {
            x: pos.x + 4, y: y + TILE - 34, w: TILE - 8, h: 28,
            text: b.label, text_size: 20, color: 0xffffff,
            align_h: hmUI.align.CENTER_H, align_v: hmUI.align.CENTER_V,
            text_style: hmUI.text_style.ELLIPSIS,
          })
        })

        // indicatore di pagina
        if (pages > 1) {
          for (let d = 0; d < pages; d++) {
            hmUI.createWidget(hmUI.widget.CIRCLE, {
              center_x: 240 + (d - (pages - 1) / 2) * 16, center_y: oy + 452,
              radius: d === p ? 5 : 3, color: d === p ? 0xffffff : 0x56607f,
            })
          }
        }
      }
    },

    send(btn) {
      if (this.state.sending) return
      this.state.sending = true
      buzz()
      hmUI.showToast({ text: `${btn.label}...` })
      this.request({ method: 'PUBLISH', params: { id: btn.id } })
        .then((r) => {
          hmUI.showToast({ text: r && r.ok ? `Inviato: ${btn.label}` : `Errore: ${(r && r.error) || 'sconosciuto'}` })
        })
        .catch(() => {
          hmUI.showToast({ text: 'Telefono non connesso' })
        })
        .finally(() => {
          this.state.sending = false
        })
    },

    // Aggiorna i pulsanti dal telefono; ricarica la pagina solo se cambiano.
    syncConfig(current) {
      this.request({ method: 'GET_CONFIG' })
        .then((r) => {
          const next = (r && r.buttons) || []
          if (JSON.stringify(next) !== JSON.stringify(current)) {
            store.setItem('buttons', JSON.stringify(next))
            replace({ url: 'page/index' })
          }
        })
        .catch(() => {})
    },
  }),
)
