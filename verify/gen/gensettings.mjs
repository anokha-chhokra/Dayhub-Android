import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

let seed = 190019;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const NAMES = ['Asha', '  Ravi  ', '', '   ', null, 5, 'x'.repeat(40), 'x'.repeat(41), 'J\u00fcrgen \u65e5\u672c', 'tab\there'];
const CURS = ['inr', 'USD', 'eur', 'GBP', 'aed', 'SGD', 'ZZZ', 'xx', 'abcd', 'ZZZ9', '', null, 5, ' usd '];
const BUDGETS = ['30000', '30,000', '1500.50', '', 0, '0', null, 250, 12345.67, 99999999999, '99999999999', -5, 'abc', '1.234', true];
const NOTIFS = [true, false, 'yes', 1, 0, null];
const REMINDERS = ['', '07:30', '21:00', '23:59', '24:00', '7:30', null, 5, 'abc'];

const projectSettings = (s) => ({ name: s.name, currency: s.currency, monthlyBudgetMinor: s.monthlyBudgetMinor, setupDone: s.setupDone, currentMusicId: s.currentMusicId ?? null, notifications: s.notifications, journalReminder: s.journalReminder });
const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => backend.call(m, p, b === undefined ? undefined : JSON.parse(JSON.stringify(b)));
const ops = [];
for (let i = 0; i < 600; i++) {
  const body = {};
  for (const [k, vals] of [['name', NAMES], ['currency', CURS], ['monthlyBudget', BUDGETS], ['notifications', NOTIFS], ['journalReminder', REMINDERS]]) {
    if (rnd() < 0.4) body[k] = pick(vals);
  }
  const res = await call('PUT', '/api/settings', body);
  const after = (await call('GET', '/api/settings')).body;
  ops.push({ body, status: res.status, error: res.status >= 400 ? res.body.error : null, response: res.status < 400 ? projectSettings(res.body) : null, after: projectSettings(after) });
}
fs.writeFileSync(process.argv[2], JSON.stringify({ ops }));
const ok = ops.filter((o) => o.status < 400).length;
console.log('ops', ops.length, 'accepted', ok, 'refused', ops.length - ok, 'distinct errors', new Set(ops.filter((o) => o.error).map((o) => o.error)).size);
