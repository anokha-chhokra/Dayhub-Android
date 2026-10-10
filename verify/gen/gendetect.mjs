import { web, readWeb } from './web.mjs';
const { detectExpenses, buildHints } = await web('public/js/core/detect.js');
import fs from 'node:fs';

let seed = 987654;
const rnd = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (xs) => xs[int(0, xs.length - 1)];

const verbs = ['Spent', 'spent', 'Paid', 'paid', 'pay', 'Bought', 'bought', 'buy', 'cost', 'costs', 'Ordered', 'recharged', 'booked', 'got paid', 'was paid', 'Earned', 'received', 'Refunded', 'saved', 'My budget is', 'balance', 'Salary credited', 'invoiced'];
const amounts = ['₹250', '₹ 90', 'Rs 1,200', 'rs. 90', 'Rs.45', 'INR 500', 'inr 75', '$12.50', '€30', '£5', '250 rs', '400 rupees', '1 rupee', '60 Rs.', '7 bucks', '1.5k', '2k', '3 lakhs', '1.2 lakh', '1.2 crore', '2 crores', '5 lacs', '540', '8', '8000', '12,000', '9', '10', '0', '0.5', '99999999999', '1,23,456', '₹1.234', '250.75', '₹250/-'];
const notes = ['on lunch', 'for fuel', 'at the mall', 'towards rent', 'on dosa on Friday', 'for the internet bill', 'groceries', 'an auto', 'coffee yesterday', 'on tea with Ravi', 'for movie tickets', 'on a new shirt', 'for zorbing', 'for dinner and drinks', 'on the metro today', 'to the doctor', 'on Netflix', 'in cash', 'only', '', 'for some of the things', 'on a gift for my mother'];
const days = ['', '', '', 'Yesterday ', 'yesterday ', 'The day before yesterday ', '3 days ago ', '45 days ago ', 'Last night ', 'On Monday ', 'last Friday ', 'on sunday '];
const joins = [' and ', ', ', ' + ', '. ', '\n', '; ', ' but ', '! ', '? ', '\r\n', ' \u00a0 '];
const fillers = ['Had a great day.', 'Walked 8000 steps', 'I met 3 friends', 'Nothing special', 'Café était bon', 'Watched a movie with 2 friends', 'Slept 8 hours', 'Rent due on the 5th'];

const texts = [
  'Spent ₹250 on lunch with Ravi.', 'paid Rs 1,200 for internet bill', 'bought groceries for 540', 'spent 1.5k on headphones',
  'Lunch ₹180 and auto 60 rs', 'Spent ₹250 on lunch with Ravi and paid 400 for fuel.', 'Spent ₹250 on lunch.',
  'I walked 8000 steps and bought 3 apples', 'Had a great day, nothing special.', '', '   ', 'Earned ₹5000 today from a client.',
  'Got paid 50000 for the project.', 'My budget is ₹30000 this month.', 'Received a refund of ₹200 from amazon',
  'Refunded ₹200 by amazon, spent ₹90 on chai', 'Spent ₹500 which is within my budget', 'Paid 1.2 lakh for the bike',
  'Spent 2k on a gift', 'Spent 3 lakhs on a car', 'Yesterday I spent ₹400 on dinner', 'Spent ₹90 on chai the day before yesterday',
  '5 days ago I spent 2k on a gift', 'Spent 300 on dosa today', 'Spent 300 on dosa on Friday', 'Recharged phone for 299 and paid rent of ₹12,000',
  'paid 700 for zorbing', 'spent 300 on dinner',
];
for (let i = 0; i < 900; i++) {
  const clauses = [];
  const n = int(1, 4);
  for (let k = 0; k < n; k++) {
    const r = rnd();
    if (r < 0.12) clauses.push(pick(fillers));
    else if (r < 0.35) clauses.push(`${pick(days)}${pick(notes) ? pick(notes) + ' ' : ''}${pick(amounts)}`.trim());
    else if (r < 0.5) clauses.push(`${pick(amounts)} ${pick(notes)}`.trim());
    else clauses.push(`${pick(days)}${pick(verbs)} ${pick(amounts)} ${pick(notes)}`.trim());
  }
  let t = clauses[0];
  for (let k = 1; k < clauses.length; k++) t += pick(joins) + clauses[k];
  if (rnd() < 0.3) t += pick(['.', '!', '', '\n', ' ']);
  if (rnd() < 0.15) t = t.toUpperCase();
  texts.push(t);
}

const histories = [
  null,
  [{ note: 'zorbing', category: 'Fun' }, { note: 'zorbing ride', category: 'Fun' }, { note: 'cricket nets', category: 'Fun' }],
  [{ note: 'dinner', category: 'Fun' }],
  [{ note: 'dinner', category: 'Fun' }, { note: 'dinner party', category: 'Fun' }],
  [{ note: 'mall food court', category: 'Food' }, { note: 'mall parking', category: 'Transport' }, { note: 'mall gift', category: 'Shopping' }, { note: 'mall', category: 'Shopping' }],
  [{ note: 'rent', category: 'Other' }, { note: 'rent', category: 'Other' }, { note: 'metro card', category: 'Bills' }, { note: 'metro card recharge', category: 'Bills' }, { note: null, category: 'Food' }, { note: 'x', category: '' }],
];
const refDays = [null, '2026-10-05', '2026-10-09', '2026-02-28'];

const cases = [];
for (const text of texts) {
  const expenses = pick(histories);
  const refDay = pick(refDays);
  const hints = expenses ? buildHints(expenses) : null;
  cases.push({ text, refDay, expenses, expected: detectExpenses(text, { refDay, hints }) });
}
// every text once more with no history and no day, plus the web app's own history cases
for (const text of texts.slice(0, 40)) cases.push({ text, refDay: null, expenses: null, expected: detectExpenses(text) });
const hintCases = histories.filter(Boolean).map((expenses) => ({ expenses, hints: buildHints(expenses) }));
fs.writeFileSync(process.argv[2], JSON.stringify({ cases, hintCases }));
const hits = cases.filter((c) => c.expected.length).length;
const cats = {};
for (const c of cases) for (const e of c.expected) cats[e.category] = (cats[e.category] || 0) + 1;
console.log('cases', cases.length, 'with suggestions', hits, 'categories', JSON.stringify(cats), 'daysAgo>0', cases.filter((c) => c.expected.some((e) => e.daysAgo > 0)).length);
