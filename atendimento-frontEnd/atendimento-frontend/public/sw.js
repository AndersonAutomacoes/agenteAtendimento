/* AxeZap — service worker básico: reforça carregamento de assets estáticos em redes instáveis */
const CACHE = "axezap-static-v1";

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim()),
  );
});

function isStaticAsset(url) {
  if (url.pathname.startsWith("/_next/static")) return true;
  if (url.pathname.startsWith("/icons/")) return true;
  return /\.(?:js|css|woff2|png|svg|ico|webmanifest)$/i.test(url.pathname);
}

/** stale-while-revalidate — resposta rápida do cache + atualização em segundo plano */
async function staleWhileRevalidate(request) {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(request);
  const networkPromise = fetch(request).then((response) => {
    if (response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  });
  return cached || networkPromise;
}

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;
  if (request.mode === "navigate") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api")) return;
  if (!isStaticAsset(url)) return;
  event.respondWith(
    staleWhileRevalidate(request).catch(() => caches.match(request)),
  );
});
