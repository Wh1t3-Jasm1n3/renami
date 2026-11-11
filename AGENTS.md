# Renami — project rules for agent sessions

## Live per-session todo list (mandatory)

Every session working in this repo keeps a live todo list in claude-mem's work state,
so progress is visible in the side panel and on the dashboard (http://localhost:37701)
even while several apps/sessions run at once:

- As soon as the work has 3 or more distinct steps, create one work_state list named
  `renami: <goal>` (e.g. `renami: ship 1.3.0 public release`). One list per session goal;
  reuse and update that same list for the rest of the session — never start a duplicate.
- Mark the current step `doing` when it starts and `done` the moment it is actually done
  (e.g. committed/pushed). Update after every step, not just once at the start.
- Keep items coarse (milestone-level, one line each) so the global dashboard stays scannable.
- Attach finished deliverables via `work_product` (commit, PR or release URL).
- Close the list with `"status": "done"` when the session goal is complete.

## Release discipline

- Every release: bump the version in `gradle.properties`, update the README roadmap table,
  run `gradle test` green, tag `v<version>`, push, and publish a GitHub release with the
  jpackage `.app` zip and the CLI `installDist` zip as downloadable assets.
