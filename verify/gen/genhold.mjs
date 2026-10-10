import { web, readWeb } from './web.mjs';
// Reads the hold-to-end time out of the web app's own sources (the page and its Android guard).
import fs from 'node:fs';

const focus = readWeb('public/js/focus.js');
const guard = readWeb('android/app/src/main/java/app/dayhub/FocusGuardService.java');
const strings = readWeb('android/app/src/main/res/values/strings.xml');

const page = Number(focus.match(/export const HOLD_TO_END_MS = (\d+);/)[1]);
const android = Number(guard.match(/HOLD_MS = (\d+);/)[1]);
const coverSeconds = Number(strings.match(/name="cover_hold">[^<]*?(\d+) seconds/)[1]);
const delay = Number(guard.match(/COVER_DELAY_MS = (\d+);/)[1]);
const gap = Number(guard.match(/BOUNCE_GAP_MS = (\d+);/)[1]);
fs.writeFileSync(process.argv[2], JSON.stringify({ page, android, coverSeconds, delay, gap }));
console.log({ page, android, coverSeconds, delay, gap });
