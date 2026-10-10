import { web, readWeb } from './web.mjs';
// Runs the web app's own reminder check (read out of reminders.js as text) on its real backend, for many data sets,
// days and clock times, and records which reminders it shows (title and body, in order).
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { addDays } = await web('public/js/core/habits.js');
import fs from 'node:fs';

const src = readWeb('public/js/reminders.js');
const checkSrc = src.match(/async function check\(\) \{[\s\S]*?\n\}\n/)[0];
const factory = new Function('api', 'app', 'toast', 'ymd', 'nowHHMM', 'fired', 'window', 'Notification', `${checkSrc}\nreturn check;`);

let seed = 2929;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];
const TIMES = [null, null, '06:30', '07:00', '08:15', '12:00', '18:30', '20:00', '23:59', '00:00', '00:05'];

const minus = (t, m) => { const [h, mi] = t.split(':').map(Number); const v = Math.max(0, Math.min(1439, h * 60 + mi + m)); return `${String(Math.floor(v / 60)).padStart(2, '0')}:${String(v % 60).padStart(2, '0')}`; };

const source = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const scenarios = [];
let withJournal = 0, withHabit = 0, dueTotal = 0, empty = 0;
for (const sc of source) {
  const doc = JSON.parse(JSON.stringify(sc.doc));
  const today = sc.queries.find((q) => q.kind === 'stats').today;
  doc.settings = { name: 'A', currency: 'INR', monthlyBudgetMinor: 0, journalReminder: pick(['', '', '07:45', '20:30', '21:00']), setupDone: true, notifications: rnd() < 0.7 };
  for (const h of doc.habits) h.remindAt = pick(TIMES);
  for (const t of doc.tasks) t.createdAt = '2026-10-01T10:00:00.000Z';
  for (const e of doc.journal) { e.createdAt ??= '2026-10-01T10:00:00.000Z'; e.updatedAt ??= '2026-10-01T10:00:00.000Z'; }
  const backend = createBackend({ storage: memoryStorage() });
  const imp = await backend.call('POST', '/api/import', JSON.parse(JSON.stringify(doc)));
  if (imp.status >= 400) throw new Error('import ' + JSON.stringify(imp.body));

  const queries = [];
  for (const day of [today, addDays(today, -1), addDays(today, 3)]) {
    const times = new Set(['00:00', '23:59', pick(['06:00', '12:30', '19:00'])]);
    for (const t of [doc.settings.journalReminder, ...doc.habits.map((h) => h.remindAt)].filter(Boolean)) { times.add(t); times.add(minus(t, -1)); times.add(minus(t, 1)); }
    for (const now of [...times].slice(0, 14)) {
      const shown = [];
      class FakeNotification {
        constructor(title, opts) { shown.push([title, opts.body]); }
      }
      FakeNotification.permission = 'granted';
      const api = async (path) => {
        const res = await backend.call('GET', `/api${path}`);
        if (res.status >= 400) throw new Error(JSON.stringify(res.body));
        return res.body;
      };
      const check = factory(api, { settings: { ...doc.settings, notifications: true } }, () => {}, () => day, () => now, new Set(), { Notification: FakeNotification }, FakeNotification);
      await check();
      queries.push({ day, now, shown });
      dueTotal += shown.length;
      if (!shown.length) empty++;
      if (shown.some((s) => s[0] === 'Time to write')) withJournal++;
      if (shown.some((s) => s[1] === 'Reminder from Day Hub')) withHabit++;
    }
  }
  scenarios.push({ doc, queries });
}
fs.writeFileSync(process.argv[3], JSON.stringify(scenarios));
console.log('scenarios', scenarios.length, 'checks', scenarios.reduce((n, s) => n + s.queries.length, 0), '| reminders shown', dueTotal, 'journal', withJournal, 'habit', withHabit, 'nothing due', empty);
