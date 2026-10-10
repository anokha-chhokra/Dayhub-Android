import { web, readWeb } from './web.mjs';
const { spendPace, buildAttention, minutesBetween } = await web('public/js/core/insights.js');
const { addDays } = await web('public/js/core/habits.js');
import fs from 'node:fs';

let seed = 424242;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const paces = [];
const months = ['2026-02', '2024-02', '2026-10', '2026-12', '2026-01', '2025-04', '2026-07'];
for (let i = 0; i < 1500; i++) {
  const ym = pick(months);
  const r = rnd();
  let today;
  if (r < 0.75) today = `${ym}-${String(int(1, ym.endsWith('-02') ? (ym.startsWith('2024') ? 29 : 28) : 28)).padStart(2, '0')}`;
  else if (r < 0.88) today = addDays(`${ym}-01`, -int(1, 40));
  else today = addDays(`${ym}-28`, int(5, 60));
  const budget = pick([0, 0, 100000, 3000000, 3000001, 9999999, 123456789]);
  const total = pick([0, 1, 99999, 400000, 1000000, 3000000, 3100000, int(0, 8000000), int(0, 200000000)]);
  paces.push({ ym, today, totalMinor: total, budgetMinor: budget, expected: spendPace({ ym, today, totalMinor: total, budgetMinor: budget }) });
}

const norm = (x) => [x.type, x.id ?? null, x.title ?? null, x.dueOn ?? null, x.overdue ?? null, x.priority ?? null, x.icon ?? null, x.remindAt ?? null, x.overByMinor ?? null];
const attentions = [];
for (let i = 0; i < 1500; i++) {
  const today = addDays('2026-09-01', int(0, 80));
  const tasks = [];
  const nt = int(0, 7);
  for (let k = 0; k < nt; k++) {
    tasks.push({ id: k + 1, title: `t${k}`, dueOn: pick([null, null, today, addDays(today, -int(1, 45)), addDays(today, int(1, 10)), addDays(today, -1)]), priority: pick([0, 1]) });
  }
  const habits = [];
  const nh = int(0, 5);
  for (let k = 0; k < nh; k++) {
    habits.push({ id: 100 + k, title: `h${k}`, icon: pick(['water', 'walk']), kind: pick(['check', 'check', 'goal', 'limit']), done: rnd() < 0.3, scheduled: rnd() < 0.8, remindAt: pick([null, '', '06:30', '19:30', '23:00', '20:00', '12:00']) });
  }
  const now = pick([null, null, '00:00', '10:00', '19:59', '20:00', '20:01', '21:00', '23:59']);
  const entriesToday = pick([0, 0, 1, 3]);
  const journalReminder = pick(['', '', '20:30', '07:00', '23:00']);
  const status = pick(['ok', 'over', 'watch', 'none']);
  const spend = rnd() < 0.1 ? null : { status, overByMinor: int(1, 99999) };
  const limit = rnd() < 0.2 ? int(1, 8) : undefined;
  const args = { today, now, tasks, habits, entriesToday, journalReminder, spend };
  if (limit !== undefined) args.limit = limit;
  attentions.push({ today, now, tasks, habits, entriesToday, journalReminder, spend, limit: limit ?? null, expected: buildAttention(args).map(norm) });
}

const minutes = [];
for (const a of [null, '', '00:00', '07:30', '23:59', '7:30', '07:3', 'ab:cd', '25:61', '07:30 ']) {
  for (const b of [null, '', '00:00', '07:45', '23:59', '99:99']) {
    minutes.push({ a, b, expected: minutesBetween(a ?? undefined, b ?? undefined) });
  }
}

fs.writeFileSync(process.argv[2], JSON.stringify({ paces, attentions, minutes }));
const kinds = {};
for (const c of attentions) for (const it of c.expected) kinds[it[0]] = (kinds[it[0]] || 0) + 1;
const statuses = {};
for (const p of paces) statuses[p.expected.status] = (statuses[p.expected.status] || 0) + 1;
console.log('paces', paces.length, JSON.stringify(statuses), 'attentions', attentions.length, JSON.stringify(kinds));
