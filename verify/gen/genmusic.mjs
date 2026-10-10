import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

let seed = 180018;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const VIDEOS = ['dQw4w9WgXcQ', 'aaaaaaaaaaa', 'bbbbbbbbbbb', 'ccccccccccc', 'ddddddddddd'];
const LISTS = ['PLabcdefghij1234567', 'PLzzzzzzzzzzzzzzzzzz', 'PLshort'];
const urls = [];
for (const v of VIDEOS) {
  urls.push(`https://www.youtube.com/watch?v=${v}`, `https://youtu.be/${v}?t=3`, `https://music.youtube.com/watch?v=${v}`, `https://www.youtube.com/shorts/${v}`, `https://m.youtube.com/embed/${v}`, `https://www.youtube.com/watch?v=${v}&list=${LISTS[0]}`);
}
for (const l of LISTS) urls.push(`https://www.youtube.com/playlist?list=${l}`);
const BAD = ['', '   ', 'hello', 'javascript:alert(1)', 'https://evil.example/watch?v=dQw4w9WgXcQ', 'https://www.youtube.com/watch?v=short', 'https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ', 'ftp://youtu.be/dQw4w9WgXcQ', 'spotify:track:37i9dQZF1DXcBWIGoYBM5M', 'https://www.youtube.com/channel/UC12345', 'https://www.youtube.com/', null, 5, undefined];
const LABELS = [undefined, undefined, null, '', '  My mix  ', 'x'.repeat(60), 'x'.repeat(61), 5, 'caf\u00e9 \u65e5\u672c', 'two\nlines'];

const project = (l) => ({ id: l.id, provider: l.provider, kind: l.kind, url: l.url, embedUrl: l.embedUrl, label: l.label });
const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => backend.call(m, p, b === undefined ? undefined : JSON.parse(JSON.stringify(b)));
const ops = [];
let ids = [];
for (let i = 0; i < 500; i++) {
  const r = rnd();
  let op;
  if (r < 0.5 || !ids.length) {
    const body = { url: rnd() < 0.85 ? pick(urls) : pick(BAD) };
    if (rnd() < 0.5) body.label = pick(LABELS);
    if (rnd() < 0.3) body.makeCurrent = pick([false, true, 0, null, 'no']);
    op = { kind: 'add', body };
  } else if (r < 0.8) {
    const id = rnd() < 0.15 ? pick([null, undefined, 0, -1, 'x', 9999, '3', 1.5]) : pick(ids);
    op = { kind: 'current', body: id === undefined ? {} : { id } };
  } else {
    op = { kind: 'delete', id: rnd() < 0.12 ? pick([9999, 0]) : pick(ids) };
  }
  let res;
  if (op.kind === 'add') res = await call('POST', '/api/music', op.body);
  else if (op.kind === 'current') res = await call('PUT', '/api/music/current', op.body);
  else res = await call('DELETE', `/api/music/${op.id}`);
  op.status = res.status;
  op.error = res.status >= 400 ? res.body.error : null;
  if (op.kind === 'add' && res.status === 201 && !ids.includes(res.body.link.id)) ids.push(res.body.link.id);
  if (op.kind === 'delete' && res.status === 204) ids = ids.filter((x) => x !== op.id);
  const music = (await call('GET', '/api/music')).body;
  const settings = (await call('GET', '/api/settings')).body;
  op.after = { links: music.links.map(project), current: music.current ? music.current.id : null, settingsCurrent: settings.currentMusicId ?? null };
  ops.push(op);
}
fs.writeFileSync(process.argv[2], JSON.stringify({ ops }));
const ok = ops.filter((o) => o.status < 400).length;
const errs = [...new Set(ops.filter((o) => o.status >= 400).map((o) => o.error))];
console.log('ops', ops.length, 'accepted', ok, 'refused', ops.length - ok, 'distinct errors', errs.length, 'max saved', Math.max(...ops.map((o) => o.after.links.length)));
