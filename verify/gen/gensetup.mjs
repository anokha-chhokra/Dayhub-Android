import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { PRESETS } = await web('public/js/core/habits.js');
import fs from 'node:fs';

let seed = 5150;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const ids = PRESETS.map((p) => p.id);
const YT = ['https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'https://youtu.be/dQw4w9WgXcQ', 'https://www.youtube.com/playlist?list=PLabcdefghij1234567', 'https://example.com/x', 'hello', '   ', '', null, 5];
const bodies = [
  {}, { name: 'Sam' }, { name: '' }, { name: '   ' }, { name: 'x'.repeat(41) }, { name: 5 }, { name: null },
  { name: 'Asha', currency: 'inr', monthlyBudget: '30,000', musicUrl: YT[0] },
  { name: 'Asha', currency: 'XXXX' }, { name: 'Asha', currency: null }, { name: 'Asha', currency: 'us' },
  { name: 'Asha', musicUrl: 'https://example.com/x' },
  { name: 'Asha', habits: ['nope'] }, { name: 'Asha', habits: ['water', 7] }, { name: 'Asha', habits: 'water' }, { name: 'Asha', habits: null },
  { name: 'Asha', habits: ids, today: '2026-10-05' }, { name: 'Asha', habits: ['water', 'water'], today: '2026-10-05' },
  { monthlyBudget: 0 }, { monthlyBudget: '' }, { monthlyBudget: null }, { monthlyBudget: -5 }, { monthlyBudget: 'abc' }, { monthlyBudget: '1000.5' }, { monthlyBudget: 12345.67 },
  { name: 'Z', habits: ['coffee'], today: 'not a date' },
  { name: 'Z', habits: ['steps', 'screen'], today: '2026-02-30' },
];
for (let i = 0; i < 160; i++) {
  const b = {};
  if (rnd() < 0.85) b.name = pick(['Asha', 'Ravi', '  Zoe  ', 'A\tB', 'J\u00fcrgen', '']);
  if (rnd() < 0.6) b.currency = pick(['inr', 'USD', 'eur', 'GBP', 'aed', 'sgd', 'zzz']);
  if (rnd() < 0.6) b.monthlyBudget = pick(['30000', '1,500.50', '', 0, 250, '0', '99999999999', 'x', null]);
  if (rnd() < 0.5) b.musicUrl = pick(YT);
  if (rnd() < 0.7) b.habits = ids.filter(() => rnd() < 0.4).concat(rnd() < 0.05 ? ['bogus'] : []);
  if (rnd() < 0.7) b.today = pick(['2026-10-05', '2026-12-31', '2026-02-28', 'bad']);
  bodies.push(b);
}

const norm = (doc) => { const c = JSON.parse(JSON.stringify(doc)); delete c.exportedAt; return c; };
const cases = [];
for (const body of bodies) {
  const backend = createBackend({ storage: memoryStorage() });
  const res = await backend.call('POST', '/api/setup', JSON.parse(JSON.stringify(body)));
  const after = (await backend.call('GET', '/api/export')).body;
  cases.push({ body, status: res.status, response: res.body, after: norm(after) });
}
fs.writeFileSync(process.argv[2], JSON.stringify({ cases, presets: PRESETS }));
const ok = cases.filter((c) => c.status === 200).length;
const errors = [...new Set(cases.filter((c) => c.status !== 200).map((c) => c.response.error))];
console.log('cases', cases.length, 'ok', ok, 'errors', cases.length - ok, 'distinct messages', errors.length);
