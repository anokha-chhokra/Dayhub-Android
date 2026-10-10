import { web, readWeb } from './web.mjs';
const { createBackend } = await web('public/js/core/backend.js');
const { memoryStorage } = await web('public/js/core/store.js');
import fs from 'node:fs';

// The web app's own test (public/js/native.js).
const isBlank = (doc) => !doc || (!(doc.tasks || []).length && !(doc.expenses || []).length && !(doc.habits || []).length && !(doc.journal || []).length);

const source = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const docs = source.map((s) => s.doc);
const empty = { settings: { name: 'Only settings', setupDone: true } };
docs.push(empty);
docs.push({ settings: {}, music: [{ id: 1, provider: 'youtube', kind: 'video', url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', embedUrl: 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', label: 'x' }] });
docs.push({ badges: { 'first-step': '2026-10-05' } });
docs.push({ tasks: [{ id: 1, title: 'a' }] });
docs.push({ expenses: [{ id: 1, amountMinor: 100, category: 'Food', spentOn: '2026-10-05' }] });
docs.push({ habits: [{ id: 1, title: 'h', kind: 'check', createdOn: '2026-10-05' }] });
docs.push({ journal: [{ id: 1, day: '2026-10-05', text: 'hi' }] });
docs.push({ habitLogs: [] });
for (const k of ['tasks', 'expenses', 'habits', 'journal']) docs.push({ [k]: [] });

const cases = [];
for (const doc of docs) {
  const backend = createBackend({ storage: memoryStorage() });
  const imp = await backend.call('POST', '/api/import', JSON.parse(JSON.stringify(doc)));
  if (imp.status >= 400) throw new Error(JSON.stringify(imp.body));
  const exported = (await backend.call('GET', '/api/export')).body;
  cases.push({ doc, blank: isBlank(exported) });
}
fs.writeFileSync(process.argv[3], JSON.stringify(cases));
console.log('cases', cases.length, 'blank', cases.filter((c) => c.blank).length);
