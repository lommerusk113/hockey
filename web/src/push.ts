import { api } from './api'

export type PushState = 'unsupported' | 'denied' | 'on' | 'off'

const supported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

// The VAPID public key is base64url; PushManager wants raw bytes.
function keyBytes(base64url: string) {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) => c.charCodeAt(0))
}

export async function pushState(): Promise<PushState> {
  if (!supported()) return 'unsupported'
  if (Notification.permission === 'denied') return 'denied'
  const registration = await navigator.serviceWorker.getRegistration()
  return (await registration?.pushManager.getSubscription()) ? 'on' : 'off'
}

export async function enablePush() {
  if ((await Notification.requestPermission()) !== 'granted') {
    throw new Error('Notifications are blocked. Allow them in your browser settings.')
  }
  const registration = await navigator.serviceWorker.ready
  const { publicKey } = await api.pushKey()
  const subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) })
  await api.subscribePush(subscription.toJSON())
}

export async function disablePush() {
  const registration = await navigator.serviceWorker.getRegistration()
  const subscription = await registration?.pushManager.getSubscription()
  if (subscription) {
    await api.unsubscribePush(subscription.endpoint)
    await subscription.unsubscribe()
  }
}
