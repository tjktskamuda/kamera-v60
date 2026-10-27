/* Kamera V60 — service worker untuk mode offline.
   Menyimpan aplikasi dan model AI Sinematik di HP saat pertama dibuka dengan internet. */
const CACHE = 'kv60-v2';
const CORE = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './icon-maskable-512.png'];
const MP = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14';
const AI = [
  MP + '/vision_bundle.mjs',
  MP + '/wasm/vision_wasm_internal.js',
  MP + '/wasm/vision_wasm_internal.wasm',
  'https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite',
];
const AI_HOSTS = ['cdn.jsdelivr.net', 'storage.googleapis.com'];

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await cache.addAll(CORE);
    // Model AI diunduh sebisanya; kalau gagal, nanti disimpan saat mode Sinematik dibuka.
    await Promise.allSettled(AI.map(u => cache.add(new Request(u, { mode: 'cors' }))));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) if (key !== CACHE) await caches.delete(key);
    await self.clients.claim();
  })());
});

self.addEventListener('message', event => {
  if (event.data === 'cache-ai') {
    event.waitUntil((async () => {
      const cache = await caches.open(CACHE);
      await Promise.allSettled(AI.map(async u => { if (!(await cache.match(u))) await cache.add(new Request(u, { mode: 'cors' })); }));
    })());
  }
});

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout')), ms);
    promise.then(r => { clearTimeout(t); resolve(r); }, e => { clearTimeout(t); reject(e); });
  });
}

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // File aplikasi: ambil versi terbaru dari internet bila ada, kalau tidak pakai simpanan.
  if (url.origin === self.location.origin) {
    event.respondWith((async () => {
      const cache = await caches.open(CACHE);
      try {
        const res = await withTimeout(fetch(req), 4000);
        if (res.ok) cache.put(req, res.clone());
        return res;
      } catch (e) {
        const hit = await cache.match(req, { ignoreSearch: true });
        if (hit) return hit;
        if (req.mode === 'navigate') return (await cache.match('./index.html')) || (await cache.match('./'));
        return Response.error();
      }
    })());
    return;
  }

  // File model AI: pakai simpanan dulu (tidak berubah), unduh hanya jika belum ada.
  if (AI_HOSTS.includes(url.host)) {
    event.respondWith((async () => {
      const cache = await caches.open(CACHE);
      const hit = await cache.match(req, { ignoreVary: true });
      if (hit) return hit;
      const res = await fetch(req);
      if (res.ok || res.type === 'opaque') cache.put(req, res.clone());
      return res;
    })());
  }
});
