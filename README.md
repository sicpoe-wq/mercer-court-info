# Mercer County Court Info

Android app (Jetpack Compose) showing public Mercer County, PA information:

- **Jail** – Mercer County Jail population report, with a NEW badge for people who first appear on the roster (kept 48 hours), a "New only" filter and a "Left roster" list.
- **Hearings** – today's Court of Common Pleas daily hearing list (PDF parsed on device).
- **Dockets** – links to the PA UJS docket search and PA DOC inmate locator (not scraped).
- **News** – police/arrest and local news from public RSS feeds (WKBN, WYTV, WFMJ, The Vindicator, Tribune Chronicle, YourErie, ButlerRadio.com, plus Google News RSS searches for Sharon Herald, Allied News and "Mercer County" Pennsylvania), filtered by distance from your ZIP using an offline Census 2023 Gazetteer table.

A listing or charge is not a conviction. Data comes from public sources and may be incomplete or out of date.

Download: see [Releases](https://github.com/sicpoe-wq/mercer-court-info/releases/latest).

Build: JDK 17, `gradle assembleDebug` (create `local.properties` with `sdk.dir=...`). Test fixtures with personal data (jail roster/hearing snapshots) are not committed; those tests are skipped without them.
