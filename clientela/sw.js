// Clientela se movió a https://jeal1498.github.io/PWA-Clientela/ ; este SW viejo se borra a sí mismo y limpia su caché.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k.startsWith('clientela-')) await caches.delete(k);
    await self.registration.unregister();
  })());
});
