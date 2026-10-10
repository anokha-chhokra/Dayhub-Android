import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { addDays } = await web('public/js/core/habits.js');
import fs from 'node:fs';

let seed = 150015;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const TODAY = '2026-10-08';
const TITLES = ['Water', '  Run  ', '', '   ', 'x'.repeat(60), 'x'.repeat(61), 5, null, 'caf\u00e9 \u65e5\u672c', 'a\nb'];
const KINDS = ['check', 'goal', 'limit', 'weird', '', null, undefined];
const ICONS = ['\ud83d\udca7', 'x', '', null, '123456789', '\ud83c\udfcb\ufe0f'];
const NUMS = [1, 2, 3, 8, 100, 8000, '5', '', null, 0, -1, 1.5, 'abc', 1000001];
const DAYSETS = [undefined, null, [0, 1, 2, 3, 4, 5, 6], [1, 3, 5], [], [7], ['2'], [6, 0], 'mon', [1, 1, 2]];
const REMIND = [undefined, null, '', '07:30', '23:59', '24:00', '7:30'];

const projectHabit = (h) => ({ id: h.id, title: h.title, icon: h.icon, kind: h.kind, unit: h.unit, target: h.target, step: h.step, points: h.points, days: h.days, remindAt: h.remindAt ?? null, archived: !!h.archived });
const projectDay = (h) => ({ id: h.id, scheduled: h.scheduled, value: h.value, done: h.done, pointsToday: h.pointsToday });

const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => backend.call(m, p, b === undefined ? undefined : JSON.parse(JSON.stringify(b)));
const ops = [];
let ids = [];
for (let i = 0; i < 600; i++) {
  const r = rnd();
  let op;
  if (r < 0.3 || !ids.length) {
    const body = { today: pick([TODAY, '2026-10-01', '2026-10-06']) };
    if (rnd() < 0.9) body.title = pick(TITLES);
    if (rnd() < 0.6) body.kind = pick(KINDS);
    if (rnd() < 0.5) body.icon = pick(ICONS);
    if (rnd() < 0.5) body.target = pick(NUMS);
    if (rnd() < 0.4) body.unit = pick(['glasses', '', null, 'x'.repeat(21)]);
    if (rnd() < 0.4) body.step = pick(NUMS);
    if (rnd() < 0.4) body.points = pick(NUMS);
    if (rnd() < 0.5) body.days = pick(DAYSETS);
    if (rnd() < 0.4) body.remindAt = pick(REMIND);
    op = { kind: 'create', body };
  } else if (r < 0.5) {
    const body = { today: TODAY };
    if (rnd() < 0.4) body.title = pick(TITLES);
    if (rnd() < 0.3) body.kind = pick(KINDS);
    if (rnd() < 0.3) body.target = pick(NUMS);
    if (rnd() < 0.3) body.step = pick(NUMS);
    if (rnd() < 0.3) body.points = pick(NUMS);
    if (rnd() < 0.3) body.unit = pick(['cups', '', null]);
    if (rnd() < 0.3) body.days = pick(DAYSETS);
    if (rnd() < 0.3) body.remindAt = pick(REMIND);
    op = { kind: 'patch', id: rnd() < 0.06 ? 9999 : pick(ids), body };
  } else if (r < 0.9) {
    const day = rnd() < 0.1 ? addDays(TODAY, int(1, 3)) : addDays(TODAY, -int(0, 8));
    const body = { day: rnd() < 0.05 ? 'bad' : day, value: pick([0, 1, 2, 3, 5, 9, 50, -1, '2', 1.5, null, 1000001]), today: TODAY };
    if (rnd() < 0.5) body.time = pick(['08:00', '', '25:00']);
    op = { kind: 'log', id: rnd() < 0.06 ? 9999 : pick(ids), body };
  } else {
    op = { kind: 'archive', id: rnd() < 0.1 ? 9999 : pick(ids) };
  }
  let res;
  if (op.kind === 'create') res = await call('POST', '/api/habits', op.body);
  else if (op.kind === 'patch') res = await call('PATCH', `/api/habits/${op.id}`, op.body);
  else if (op.kind === 'log') res = await call('PUT', `/api/habits/${op.id}/log`, op.body);
  else res = await call('DELETE', `/api/habits/${op.id}`);
  op.status = res.status;
  op.error = res.status >= 400 ? res.body.error : null;
  if (op.kind === 'create' && res.status === 201) ids.push(res.body.id);
  if (op.kind === 'log' && res.status === 200) op.newBadges = res.body.newBadges.map((b) => b.id);
  if (op.kind === 'log' && res.status === 200) op.logResult = { habit: projectDay(res.body.habit), stats: res.body.stats };
  const all = (await call('GET', '/api/export')).body.habits.map(projectHabit);
  const logs = (await call('GET', '/api/export')).body.habitLogs;
  const week = [];
  for (let k = 6; k >= 0; k--) {
    const day = addDays(TODAY, -k);
    const list = (await call('GET', `/api/habits?today=${day}`)).body.habits;
    week.push({ day, list: list.map(projectDay) });
  }
  const cal = (await call('GET', '/api/calendar?month=2026-10')).body.days;
  const stats = (await call('GET', `/api/stats?today=${TODAY}`)).body;
  op.after = { habits: all, logs, week, cal, stats: { totalPoints: stats.totalPoints, todayPoints: stats.todayPoints, streak: stats.streak, longestStreak: stats.longestStreak, badges: stats.badges.map((b) => [b.id, b.unlockedOn ?? null]) } };
  ops.push(op);
}
fs.writeFileSync(process.argv[2], JSON.stringify({ today: TODAY, ops }));
const ok = ops.filter((o) => o.status < 400).length;
const errs = [...new Set(ops.filter((o) => o.status >= 400).map((o) => o.error))];
console.log('ops', ops.length, 'accepted', ok, 'refused', ops.length - ok, 'distinct errors', errs.length, 'habits', ops.at(-1).after.habits.length, 'logs', ops.at(-1).after.logs.length);
