/// <reference lib="webworker" />
import { clientsClaim } from 'workbox-core'
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'

declare let self: ServiceWorkerGlobalScope

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()
// Serve the app shell offline, but let sign-in and API navigations reach the server
registerRoute(new NavigationRoute(createHandlerBoundToURL('index.html'), { denylist: [/^\/(api|oauth|login|logout)/] }))
self.skipWaiting()
clientsClaim()

self.addEventListener('push', (event) => {
  const { title, body, url } = event.data?.json() ?? {}
  event.waitUntil(self.registration.showNotification(title ?? 'Conta Hockey', { body, icon: '/icon-192.png', data: { url } }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.url ?? '/'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows[0]
      return open ? open.focus().then((client) => client.navigate(url)) : self.clients.openWindow(url)
    }),
  )
})
