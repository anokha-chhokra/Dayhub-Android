import { web, readWeb } from './web.mjs';
// Pulls the web app's own clock() out of focus.js (the file imports the page, so it is read as text) and runs it.
import fs from 'node:fs';

const src = readWeb('public/js/focus.js');
const m = src.match(/export function clock\(ms\) \{[\s\S]*?\n\}\n/);
if (!m) throw new Error('clock() not found in focus.js');
const clock = new Function(`${m[0].replace('export ', '')}; return clock;`)();

const inputs = new Set([0, 1, 999, 1000, 1001, 1999, 2000, 59000, 59001, 60000, 60001, 3599000, 3599001, 3600000, 3600001, 3661000,
  86400000, 28800000, 28800001, 480 * 60000, -1, -1000, -1500, -999999]);
let seed = 12345;
const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
for (let i = 0; i < 4000; i++) inputs.add(Math.floor(rnd() * 30000000));
for (let i = 0; i < 400; i++) inputs.add(Math.floor(rnd() * 9000) * 1000 + [0, 1, 999, 500][i % 4]);
const cases = [...inputs].map((ms) => ({ ms, text: clock(ms) }));
fs.writeFileSync(process.argv[2], JSON.stringify(cases));
console.log('clock cases', cases.length);
