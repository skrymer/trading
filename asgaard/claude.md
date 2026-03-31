# Claude.md - Asgaard Frontend (Nuxt/Vue)

## Project Overview

Nuxt 4 frontend for the stock trading backtesting platform. Provides dashboard interfaces for backtesting, portfolio management, stock data visualization, and market breadth analysis.

For complete project capabilities and overview, see the main CLAUDE.md file in the project root.

## Tech Stack

**Key Technologies:**
- **Framework**: Nuxt 4.1.2
- **UI Library**: NuxtUI 4.0.1
- **Language**: TypeScript 5.9.3
- **Charts**: ApexCharts 5.3.5, Unovis 1.6.1, Lightweight Charts 5.0.9
- **Utilities**: date-fns 4.1.0, Zod 4.1.11, VueUse 13.9.0
- **Package Manager**: pnpm 10.24.0
- **Node.js**: 24.2.0 (via Volta)
- **Code Quality**: ESLint 9.37.0

## Project Structure

```
asgaard/
├── app/
│   ├── layouts/
│   │   └── default.vue           # Main layout with sidebar navigation
│   ├── pages/                    # File-based routing
│   │   ├── index.vue             # Home/dashboard
│   │   ├── backtesting.vue       # Backtesting UI with strategy builder
│   │   ├── portfolio.vue         # Portfolio management
│   │   ├── scanner.vue           # Stock scanner
│   │   ├── stock-data/            # Stock data explorer with charts
│   │   │   └── [[symbol]].vue    #   Dynamic route: /stock-data/:symbol?
│   │   ├── breadth.vue           # Market/sector breadth analysis
│   │   ├── data-manager.vue      # Data ingestion & refresh controls
│   │   ├── app-metrics.vue       # Application metrics dashboard
│   │   ├── settings.vue          # API credentials & settings
│   │   ├── login.vue             # Authentication login page
│   │   └── test-chart.vue        # Chart component testing
│   ├── components/
│   │   ├── backtesting/          # Backtesting components (16)
│   │   │   ├── Cards.vue         # Summary stat cards
│   │   │   ├── ConfigModal.vue   # Strategy configuration modal
│   │   │   ├── EquityCurve.client.vue  # Equity curve visualization (client-only)
│   │   │   ├── SectorAnalysis.vue
│   │   │   ├── StockPerformance.vue
│   │   │   ├── ATRDrawdownStats.vue
│   │   │   ├── ExcursionAnalysis.vue
│   │   │   ├── ExitReasonAnalysis.vue
│   │   │   ├── MonteCarloResults.vue / MonteCarloEquityCurve.client.vue / MonteCarloMetrics.vue
│   │   │   ├── TimeBasedStats.vue
│   │   │   ├── MarketConditions.vue
│   │   │   ├── TradeChart.client.vue
│   │   │   ├── TradeDetailsModal.vue
│   │   │   └── DataCard.vue
│   │   ├── charts/               # Reusable chart components (9)
│   │   │   ├── BarChart.client.vue
│   │   │   ├── BreadthChart.client.vue  # Lightweight Charts breadth with Donchian channel
│   │   │   ├── DonutChart.client.vue
│   │   │   ├── HistogramChart.client.vue
│   │   │   ├── LineChart.client.vue
│   │   │   ├── ScatterChart.client.vue
│   │   │   ├── StockChart.client.vue    # Lightweight Charts candlestick
│   │   │   ├── SignalDetailsModal.vue
│   │   │   └── StrategySignalsTable.vue
│   │   ├── data-management/      # Data management components (4)
│   │   │   ├── DatabaseStatsCards.vue
│   │   │   ├── RefreshControlsCard.vue
│   │   │   ├── BreadthRefreshCard.vue
│   │   │   └── RateLimitCard.vue
│   │   ├── portfolio/            # Portfolio components (14)
│   │   │   ├── CreateModal.vue
│   │   │   ├── PositionDetailsModal.vue
│   │   │   ├── ClosePositionModal.vue
│   │   │   ├── DeleteModal.vue / DeletePositionModal.vue
│   │   │   ├── EditPositionMetadataModal.vue
│   │   │   ├── BatchEditStrategyModal.vue
│   │   │   ├── AddExecutionModal.vue
│   │   │   ├── EquityCurve.client.vue
│   │   │   ├── OpenTradeChart.client.vue
│   │   │   ├── OptionTradeChart.client.vue
│   │   │   ├── SyncPortfolioModal.vue
│   │   │   ├── RollChainModal.vue
│   │   │   └── CreateFromBrokerModal.vue
│   │   ├── scanner/              # Scanner components (10)
│   │   │   ├── ScanConfigModal.vue
│   │   │   ├── ScanResultsTable.vue
│   │   │   ├── AddTradeModal.vue
│   │   │   ├── BatchAddTradesModal.vue
│   │   │   ├── DeleteTradeModal.vue
│   │   │   ├── RollTradeModal.vue
│   │   │   ├── TradeDetailsModal.vue
│   │   │   ├── ExitAlerts.vue
│   │   │   ├── StatsCards.vue
│   │   │   └── NearMissAnalysis.vue
│   │   ├── settings/             # Settings components (1)
│   │   │   └── MembersList.vue
│   │   ├── strategy/             # Strategy builder components (3)
│   │   │   ├── StrategyBuilder.vue
│   │   │   ├── StrategySelector.vue
│   │   │   └── ConditionCard.vue
│   │   ├── StockPriceChart.client.vue  # Standalone stock chart
│   │   ├── SymbolSearch.vue      # Symbol search autocomplete
│   │   ├── UserMenu.vue          # User menu dropdown
│   │   ├── ConditionConfigModal.vue   # Condition configuration modal
│   │   └── ConditionSignalsTable.vue  # Condition signals display table
│   ├── plugins/
│   │   ├── apexcharts.client.ts  # ApexCharts plugin (client-only)
│   │   └── auth-interceptor.client.ts  # Auth interceptor plugin (client-only)
│   ├── composables/
│   │   ├── useAuth.ts            # Authentication composable
│   │   └── usePositionFormatters.ts  # Position formatting utilities
│   ├── middleware/
│   │   └── auth.global.ts        # Global auth middleware
│   ├── types/
│   │   ├── index.d.ts            # Main type definitions
│   │   └── enums.ts              # Enum types
│   ├── app.vue                   # Root component
│   └── error.vue                 # Error page
├── assets/css/main.css           # Global styles
├── nuxt.config.ts                # Nuxt configuration (proxy target via NUXT_BACKEND_URL env var)
├── Dockerfile                    # Multi-stage build (node:24-alpine, pnpm)
├── .dockerignore                 # Docker build exclusions
├── package.json                  # Dependencies
├── pnpm-lock.yaml                # Lock file
└── claude.md                     # This file
```

## Development Commands

```bash
pnpm dev          # Start development server on http://localhost:3000
pnpm build        # Build for production
pnpm preview      # Preview production build locally
pnpm lint         # Run ESLint
pnpm typecheck    # Run TypeScript type checking
```

### Docker / Production

The Asgaard frontend is containerized via a multi-stage Dockerfile (node:24-alpine). The backend proxy target is configurable via the `NUXT_BACKEND_URL` environment variable (defaults to `http://localhost:8080` for local dev, set to `http://udgaard:8080` in Docker).

```bash
# Build Docker image
docker build -t asgaard .

# Production deployment (via root compose.prod.yaml)
cd /path/to/trading && docker compose -f compose.prod.yaml up -d asgaard
```

## Key Patterns

### 1. NuxtUI Components

This project uses NuxtUI 4 extensively:

- **Layout**: `UDashboardLayout`, `UDashboardPanel`, `UDashboardSidebar`, `UContainer`
- **Navigation**: `UVerticalNavigation`, `UBreadcrumb`, `UCommandPalette`
- **Forms**: `UInput`, `UButton`, `USelect`, `UCheckbox`, `URadio`, `UTextarea`, `UForm`, `UFormGroup`
- **Data**: `UTable`, `UCard`, `UBadge`
- **Feedback**: `UNotification`, `UAlert`, `UProgress`, `UModal`, `USlideover`
- **Icons**: Access via `<UIcon name="i-lucide-{icon-name}" />`

### 2. Chart Libraries

Three chart libraries serve different purposes:

- **ApexCharts** (via `vue3-apexcharts`): Interactive line, bar, donut, histogram, scatter charts. Used for backtesting metrics, equity curves, sector analysis.
- **Lightweight Charts** (TradingView): Candlestick/OHLC stock charts in `StockChart.client.vue`, market breadth with Donchian channel fill in `BreadthChart.client.vue`.
- **Unovis**: Specialized visualizations.

Chart components use `.client.vue` suffix for client-side-only rendering.

### 3. File-Based Routing

Pages are automatically routed based on file structure:
- `pages/index.vue` → `/`
- `pages/backtesting.vue` → `/backtesting`
- `pages/stock-data.vue` → `/stock-data`

### 4. Auto-Imports

Nuxt auto-imports:
- Vue APIs (`ref`, `computed`, `watch`, etc.)
- Nuxt composables (`useRoute`, `useRouter`, `useFetch`, `useAsyncData`, etc.)
- VueUse composables (e.g., `useLocalStorage`, `useDark`, etc.)
- Components from `components/` directory
- NuxtUI composables (`useToast`, `useModal`, `useSlideover`, etc.)

### 5. TypeScript

- Full TypeScript support with strict type checking
- Use `pnpm typecheck` to validate types
- Vue SFC `<script setup lang="ts">` syntax preferred
- Type definitions in `app/types/index.d.ts` and `app/types/enums.ts`

### 6. Styling

- Tailwind CSS classes available throughout
- NuxtUI provides design tokens via `app.config.ts`
- Dark mode support built-in
- Custom CSS in `assets/css/main.css`

## Code Style Guidelines

### ESLint Configuration

```javascript
{
  stylistic: {
    commaDangle: 'never',  // No trailing commas
    braceStyle: '1tbs'     // One True Brace Style
  }
}
```

### Vue Component Structure

```vue
<script setup lang="ts">
// 1. Imports (if needed - most are auto-imported)
import type { BacktestReport } from '~/types'

// 2. Props & Emits
const props = defineProps<{
  report: BacktestReport
}>()

const emit = defineEmits<{
  refresh: []
}>()

// 3. Composables
const toast = useToast()

// 4. Reactive state
const isLoading = ref(false)

// 5. Computed values
const winRate = computed(() => props.report.winRate * 100)

// 6. Methods
function handleRefresh() {
  emit('refresh')
}
</script>

<template>
  <!-- Template here -->
</template>
```

## Common Patterns

### Data Fetching

```typescript
// Using useFetch for API calls to backend
const { data, pending, error } = await useFetch('/api/stocks', {
  baseURL: 'http://localhost:8080/udgaard'
})

// Using useAsyncData with custom logic
const { data } = await useAsyncData('backtest', () =>
  $fetch('/api/backtest', {
    baseURL: 'http://localhost:8080/udgaard',
    method: 'POST',
    body: request
  })
)
```

### Forms with Validation

```vue
<script setup lang="ts">
import { z } from 'zod'

const schema = z.object({
  symbol: z.string().min(1),
  startDate: z.string()
})

type Schema = z.output<typeof schema>

const state = reactive<Partial<Schema>>({
  symbol: undefined,
  startDate: undefined
})

async function onSubmit() {
  const result = schema.safeParse(state)
  if (!result.success) return
  // Submit form
}
</script>

<template>
  <UForm :state="state" :schema="schema" @submit="onSubmit">
    <UFormGroup label="Symbol" name="symbol">
      <UInput v-model="state.symbol" />
    </UFormGroup>
    <UButton type="submit">Run</UButton>
  </UForm>
</template>
```

### Toast Notifications

```typescript
const toast = useToast()

toast.add({
  title: 'Success',
  description: 'Backtest completed',
  color: 'green',
  icon: 'i-lucide-check-circle'
})
```

### Modal & Slideover

```typescript
const modal = useModal()

modal.open(ConfigModal, {
  // props
  onSubmit: (config) => runBacktest(config)
})
```

### Dark Mode

```typescript
const colorMode = useColorMode()
colorMode.preference = colorMode.preference === 'dark' ? 'light' : 'dark'
```

## Important Notes

### When Adding New Features

1. **Check auto-imports**: Many APIs don't need explicit imports
2. **Use NuxtUI components**: Prefer `UButton` over custom buttons for consistency
3. **Follow file conventions**: Place files in correct directories for auto-discovery
4. **Type everything**: Leverage TypeScript for better DX and fewer bugs
5. **Client-only charts**: Use `.client.vue` suffix for chart components (they depend on browser APIs)

### Performance

- Use `useFetch` with proper keys for caching
- Lazy-load components: `const Component = defineAsyncComponent(() => import('./Component.vue'))`
- Consider `<ClientOnly>` for client-side only components
- Use `.client.vue` or `.server.vue` suffixes for specific rendering

### Testing

- Run `pnpm typecheck` before committing
- Run `pnpm lint` to catch style issues
- Test in both light and dark modes

## Useful Links

- [Nuxt Documentation](https://nuxt.com/docs)
- [NuxtUI Documentation](https://ui.nuxt.com)
- [Tailwind CSS](https://tailwindcss.com/docs)
- [ApexCharts](https://apexcharts.com/)
- [Lightweight Charts](https://tradingview.github.io/lightweight-charts/)
- [Unovis](https://unovis.dev/)
- [VueUse Composables](https://vueuse.org/)
- [date-fns](https://date-fns.org/)
- [Zod](https://zod.dev/)
- [Iconify Icons](https://icon-sets.iconify.design/)
