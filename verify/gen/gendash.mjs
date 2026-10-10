import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

const TODAY = '2026-10-05';
const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => {
  const res = await backend.call(m, p, b);
  if (res.status >= 400) throw new Error(`${m} ${p} -> ${res.status} ${JSON.stringify(res.body)}`);
  return res.body;
};

await call('POST', '/api/setup', { name: 'Asha', currency: 'inr', monthlyBudget: 30000, habits: ['workout', 'water'], today: '2026-10-01' });
await call('PUT', '/api/settings', { journalReminder: '21:00' });
const habits = (await call('GET', `/api/habits?today=${TODAY}`)).habits;
await call('PATCH', `/api/habits/${habits[0].id}`, { remindAt: '07:00', today: TODAY });
await call('POST', '/api/habits', { title: 'Stretch', kind: 'check', remindAt: '18:30', days: [1, 2, 3, 4, 5], today: '2026-10-01' });
await call('POST', '/api/habits', { title: 'Sundays only', kind: 'check', remindAt: '09:00', days: [0], today: '2026-10-01' });
await call('POST', '/api/tasks', { title: 'Pay rent', dueOn: '2026-09-28', priority: 1 });
await call('POST', '/api/tasks', { title: 'Call mum', dueOn: TODAY });
await call('POST', '/api/tasks', { title: 'Next week', dueOn: '2026-10-12', priority: 1 });
await call('POST', '/api/tasks', { title: 'Someday' });
const done = await call('POST', '/api/tasks', { title: 'Already done', dueOn: TODAY });
await call('PATCH', `/api/tasks/${done.id}`, { done: true, today: TODAY, time: '08:00' });
await call('POST', '/api/expenses', { amount: '9000', category: 'Bills', spentOn: '2026-10-02' });
await call('POST', '/api/expenses', { amount: '1500.50', category: 'Food', spentOn: TODAY });
await call('POST', '/api/expenses', { amount: '40', category: 'Food', spentOn: '2026-09-30' });

const backup = await call('GET', '/api/export');
const cases = [];
for (const [today, now] of [[TODAY, null], [TODAY, '07:30'], [TODAY, '19:00'], [TODAY, '21:30'], ['2026-10-02', '22:00'], ['2026-10-04', '12:00'], ['2026-11-03', '23:00']]) {
  const q = `today=${today}${now ? `&now=${now}` : ''}`;
  const d = await call('GET', `/api/dashboard?${q}`);
  cases.push({ today, now, spend: d.spend, attention: d.attention });
}
// Now push spending over the budget and look again.
await call('POST', '/api/expenses', { amount: '25000', category: 'Shopping', spentOn: TODAY });
const overBackup = await call('GET', '/api/export');
const over = await call('GET', `/api/dashboard?today=${TODAY}&now=10:00`);
fs.writeFileSync(process.argv[2], JSON.stringify({ backup, cases, overBackup, over: { today: TODAY, now: '10:00', spend: over.spend, attention: over.attention } }));
console.log('cases', cases.length, 'sample attention:', JSON.stringify(cases[3].attention.map((a) => a.type + ':' + (a.title))), 'pace', JSON.stringify(cases[0].spend.pace));
