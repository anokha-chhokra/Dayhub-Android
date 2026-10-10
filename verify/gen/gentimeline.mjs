import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { addDays } = await web('public/js/core/habits.js');
import fs from 'node:fs';

let seed = 20261010;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];
const TIMES = [null, null, '00:30', '04:59', '05:00', '07:15', '09:00', '11:59', '12:00', '13:10', '16:59', '17:00', '19:30', '20:59', '21:00', '22:45'];
const START = '2026-09-28';
const day = (n) => addDays(START, n);

const habitView = (h) => ({ id: h.id, title: h.title, remindAt: h.remindAt ?? null, scheduled: h.scheduled, value: h.value, done: h.done, pointsToday: h.pointsToday, loggedTime: h.loggedTime ?? null });
const projectDay = (t) => ({
  day: t.day,
  summary: t.summary,
  goals: t.goals.map(habitView),
  anytime: t.anytime.map(habitView),
  items: t.items.map((it) => ({
    type: it.type, time: it.time ?? null, part: it.part ?? null, pending: !!it.pending, missed: !!it.missed,
    ref: (it.habit || it.entry || it.expense || it.task).id,
  })),
});
const projectStats = (s) => ({
  totalPoints: s.totalPoints, todayPoints: s.todayPoints, totalEntries: s.totalEntries, totalWords: s.totalWords,
  habitDoneCount: s.habitDoneCount, perfectDays: s.perfectDays, streak: s.streak, longestStreak: s.longestStreak,
  nightOwl: s.nightOwl, earlyBird: s.earlyBird,
  badges: s.badges.map((b) => ({ id: b.id, unlockedOn: b.unlockedOn ?? null })),
});

const scenarios = [];
for (let n = 0; n < 70; n++) {
  const today = day(int(8, 30));
  const habits = [];
  const nh = int(0, 7);
  for (let id = 1; id <= nh; id++) {
    const kind = pick(['check', 'check', 'check', 'goal', 'limit']);
    habits.push({
      id, title: `Habit ${id}`, icon: 'x', kind, unit: '', target: kind === 'check' ? 1 : pick([2, 3, 8]), step: 1, points: int(1, 50),
      days: rnd() < 0.5 ? [0, 1, 2, 3, 4, 5, 6] : [0, 1, 2, 3, 4, 5, 6].filter(() => rnd() < 0.5).concat([int(0, 6)]).filter((v, i, a) => a.indexOf(v) === i),
      remindAt: kind === 'check' ? pick([null, null, '07:00', '08:30', '12:00', '19:30', '22:00']) : null,
      archived: rnd() < 0.2, createdOn: day(int(-5, 6)),
    });
  }
  const habitLogs = [];
  for (let k = 0; k < int(0, 70); k++) {
    const h = nh ? int(1, nh) : 1;
    const d = day(int(-3, 34));
    if (!habitLogs.some((l) => l.habitId === h && l.day === d)) habitLogs.push({ habitId: h, day: d, value: pick([1, 1, 2, 3, 5, 9]), time: pick(TIMES) });
  }
  const journal = [];
  for (let id = 1; id <= int(0, 14); id++) {
    const mood = pick([null, null, 1, 2, 3, 4, 5]);
    const text = mood && rnd() < 0.3 ? '' : pick(['hello world', 'a b', 'one two three four']);
    journal.push({ id, day: day(int(-3, 34)), time: pick(TIMES), text, mood, tags: [], wordCount: text ? text.split(' ').length : 0 });
  }
  const expenses = [];
  for (let id = 1; id <= int(0, 12); id++) {
    expenses.push({ id, amountMinor: int(100, 500000), category: pick(['Food', 'Bills', 'Other']), note: null, spentOn: day(int(-3, 34)), entryId: null, time: pick(TIMES) });
  }
  const tasks = [];
  for (let id = 1; id <= int(0, 12); id++) {
    const done = rnd() < 0.6;
    tasks.push({ id, title: `Task ${id}`, dueOn: null, priority: 0, done, doneOn: done ? day(int(-3, 34)) : null, doneTime: done ? pick(TIMES) : null });
  }
  const doc = { settings: { name: 'T' }, tasks, expenses, habits, habitLogs, journal, badges: {}, music: [] };

  const backend = createBackend({ storage: memoryStorage() });
  const call = async (m, p, b) => { const res = await backend.call(m, p, b); if (res.status >= 400) throw new Error(`${m} ${p} -> ${res.status} ${JSON.stringify(res.body)}`); return res.body; };
  await call('POST', '/api/import', JSON.parse(JSON.stringify(doc)));
  const queries = [];
  const days = [today, addDays(today, -1), day(int(0, 30)), day(int(0, 30)), day(int(0, 30)), addDays(today, 2)];
  for (const d of days) {
    const now = pick([null, '06:00', '12:00', '19:31', '23:59']);
    const q = `day=${d}&today=${today}${now ? `&now=${now}` : ''}`;
    queries.push({ kind: 'day', day: d, today, now, expected: projectDay(await call('GET', `/api/timeline?${q}`)) });
  }
  for (const ym of ['2026-09', '2026-10', '2026-11']) {
    const cal = await call('GET', `/api/calendar?month=${ym}`);
    queries.push({ kind: 'month', month: ym, expected: cal });
  }
  queries.push({ kind: 'stats', today, expected: projectStats(await call('GET', `/api/stats?today=${today}`)) });
  scenarios.push({ doc, queries });
}
fs.writeFileSync(process.argv[2], JSON.stringify(scenarios));
let items = 0, missed = 0, goals = 0, cells = 0;
for (const s of scenarios) for (const q of s.queries) {
  if (q.kind === 'day') { items += q.expected.items.length; missed += q.expected.summary.missed; goals += q.expected.goals.length; }
  if (q.kind === 'month') cells += Object.keys(q.expected.days).length;
}
console.log('scenarios', scenarios.length, 'timeline items', items, 'missed', missed, 'goal rows', goals, 'calendar cells', cells);
