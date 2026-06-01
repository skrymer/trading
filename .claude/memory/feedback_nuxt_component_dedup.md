---
name: Nuxt component auto-import dedupes overlapping path segments
description: When a components/ subdir name ends with the same word the filename starts with, Nuxt collapses them — verify the registered name in .nuxt/components.d.ts before referencing the component
type: feedback
originSessionId: 399c9a2a-ecaf-4456-befd-f2cc7b6af5cc
---
When you create `components/<dir>/<File>.vue`, Nuxt auto-registers the component as `<DirPascal><FilePascal>`. **But if the dir suffix matches the file prefix, Nuxt deduplicates them.** Example:

- `components/backtest-reports/ReportsTable.vue` → `BacktestReportsTable` (NOT `BacktestReportsReportsTable`)
- `components/data-management/DatabaseStatsCards.vue` → `DataManagementDatabaseStatsCards` (no overlap, no dedup)
- `components/backtesting/Cards.vue` → `BacktestingCards` (no overlap)

**Why:** Nuxt 4's auto-import has a "smart" naming layer that strips the duplicated word. Lint and typecheck **do not catch a wrong reference** — they only know the component file exists, not what name auto-import assigned. The failure is a runtime Vue warn (`Failed to resolve component: ...`) and the slot just never renders.

**How to apply:** before referencing a new component in a page or another component, check `.nuxt/components.d.ts` for the actual registered name. Don't trust your own Pascal-case-concatenation prediction. The grep is one line:

```bash
grep -i <substring> asgaard/.nuxt/components.d.ts
```

If you're naming a new component and want to avoid surprise, pick a filename that doesn't share its first word with the directory's last word.
