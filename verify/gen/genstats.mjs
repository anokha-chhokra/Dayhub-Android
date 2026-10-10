import { web, readWeb } from './web.mjs';
const { computeStats, earnedBadgeIds, dayPoints, isDone, isOk, isActivity, isScheduled, addDays, BADGES } = await web('public/js/core/habits.js');
import fs from 'node:fs';

let seed = 12345;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];
const START = '2026-09-01';
const day = (n) => addDays(START, n);

const scenarios = [];
for (let i = 0; i < 400; i++) {
  const nh = int(0, 6);
  const habits = [];
  for (let id = 1; id <= nh; id++) {
    const kind = pick(['check', 'goal', 'limit']);
    const everyday = rnd() < 0.5;
    const days = everyday ? [0, 1, 2, 3, 4, 5, 6] : [0, 1, 2, 3, 4, 5, 6].filter(() => rnd() < 0.5);
    habits.push({
      id, kind, title: `h${id}`,
      target: kind === 'check' ? 1 : pick([1, 2, 3, 5, 8, 100, 8000]),
      points: int(1, 100),
      days: days.length ? days : [int(0, 6)],
      createdOn: day(int(0, 20)),
      archived: rnd() < 0.15,
    });
  }
  const today = day(int(10, 55));
  const logs = [];
  const nl = int(0, 90);
  for (let k = 0; k < nl; k++) {
    const h = nh && rnd() < 0.93 ? int(1, nh) : 99; // sometimes a log for an unknown habit
    const d = rnd() < 0.05 ? addDays(today, int(1, 3)) : day(int(0, 55));
    const hb = habits.find((x) => x.id === h);
    const v = hb && hb.kind === 'check' ? 1 : pick([1, 1, 2, 3, 4, 5, 9, 50, 8000, 12000]);
    if (!logs.some((l) => l.habitId === h && l.day === d)) logs.push({ habitId: h, day: d, value: v });
  }
  const entries = [];
  const ne = pick([0, 0, 1, 2, 5, 12]);
  for (let k = 0; k < ne; k++) {
    entries.push({ day: day(int(0, 55)), time: rnd() < 0.2 ? null : pick(['05:30', '06:59', '07:00', '12:00', '20:59', '21:00', '23:45']), wordCount: pick([0, 3, 40, 1200, 9000]) });
  }
  const stats = computeStats({ habits, logs, entries, today });
  scenarios.push({ habits, logs, entries, today, expected: { ...stats, badges: earnedBadgeIds(stats) } });
}

const probes = [];
for (const kind of ['check', 'goal', 'limit']) {
  for (const target of [1, 3, 8, 8000]) {
    for (const points of [1, 5, 10, 33, 100]) {
      const habit = { id: 1, kind, target: kind === 'check' ? 1 : target, points, days: [1, 3], createdOn: '2026-09-10' };
      for (const value of [null, -2, 0, 1, 2, 3, 4, 7, 8, 9, 100, 4000, 8000, 9999]) {
        probes.push({ habit, value, expected: { points: dayPoints(habit, value ?? undefined), done: isDone(habit, value ?? undefined), ok: isOk(habit, value ?? undefined), activity: isActivity(habit, value ?? undefined) } });
      }
      for (const d of ['2026-09-09', '2026-09-10', '2026-09-14', '2026-09-16', '2026-10-05']) {
        probes.push({ habit, day: d, expected: { scheduled: isScheduled(habit, d) } });
      }
    }
  }
}
fs.writeFileSync(process.argv[2], JSON.stringify({ scenarios, probes, badges: BADGES.map(({ id, icon, name, desc }) => ({ id, icon, name, desc })) }));
const earned = new Set(scenarios.flatMap((s) => s.expected.badges));
console.log('scenarios', scenarios.length, 'probes', probes.length, 'distinct badges seen', earned.size, 'streak>=7 cases', scenarios.filter((s) => s.expected.longestStreak >= 7).length, 'perfect-day cases', scenarios.filter((s) => s.expected.perfectDays > 0).length);
