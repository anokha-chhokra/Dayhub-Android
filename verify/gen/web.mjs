// Where the web app lives, for the generators. Set WEB_APP to the web app's folder (the one with public/ and android/).
// The generators run the web app's own code, so the Java side is compared with the real thing, not with a copy of it.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = process.env.WEB_APP;
if (!root || !fs.existsSync(path.join(root, 'public', 'js', 'core', 'backend.js'))) {
  console.error('WEB_APP must be the web app folder (it has public/js/core/backend.js). Got: ' + (root || '(not set)'));
  process.exit(2);
}

/** Loads one of the web app's modules, e.g. web('public/js/core/backend.js'). */
export const web = (rel) => import(pathToFileURL(path.join(root, rel)).href);

/** Reads one of the web app's files as text, e.g. readWeb('public/js/focus.js'). */
export const readWeb = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');
