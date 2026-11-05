/* Stüdyo çevrimdışı önbelleği. Yalnız kendi önbelleğini (st-) siler; aynı adresteki diğer uygulamalara dokunmaz. */
const P='st-',V=P+'%SURUM%';
const SHELL=['./','manifest.webmanifest','icon.svg','icon-192.png','icon-512.png','icon-maskable.png','foto/','foto/icon-192.png','video/','video/icon-192.png?v=2'];
const anahtar=u=>{const x=new URL(u);let p=x.pathname;if(p.endsWith('/'))p+='index.html';return x.origin+p;};
self.addEventListener('install',e=>{e.waitUntil(caches.open(V).then(c=>Promise.all(SHELL.map(s=>fetch(s,{cache:'no-cache'}).then(r=>{if(r.ok)return c.put(anahtar(new URL(s,self.registration.scope).href),r)}).catch(()=>{})))).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k.indexOf(P)===0&&k!==V).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{
  const r=e.request;if(r.method!=='GET')return;
  const u=new URL(r.url);if(u.origin!==location.origin)return;   /* yazı tipleri vb. dış adresler: tarayıcı kendi önbelleği */
  if(u.pathname.indexOf('/yz/')>=0)return;   /* yapay zekâ modeli ve motoru: sayfa kendi önbelleğinde (yz-) tutar, burada kopyalanmaz */
  if(r.mode==='navigate'||/\.html$/.test(u.pathname)){
    /* önce ağ (güncelleme gelsin); 3 sn'de yanıt yoksa önbellek → internetsiz açılış takılmaz */
    const k=anahtar(r.url);
    const net=fetch(r.url,{cache:'no-cache',credentials:'same-origin'}).then(res=>{if(res&&res.ok){const c=res.clone();caches.open(V).then(x=>x.put(k,c))}return res});
    e.respondWith(Promise.race([net,new Promise((_,rej)=>setTimeout(rej,3000))]).catch(()=>caches.open(V).then(c=>c.match(k)).then(h=>h||net)));
    return;
  }
  e.respondWith(caches.open(V).then(c=>c.match(r).then(hit=>{
    const net=fetch(r).then(res=>{if(res&&res.ok)c.put(r,res.clone());return res}).catch(()=>hit);
    return hit||net;
  })));
});
