import tailwindcss from '@tailwindcss/vite'
import ui from '@nuxt/ui/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    ui({
      ui: {
        colors: { primary: 'sky', neutral: 'slate' },
        card: { slots: { root: 'rounded-2xl ring-0 border border-default shadow-sm dark:shadow-none dark:bg-elevated/40' } },
        // Cap the sheet height so long content scrolls, with the footer (Save) pinned at the bottom
        drawer: {
          slots: {
            content: 'rounded-t-3xl max-h-[92dvh]',
            title: 'text-lg font-extrabold',
            footer: 'sticky -bottom-4 -mx-4 border-t border-default bg-default px-4 pt-3 pb-[max(1rem,env(safe-area-inset-bottom))]',
          },
        },
        modal: { slots: { content: 'rounded-2xl', title: 'text-lg font-extrabold' } },
        slideover: { slots: { title: 'text-lg font-extrabold' } },
      },
      // Bundle the icons we use so the PWA works offline instead of fetching from the Iconify API.
      icon: { clientBundle: { scan: true } },
    }),
    VitePWA({
      registerType: 'autoUpdate',
      // Custom service worker for push notifications (src/sw.ts)
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      manifest: {
        name: 'Conta Hockey',
        short_name: 'Conta Hockey',
        theme_color: '#0f172a',
        background_color: '#0f172a',
        display: 'standalone',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml' },
        ],
      },
    }),
  ],
  server: { proxy: { '^/(api|oauth|login|logout)': 'http://localhost:8080' } },
})
