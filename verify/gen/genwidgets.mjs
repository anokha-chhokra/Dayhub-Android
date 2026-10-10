import { web, readWeb } from './web.mjs';
// Runs the web app's own buildSnapshot (read out of native.js as text, its page helpers replaced by plain
// deterministic ones) over the real backend, for many generated data sets, and records what it produced.
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { addDays } = await web('public/js/core/habits.js');
import fs from 'node:fs';

const nativeSrc = readWeb('public/js/native.js');
const libSrc = readWeb('public/js/lib.js');
const attn = nativeSrc.match(/const attentionText = [\s\S]*?\n\};\n/)[0];
const build = nativeSrc.match(/export async function buildSnapshot\(\) \{[\s\S]*?\n\}\n/)[0].replace('export ', '');
const moods = libSrc.match(/export const MOODS = \[[\s\S]*?\n\];/)[0].replace('export ', '');
const factory = new Function('api', 'ymd', 'nowHHMM', 'money', 'dueLabel', 'greeting',
  `${moods}\n${attn}\n${build}\nreturn buildSnapshot;`);

let seed = 4242;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const source = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const scenarios = [];
let withAttention = 0, withTasks = 0, withHabits = 0, over = 0, budgeted = 0, moodSet = 0, manyTasks = 0;

for (const sc of source) {
  const doc = JSON.parse(JSON.stringify(sc.doc));
  const today = sc.queries.find((q) => q.kind === 'stats').today;
  doc.settings = {
    name: pick(['', 'Asha', 'Ravi']), currency: pick(['INR', 'USD', 'EUR']), monthlyBudgetMinor: pick([0, 0, 300000, 1000000, 5000000, 20000]),
    journalReminder: pick(['', '', '20:30', '07:00']), setupDone: true, notifications: false,
  };
  const nextId = doc.journal.reduce((n, e) => Math.max(n, e.id), 0) + 1;
  if (rnd() < 0.7) doc.journal.push({ id: nextId, day: today, time: pick(['19:00', '19:15', '07:10', '23:30']), text: '', mood: int(1, 5), tags: [], wordCount: 0, createdAt: '2026-10-01T10:00:00.000Z', updatedAt: '2026-10-01T10:00:00.000Z' });
  for (const t of doc.tasks) t.createdAt = '2026-10-01T10:00:00.000Z';
  for (const e of doc.journal) { e.createdAt ??= '2026-10-01T10:00:00.000Z'; e.updatedAt ??= '2026-10-01T10:00:00.000Z'; }

  if (scenarios.length % 5 === 0) {
    // Crowded: more open tasks and daily habits than any widget lists, so the "+N more" rows and the cut-offs are exercised.
    let tid = doc.tasks.reduce((n, t) => Math.max(n, t.id), 0);
    for (let i = 0; i < 22; i++) {
      tid++;
      doc.tasks.push({ id: tid, title: `Crowd task ${i + 1}`, dueOn: pick([null, today, addDays(today, -3), addDays(today, -1), addDays(today, 2)]), priority: pick([0, 1]), done: false, doneOn: null, doneTime: null, createdAt: '2026-10-01T10:00:00.000Z' });
    }
    let hid = doc.habits.reduce((n, h) => Math.max(n, h.id), 0);
    for (let i = 0; i < 20; i++) {
      hid++;
      const kind = pick(['check', 'goal', 'limit']);
      doc.habits.push({ id: hid, title: `Crowd habit ${i + 1}`, icon: pick(['x', '\u{1F4A7}']), kind, unit: kind === 'check' ? '' : pick(['', 'glasses', 'min']), target: kind === 'check' ? 1 : int(1, 8), step: int(1, 3), points: 10, days: [0, 1, 2, 3, 4, 5, 6], remindAt: null, archived: false, createdOn: '2026-09-01' });
      if (rnd() < 0.7) doc.habitLogs.push({ habitId: hid, day: today, value: int(1, 9), time: '08:00' });
    }
  }
  const backend = createBackend({ storage: memoryStorage() });
  const imp = await backend.call('POST', '/api/import', JSON.parse(JSON.stringify(doc)));
  if (imp.status >= 400) throw new Error('import ' + JSON.stringify(imp.body));

  const queries = [];
  for (const t of [today, addDays(today, -1), addDays(today, 3)]) {
    const now = pick(['07:30', '19:20', '19:45', '23:59', '12:00']);
    let current = 'INR';
    const api = async (path) => {
      const res = await backend.call('GET', `/api${path}`);
      if (res.status >= 400) throw new Error(JSON.stringify(res.body));
      current = res.body.spend.currency;
      return res.body;
    };
    const build = factory(
      api,
      () => t,
      () => now,
      (minor, currency) => `${currency || current}:${((minor || 0) / 100).toFixed(2)}`,
      (dueOn, today2) => `due(${dueOn}|${today2})`,
      (name) => `hi ${name || ''}`,
    );
    const snapshot = await build();
    delete snapshot.at;
    queries.push({ today: t, now, snapshot });
    if (snapshot.attention.length) withAttention++;
    if (snapshot.tasks.length) withTasks++;
    if (snapshot.tasks.length >= 15) manyTasks++;
    if (snapshot.habits.length) withHabits++;
    if (snapshot.spend.over) over++;
    if (snapshot.spend.hasBudget) budgeted++;
    if (snapshot.journal.mood) moodSet++;
  }
  scenarios.push({ doc, queries });
}
fs.writeFileSync(process.argv[3], JSON.stringify(scenarios));
console.log('scenarios', scenarios.length, 'snapshots', scenarios.length * 3, '| with attention', withAttention, 'tasks', withTasks, '15+ tasks', manyTasks,
  'habits', withHabits, 'budget', budgeted, 'over budget', over, 'mood', moodSet);
