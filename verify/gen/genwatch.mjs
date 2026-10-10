import { web, readWeb } from './web.mjs';
const { clampMinutes, minutesLabel, faceLength, dialPlan, polar, wedgePath, handDegrees } = await web('public/js/stopwatch.js');
import fs from 'node:fs';

// Inputs for clampMinutes, tagged so NaN and strings survive JSON.
const clampInputs = [
  ...[25, 0, -5, 999, 24.6, 1, 480, 481, 479.5, 0.5, -0.5, 2.5, 3.5, 1e9, -1e9, 7.4999, 1e21].map((v) => ({ t: 'n', v })),
  { t: 'nan' }, { t: 'inf' }, { t: 'ninf' },
  ...['45', ' 7 ', '', '   ', 'abc', '12abc', '24.6', '-5', '+7', '0x10', '999', '1e3', '٣', '٣٣', '0', '00012', '480', '481', '99999999999999999999', '-99999999999999999999', 'abc12', '  25 min', '1.5h', '\u00a07\u00a0'].map((v) => ({ t: 's', v })),
];
const decode = (i) => (i.t === 'nan' ? NaN : i.t === 'inf' ? Infinity : i.t === 'ninf' ? -Infinity : i.v);
const clamp = clampInputs.map((i) => ({ input: i, expected: clampMinutes(decode(i)) }));

const plans = [];
for (let m = 1; m <= 480; m++) plans.push({ m, plan: dialPlan(m) });
for (const m of [0, -3, 481, 9999, 24.6]) plans.push({ m, plan: dialPlan(m) });

const labels = [];
for (let m = 0; m <= 700; m++) labels.push({ m, label: minutesLabel(m), face: faceLength(m) });
for (const m of [24.5, 24.4, 99.5, 99.4, 59.5, 0.4]) labels.push({ m, label: minutesLabel(m), face: faceLength(m) });

const points = [];
for (const [cx, cy, r] of [[100, 100, 50], [120, 148, 86], [120, 148, 63], [120, 148, 94], [0, 0, 1]]) {
  for (let deg = -90; deg <= 450; deg += 7.5) points.push({ cx, cy, r, deg, p: polar(cx, cy, r, deg) });
}

const wedges = [];
const degs = [0, 0.01, 0.05, 0.06, 1, 30, 45.5, 90, 123.456, 179.99, 180, 180.01, 200, 270, 300.3, 345, 359.9, 359.94, 359.95, 360, 400, -10];
for (const from of degs) for (const to of [0, 0.01, 90, 180, 270, 359.99, 360]) wedges.push({ from, to, path: wedgePath(120, 148, 86, from, to) });
for (const [cx, cy, r] of [[120, 148, 86], [100, 100, 50], [0, 0, 1]]) wedges.push({ cx, cy, r, from: 0, to: 360, path: wedgePath(cx, cy, r, 0, 360) });

const hands = [];
for (const total of [0, -5, 1500000, 60000, 28800000]) for (const el of [-100, 0, 1, 750000, 1125000, 1500000, 1500001, 9999999, 30000, 14400000]) hands.push({ el, total, deg: handDegrees(el, total) });

fs.writeFileSync(process.argv[2], JSON.stringify({ clamp, plans, labels, points, wedges, hands }));
console.log('clamp', clamp.length, 'plans', plans.length, 'labels', labels.length, 'points', points.length, 'wedges', wedges.length, 'hands', hands.length);
