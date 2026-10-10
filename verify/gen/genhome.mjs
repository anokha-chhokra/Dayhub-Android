import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { addDays } = await web('public/js/core/habits.js');
const { promptForDay, PROMPTS, TAG_SUGGESTIONS } = await web('public/js/core/prompts.js');
import fs from 'node:fs';

let seed = 1313;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const source = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const projectHabit = (h) => ({ id: h.id, scheduled: h.scheduled, value: h.value, done: h.done, pointsToday: h.pointsToday });
const norm = (x) => [x.type, x.id ?? null, x.title ?? null, x.dueOn ?? null, x.overdue ?? null, x.priority ?? null, x.icon ?? null, x.remindAt ?? null, x.overByMinor ?? null];
const project = (d) => ({
  today: d.today,
  name: d.name,
  attention: d.attention.map(norm),
  tasks: { ids: d.tasks.list.map((t) => t.id), done: d.tasks.list.map((t) => t.done), total: d.tasks.total, doneToday: d.tasks.doneToday },
  spend: d.spend,
  habits: { list: d.habits.list.map(projectHabit), doneCount: d.habits.doneCount, total: d.habits.total },
  stats: d.stats,
  journal: { promptId: d.journal.prompt.id, todayCount: d.journal.todayCount, latestMood: d.journal.latestMood ?? null, quickMoodId: d.journal.quickMoodId ?? null },
  musicId: d.music.current ? d.music.current.id : null,
});

const scenarios = [];
for (const sc of source) {
  const doc = JSON.parse(JSON.stringify(sc.doc));
  const today = sc.queries.find((q) => q.kind === 'stats').today;
  doc.settings = {
    name: pick(['', 'Asha', 'Ravi']), currency: pick(['INR', 'USD', 'EUR']), monthlyBudgetMinor: pick([0, 0, 300000, 1000000, 5000000]),
    journalReminder: pick(['', '', '20:30', '07:00']), setupDone: true, notifications: false,
  };
  if (rnd() < 0.6) {
    doc.music = [{ id: 1, provider: 'youtube', kind: 'video', url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', embedUrl: 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', label: 'Song' }];
    if (rnd() < 0.8) doc.settings.currentMusicId = 1;
  }
  // Mood-only check-ins close to "now" so the quick-mood window is exercised.
  const nowChoices = [null, '07:30', '19:20', '19:45', '23:59'];
  const nextId = doc.journal.reduce((n, e) => Math.max(n, e.id), 0) + 1;
  if (rnd() < 0.7) doc.journal.push({ id: nextId, day: today, time: pick(['19:00', '19:15', '07:10', '23:30']), text: '', mood: int(1, 5), tags: [], wordCount: 0, createdAt: '2026-10-01T10:00:00.000Z', updatedAt: '2026-10-01T10:00:00.000Z' });
  for (const t of doc.tasks) t.createdAt = '2026-10-01T10:00:00.000Z';
  for (const e of doc.journal) { e.createdAt ??= '2026-10-01T10:00:00.000Z'; e.updatedAt ??= '2026-10-01T10:00:00.000Z'; }

  const backend = createBackend({ storage: memoryStorage() });
  const imp = await backend.call('POST', '/api/import', JSON.parse(JSON.stringify(doc)));
  if (imp.status >= 400) throw new Error('import ' + JSON.stringify(imp.body));
  const queries = [];
  for (const t of [today, addDays(today, -1), addDays(today, 3)]) {
    const now = pick(nowChoices);
    const res = await backend.call('GET', `/api/dashboard?today=${t}${now ? `&now=${now}` : ''}`);
    if (res.status >= 400) throw new Error(JSON.stringify(res.body));
    queries.push({ today: t, now, expected: project(res.body) });
  }
  scenarios.push({ doc, queries });
}

const prompts = [];
for (let i = 0; i < 500; i++) {
  const d = addDays('2024-01-01', int(0, 1500));
  prompts.push({ day: d, id: promptForDay(d).id });
}
fs.writeFileSync(process.argv[3], JSON.stringify({ scenarios, prompts, promptList: PROMPTS, tags: TAG_SUGGESTIONS }));
let quick = 0, mood = 0, music = 0, att = 0;
for (const s of scenarios) for (const q of s.queries) { if (q.expected.journal.quickMoodId) quick++; if (q.expected.journal.latestMood) mood++; if (q.expected.musicId) music++; att += q.expected.attention.length; }
console.log('scenarios', scenarios.length, 'dashboards', scenarios.length * 3, 'quickMood', quick, 'latestMood', mood, 'withMusic', music, 'attention items', att);
