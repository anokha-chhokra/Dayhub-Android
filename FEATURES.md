# Day Hub for Android: feature checklist

A native, offline Java app: no WebView, no network, no INTERNET permission. Tick items off as they are built.

## App shell
- [x] 1. Single full-screen activity (edge-to-edge, portrait only, no WebView, no network)
- [x] 2. Cream-paper hand-drawn look: custom views, hand-drawn card, button, field and tab shapes, handwriting font, bottom nav bar
- [x] 3. Bottom sheets, date and time pickers, toasts with Undo, back-button handling

## Data and storage
- [x] 4. Local data store: one JSON document in private app storage, saved atomically
- [x] 5. Data engine in Java matching the web app: tasks, habits, journal, expenses, music links, settings, with validation
- [x] 6. Rollback when a save fails, and a "damaged data" screen with restore options
- [x] 7. Habit points, streaks and badges
- [x] 8. Expense detection from journal text (e.g. "Rs 200", "spent 200 on lunch")
- [x] 9. Spend insights: budget pace and attention items
- [x] 10. Timeline, calendar and stats
- [x] 11. Import and export of the data, plus CSV and Markdown export

## Screens
- [x] 12. First-run setup wizard
- [x] 13. Home dashboard: greeting, points and streak, needs-attention tiles, today's tasks, quick journal add, spend summary, music tile, focus tile
- [x] 14. Tasks: add and edit, due dates, priority, star, done, delete with Undo
- [x] 15. Habits: check, goal and limit habits, week view
- [x] 16. Journal: daily timeline entries with mood and tags
- [x] 17. Spend: expenses, categories, budget, month totals
- [x] 18. Music: saved YouTube links that open in the YouTube app or browser (no in-app player)
- [x] 19. Settings: profile, currency, reminders, backup, focus, notifications

## Backup
- [x] 20. Single backup file chosen through the system file picker, overwritten on every backup (only the latest exists)
- [x] 21. Write verification: read the file back after writing; skip the backup if the data is blank
- [x] 22. Automatic backup after changes, and restore from a backup file

## Focus mode
- [ ] 23. Old pocket-stopwatch dial that re-engraves for the chosen time, with presets, typed minutes and ±5 buttons beneath it
- [ ] 24. Timer that survives the app closing, an alarm when it ends, and a notification
- [ ] 25. Lock: an accessibility service keeps the phone on Day Hub, phone messages and WhatsApp; calls still come through
- [ ] 26. Fallback cover overlay, and an 8-second hold to end the session early

## Widgets and reminders
- [ ] 27. Six home-screen widgets (Home, Tasks, Habits, Spend, Quick add, Focus) that read the app's data directly
- [ ] 28. Widget taps (tick a task, check a habit, add something) apply instantly
- [ ] 29. Reminders as notifications, rescheduled after reboot and at midnight

## Quality
- [ ] 30. Checks that match the Java engine and dial against the web app, plus a guard that fails the build if any network or WebView code appears
