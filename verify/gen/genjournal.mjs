import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
const { addDays } = await web('public/js/core/habits.js');
import fs from 'node:fs';

let seed = 160016;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const TODAY = '2026-10-08';
const TEXTS = ['Good day.\nSpent 250 on lunch', 'one two three', '', '   ', 'line1\r\nline2', 'x'.repeat(10000), 'x'.repeat(10001), null, 7, 'tab\there', '\u0007bell', 'caf\u00e9 \u65e5\u672c', 'Spent \u20b9300 on dinner yesterday'];
const MOODS = [undefined, null, '', 1, 3, 5, 0, 6, '4', 2.5];
const TAGSETS = [undefined, null, [], ['work'], ['Work', 'family', '#x'], ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'], 'work', ['x'.repeat(25)], [5]];
const DAYS = [undefined, null, '', TODAY, addDays(TODAY, -1), addDays(TODAY, -3), addDays(TODAY, 1), 'bad', '2026-02-30'];
const TIMES = [undefined, null, '', '07:30', '13:10', '23:59', '25:00', 'ab'];
const PROMPTS = [undefined, null, '', 'gratitude', 'growth', 'nope'];
const EXPENSES = [
  undefined, null, [],
  [{ amount: 250, category: 'Food', note: 'lunch' }],
  [{ amount: '99.50', note: 'phone', daysAgo: 2 }],
  [{ amount: 10 }, { amount: 20, category: 'Bills' }],
  [{ amount: -1 }], [{ amount: 5, category: 'Pets' }], [{ amount: 5, daysAgo: 31 }], [7], 'x',
  [1, 1, 1, 1, 1, 1].map((n) => ({ amount: n })),
];

const projectEntry = (e) => ({ id: e.id, day: e.day, time: e.time ?? null, text: e.text, mood: e.mood ?? null, tags: e.tags, promptId: e.promptId ?? null, wordCount: e.wordCount });
const projectExpense = (e) => ({ id: e.id, amountMinor: e.amountMinor, category: e.category, note: e.note ?? null, spentOn: e.spentOn, entryId: e.entryId ?? null, time: e.time ?? null });
const projectItem = (it) => ({ type: it.type, time: it.time ?? null, part: it.part ?? null, ref: (it.habit || it.entry || it.expense || it.task).id });

const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => backend.call(m, p, b === undefined ? undefined : JSON.parse(JSON.stringify(b)));
const ops = [];
let ids = [];
for (let i = 0; i < 700; i++) {
  const r = rnd();
  let op;
  if (r < 0.45 || !ids.length) {
    const body = { today: TODAY };
    for (const [k, vals] of [['day', DAYS], ['time', TIMES], ['text', TEXTS], ['mood', MOODS], ['tags', TAGSETS], ['promptId', PROMPTS], ['expenses', EXPENSES]]) {
      if (rnd() < 0.7) body[k] = pick(vals);
    }
    op = { kind: 'create', body };
  } else if (r < 0.85) {
    const body = { today: TODAY };
    for (const [k, vals] of [['day', DAYS], ['time', TIMES], ['text', TEXTS], ['mood', MOODS], ['tags', TAGSETS], ['expenses', EXPENSES]]) {
      if (rnd() < 0.35) body[k] = pick(vals);
    }
    op = { kind: 'patch', id: rnd() < 0.06 ? 9999 : pick(ids), body };
  } else {
    op = { kind: 'delete', id: rnd() < 0.08 ? 9999 : pick(ids) };
  }
  let res;
  if (op.kind === 'create') res = await call('POST', '/api/journal', op.body);
  else if (op.kind === 'patch') res = await call('PATCH', `/api/journal/${op.id}`, op.body);
  else res = await call('DELETE', `/api/journal/${op.id}`);
  op.status = res.status;
  op.error = res.status >= 400 ? res.body.error : null;
  if (op.kind === 'create' && res.status === 201) { ids.push(res.body.entry.id); op.createdExpenses = res.body.expenses.map((e) => e.id); }
  if (op.kind === 'patch' && res.status === 200) op.createdExpenses = res.body.expenses.map((e) => e.id);
  if (op.kind === 'delete' && res.status === 204) ids = ids.filter((x) => x !== op.id);
  if ((op.kind === 'create' && res.status === 201) || (op.kind === 'patch' && res.status === 200)) op.newBadges = res.body.newBadges.map((b) => b.id);
  const exp = (await call('GET', '/api/export')).body;
  const timelines = {};
  for (const d of [TODAY, addDays(TODAY, -1), addDays(TODAY, -3)]) {
    const t = (await call('GET', `/api/timeline?day=${d}&today=${TODAY}&now=14:00`)).body;
    timelines[d] = { summary: t.summary, items: t.items.map(projectItem) };
  }
  const month = (await call('GET', '/api/journal?month=2026-10')).body.entries.map((e) => e.id);
  const stats = (await call('GET', `/api/stats?today=${TODAY}`)).body;
  op.after = {
    entries: exp.journal.map(projectEntry),
    expenses: exp.expenses.map(projectExpense),
    timelines,
    month,
    stats: { totalPoints: stats.totalPoints, totalEntries: stats.totalEntries, totalWords: stats.totalWords, streak: stats.streak, badges: stats.badges.map((b) => [b.id, b.unlockedOn ?? null]) },
  };
  ops.push(op);
}
fs.writeFileSync(process.argv[2], JSON.stringify({ today: TODAY, ops }));
const ok = ops.filter((o) => o.status < 400).length;
const errs = [...new Set(ops.filter((o) => o.status >= 400).map((o) => o.error))];
const last = ops.at(-1).after;
console.log('ops', ops.length, 'accepted', ok, 'refused', ops.length - ok, 'distinct errors', errs.length, 'entries', last.entries.length, 'expenses', last.expenses.length, 'with expenses', ops.filter((o) => o.createdExpenses && o.createdExpenses.length).length);
