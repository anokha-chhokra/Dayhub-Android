import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

let seed = 111011;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const NOTES = ['lunch, with Ravi', '=HYPERLINK("x")', '+1 more', '-5 off', '@home', 'say "hi"', 'two\nlines', '\ttabbed', 'cr\rhere', null, 'plain', 'caf\u00e9 \u20b9 \u65e5\u672c', ' spaced ', ''];
const TEXTS = ['hello world', 'Line one\nLine two\n\nLine four', 'a "quoted" bit, with comma', '', 'emoji \ud83d\ude00 text', 'trailing space '];
const TAGS = [[], ['work'], ['work', 'family'], ['gratitude', 'x y']];
const NAMES = ['', 'Asha', 'Ravi "R" K', 'Zo\u00eb \u65e5\u672c'];
const ICONS = ['\ud83d\udca7', '\ud83c\udfcb\ufe0f', 'x'];
const TITLES = ['Drink water', 'Read | pages', 'Gym'];

const source = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const cases = [];
for (const [n, sc] of source.entries()) {
  const doc = sc.doc;
  doc.settings = { name: pick(NAMES), currency: 'INR' };
  for (const t of doc.tasks) t.createdAt = '2026-10-01T10:00:00.000Z';
  for (const e of doc.journal) { e.createdAt = '2026-10-01T11:00:00.000Z'; e.updatedAt = '2026-10-02T11:30:00.123Z'; }
  for (const e of doc.expenses) { e.note = pick(NOTES); if (e.note !== null && !e.note.trim()) e.note = null; }
  for (const e of doc.journal) { e.text = e.mood && rnd() < 0.2 ? '' : pick(TEXTS); if (!e.text && !e.mood) e.mood = 3; e.tags = pick(TAGS); e.wordCount = e.text.trim() ? e.text.trim().split(/\s+/).length : 0; }
  for (const h of doc.habits) { h.icon = pick(ICONS); h.title = pick(TITLES); h.unit = h.kind === 'check' ? '' : pick(['glasses', 'cups', '']); }
  const backend = createBackend({ storage: memoryStorage() });
  const call = async (path) => { const res = await backend.call('GET', path); if (res.status >= 400) throw new Error(path + ' ' + res.status); return res; };
  const imp = await backend.call('POST', '/api/import', JSON.parse(JSON.stringify(doc)));
  if (imp.status >= 400) throw new Error('import ' + JSON.stringify(imp.body));
  const today = pick(['2026-10-05', '2026-10-20', '2026-11-02']);
  const backup = (await call('/api/export')).body;
  cases.push({
    doc,
    today,
    backupText: `${JSON.stringify(backup, null, 2)}\n`,
    csvAll: (await call('/api/expenses.csv')).body,
    csvOct: (await call('/api/expenses.csv?month=2026-10')).body,
    csvNov: (await call('/api/expenses.csv?month=2026-11')).body,
    markdown: (await call(`/api/export.md?today=${today}`)).body,
  });
}
fs.writeFileSync(process.argv[3], JSON.stringify(cases));
console.log('cases', cases.length, 'csv bytes', cases.reduce((n, c) => n + c.csvAll.length, 0), 'md bytes', cases.reduce((n, c) => n + c.markdown.length, 0), 'json bytes', cases.reduce((n, c) => n + c.backupText.length, 0));
