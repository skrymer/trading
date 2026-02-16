# Asgaard Frontend

Trading platform frontend built with Nuxt.js, providing a modern UI for backtesting, portfolio management, and market analysis.

## Tech Stack

- **Nuxt 4.1.2** - Vue.js meta-framework
- **TypeScript 5.9.3** - Type-safe JavaScript
- **NuxtUI 4.0.1** - UI component library
- **Vue 3** - Composition API with `<script setup>`
- **Tailwind CSS** - Utility-first styling
- **ApexCharts 5.3.5** - Interactive charts
- **Unovis 1.6.1** - Data visualization
- **date-fns 4.1.0** - Date utilities
- **Zod 4.1.11** - Schema validation

## Prerequisites

- Node.js 18+
- npm

## Setup

```bash
npm install
```

## Development

Start the development server on http://localhost:3000:

```bash
npm run dev
```

The backend (Udgaard) must be running on http://localhost:8080 for API calls to work.

## Code Quality

```bash
# TypeScript validation (zero errors required)
npm run typecheck

# ESLint check
npm run lint

# ESLint auto-fix
npm run lint -- --fix
```

## Production Build

```bash
npm run build
npm run preview  # Preview production build locally
```

## Pages

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | `/` | Overview and navigation |
| Backtesting | `/backtesting` | Configure and run backtests, view results |
| Portfolio | `/portfolio` | Manage portfolios and trades |
| Stock Data | `/stock-data` | View stock data and charts |
| Data Manager | `/data-manager` | Refresh stock data, view database stats |
| Settings | `/settings` | Application configuration (API keys, etc.) |
| App Metrics | `/app-metrics` | Application performance metrics |

## Project Structure

```
asgaard/
├── app/
│   ├── components/
│   │   ├── backtesting/        # Backtest config, results, charts
│   │   ├── portfolio/          # Portfolio and trade management
│   │   ├── charts/             # Reusable chart components
│   │   ├── data-management/    # Data refresh controls and stats
│   │   └── strategy/           # Strategy builder components
│   ├── layouts/
│   │   └── default.vue         # Main layout with sidebar
│   ├── pages/                  # File-based routing
│   ├── plugins/                # Nuxt plugins
│   ├── types/
│   │   ├── index.d.ts          # API type definitions
│   │   └── enums.ts            # Shared enums
│   ├── app.vue                 # Root component
│   └── error.vue               # Error page
├── nuxt.config.ts              # Nuxt configuration
├── package.json
└── claude.md                   # Frontend development guide for Claude
```

## Conventions

- **Composition API**: All components use `<script setup lang="ts">`
- **Auto-imports**: Nuxt auto-imports Vue composables and components
- **File-based routing**: Pages directory maps to routes
- **TypeScript strict mode**: All code must pass `typecheck`
- **ESLint**: No trailing commas, 1TBS brace style

## Related Documentation

- [Main Project README](../README.md)
- [Backend README](../udgaard/README.MD)
- [Frontend Development Guide](claude.md)
