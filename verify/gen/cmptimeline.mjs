import fs from 'node:fs';
import assert from 'node:assert/strict';

const scenarios = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const java = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
let compared = 0;
scenarios.forEach((s, i) => {
  s.queries.forEach((q, k) => {
    assert.deepEqual(java[i][k], q.expected, `scenario ${i} query ${k} (${q.kind} ${q.day || q.month || q.today})`);
    compared += 1;
  });
});
console.log(`IDENTICAL to the web app: ${scenarios.length} scenarios, ${compared} timeline / calendar / stats responses`);
