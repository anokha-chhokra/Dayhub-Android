import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

let seed = 140014;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const TODAY = '2026-10-05';
const TITLES = ['Send invoice', 'Call mum', '  padded  ', 'two\nlines', 'tab\there', 'x'.repeat(200), 'x'.repeat(201), '', '   ', null, 5, 'caf\u00e9 \u65e5\u672c', 'ctrl\u0007char'];
const DUES = [null, null, '', TODAY, '2026-09-28', '2026-10-12', '2026-10-06', '2026-10-04', 'bad', '2026-02-30', '2026-13-01', 20261005];
const PRIOS = [0, 1, 1, 2, '1', null, true];
const DONES = [true, true, false, 'yes', 1];
const TIMES = [undefined, '08:30', '23:59', '25:00', ''];

const project = (t) => ({ id: t.id, title: t.title, dueOn: t.dueOn ?? null, priority: t.priority, done: !!t.done, doneOn: t.doneOn ?? null, doneTime: t.doneTime ?? null });
const groups = (tasks) => {
  const g = { overdue: [], today: [], upcoming: [], noDate: [] };
  for (const t of tasks) {
    if (!t.dueOn) g.noDate.push(t.id);
    else if (t.dueOn < TODAY) g.overdue.push(t.id);
    else if (t.dueOn === TODAY) g.today.push(t.id);
    else g.upcoming.push(t.id);
  }
  return g;
};

const backend = createBackend({ storage: memoryStorage() });
const call = async (m, p, b) => { const res = await backend.call(m, p, b === undefined ? undefined : JSON.parse(JSON.stringify(b))); return res; };
const ops = [];
let ids = [];
for (let i = 0; i < 700; i++) {
  const r = rnd();
  let op;
  if (r < 0.34 || !ids.length) {
    const body = {};
    if (rnd() < 0.9) body.title = pick(TITLES);
    if (rnd() < 0.7) body.dueOn = pick(DUES);
    if (rnd() < 0.5) body.priority = pick(PRIOS);
    op = { kind: 'create', body };
  } else if (r < 0.8) {
    const body = {};
    if (rnd() < 0.4) body.title = pick(TITLES);
    if (rnd() < 0.4) body.dueOn = pick(DUES);
    if (rnd() < 0.3) body.priority = pick(PRIOS);
    if (rnd() < 0.5) { body.done = pick(DONES); body.today = TODAY; const t = pick(TIMES); if (t !== undefined) body.time = t; }
    op = { kind: 'patch', id: rnd() < 0.08 ? 9999 : pick(ids), body };
  } else {
    op = { kind: 'delete', id: rnd() < 0.1 ? 9999 : pick(ids) };
  }
  let res;
  if (op.kind === 'create') res = await call('POST', '/api/tasks', op.body);
  else if (op.kind === 'patch') res = await call('PATCH', `/api/tasks/${op.id}`, op.body);
  else res = await call('DELETE', `/api/tasks/${op.id}`);
  op.status = res.status;
  op.error = res.status >= 400 ? res.body.error : null;
  if (op.kind === 'create' && res.status === 201) ids.push(res.body.id);
  if (op.kind === 'delete' && res.status === 204) ids = ids.filter((x) => x !== op.id);
  const open = (await call('GET', '/api/tasks?status=open')).body.tasks;
  const done = (await call('GET', '/api/tasks?status=done')).body.tasks;
  const all = (await call('GET', '/api/tasks?status=all')).body.tasks;
  op.after = { open: open.map(project), done: done.map(project), all: all.map(project), groups: groups(open) };
  ops.push(op);
}
fs.writeFileSync(process.argv[2], JSON.stringify({ today: TODAY, ops }));
const ok = ops.filter((o) => o.status < 400).length;
const errs = [...new Set(ops.filter((o) => o.status >= 400).map((o) => o.error))];
console.log('ops', ops.length, 'accepted', ok, 'refused', ops.length - ok, 'distinct errors', errs.length, 'final tasks', ops.at(-1).after.all.length, 'done', ops.at(-1).after.done.length);
