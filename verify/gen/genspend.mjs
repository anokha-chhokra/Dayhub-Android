import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

let seed = 170017;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const TODAY = '2026-10-08';
const AMOUNTS = [250, '99.50', '1,234.5', 0.1, 12.345, '0', '-5', 'abc', '', null, undefined, 1e21, '99999999999', '1000000000', 5, '7.', '.5', ' 42 ', true];
const CATS = [undefined, null, '', 'Food', 'Bills', 'Transport', 'Shopping', 'Health', 'Fun', 'Other', 'Pets', 'food'];
const NOTES = [undefined, null, '', 'lunch', '  padded  ', 'two\nlines', 'x'.repeat(120), 'x'.repeat(121), 7, 'caf\u00e9 \u65e5\u672c', 'a,b "c"'];
const DATES = [undefined, null, '', TODAY, '2026-10-01', '2026-09-30', '2026-09-15', '2026-11-02', 'bad', '2026-02-30', 20261008];
const TIMES = [undefined, null, '', '07:30', '23:59', '25:00', 'ab'];

const projectExpense = (e) => ({ id: e.id, amountMinor: e.amountMinor, category: e.category, note: e.note ?? null, spentOn: e.spentOn, entryId: e.entryId ?? null, time: e.time ?? null });
const projectMonth = (m) => ({ month: m.month, total: m.totalMinor, count: m.count, byCategory: m.byCategory.map((c) => [c.category, c.totalMinor, c.count]), ids: m.expenses.map((e) => e.id) });

const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => backend.call(m, p, b === undefined ? undefined : JSON.parse(JSON.stringify(b)));
const ops = [];
let ids = [];
for (let i = 0; i < 600; i++) {
  let op;
  if (rnd() < 0.72 || !ids.length) {
    const body = { today: TODAY };
    for (const [k, vals] of [['amount', AMOUNTS], ['category', CATS], ['note', NOTES], ['spentOn', DATES], ['time', TIMES]]) {
      if (rnd() < (k === 'amount' ? 0.95 : 0.6)) body[k] = pick(vals);
    }
    op = { kind: 'add', body };
  } else {
    op = { kind: 'delete', id: rnd() < 0.1 ? 9999 : pick(ids) };
  }
  const res = op.kind === 'add' ? await call('POST', '/api/expenses', op.body) : await call('DELETE', `/api/expenses/${op.id}`);
  op.status = res.status;
  op.error = res.status >= 400 ? res.body.error : null;
  if (op.kind === 'add' && res.status === 201) ids.push(res.body.id);
  if (op.kind === 'delete' && res.status === 204) ids = ids.filter((x) => x !== op.id);
  const months = {};
  for (const ym of ['2026-09', '2026-10', '2026-11']) months[ym] = projectMonth((await call('GET', `/api/expenses?month=${ym}&today=${TODAY}`)).body);
  op.after = {
    months,
    all: (await call('GET', '/api/export')).body.expenses.map(projectExpense),
    csvAll: (await call('GET', '/api/expenses.csv')).body,
    csvOct: (await call('GET', '/api/expenses.csv?month=2026-10')).body,
  };
  ops.push(op);
}
fs.writeFileSync(process.argv[2], JSON.stringify({ today: TODAY, ops }));
const ok = ops.filter((o) => o.status < 400).length;
const errs = [...new Set(ops.filter((o) => o.status >= 400).map((o) => o.error))];
console.log('ops', ops.length, 'accepted', ok, 'refused', ops.length - ok, 'distinct errors', errs.length, 'expenses at end', ops.at(-1).after.all.length);
