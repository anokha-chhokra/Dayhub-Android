import { web, readWeb } from './web.mjs';
// Stage B of the widget tap check: the web app's own applyWidgetAction (read out of native.js as text) applies the
// queued actions to its real backend; what is left is recorded for the Java side to match.
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

const nativeSrc = readWeb('public/js/native.js');
const windowMs = nativeSrc.match(/const MOOD_WINDOW_MS = [^;]+;/)[0];
const apply = nativeSrc.match(/async function applyWidgetAction\(a, state\) \{[\s\S]*?\n\}\n/)[0];
const factory = new Function('api', 'cheer', `${windowMs}\n${apply}\nreturn applyWidgetAction;`);

export const project = (exp, dash) => ({
  tasks: exp.tasks.map((t) => [t.id, t.title, t.dueOn ?? null, t.priority ?? 0, !!t.done, t.doneOn ?? null, t.doneTime ?? null]),
  habitLogs: exp.habitLogs.map((l) => [l.habitId, l.day, l.value, l.time ?? null]),
  journal: exp.journal.map((e) => [e.id, e.day, e.time ?? null, e.text ?? '', e.mood ?? null, e.tags ?? [], e.wordCount ?? 0]),
  badges: Object.entries(exp.badges).map(([id, day]) => `${id}@${day}`).sort(),
  stats: [dash.stats.totalPoints, dash.stats.streak],
});

const plan = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const results = [];
let applied = 0, dropped = 0;
for (const item of plan) {
  const backend = createBackend({ storage: memoryStorage() });
  const imp = await backend.call('POST', '/api/import', JSON.parse(JSON.stringify(item.doc)));
  if (imp.status >= 400) throw new Error('import ' + JSON.stringify(imp.body));
  const api = async (path, opts = {}) => {
    const res = await backend.call(opts.method || 'GET', `/api${path}`, opts.body === undefined ? undefined : JSON.parse(JSON.stringify(opts.body)));
    if (res.status >= 400) throw Object.assign(new Error(res.body.error), { status: res.status });
    return res.body;
  };
  const applyWidgetAction = factory(api, () => {});
  const state = { mood: null };
  for (const a of item.actions) {
    try { await applyWidgetAction(a, state); applied++; } catch (e) { if (e && e.status && e.status >= 400 && e.status < 500) dropped++; else throw e; }
  }
  const exp = (await backend.call('GET', '/api/export')).body;
  const dash = (await backend.call('GET', `/api/dashboard?today=${item.day}`)).body;
  results.push(project(exp, dash));
}
fs.writeFileSync(process.argv[3], JSON.stringify(results));
console.log('sequences', plan.length, 'actions applied', applied, 'rejected by the web app', dropped);
