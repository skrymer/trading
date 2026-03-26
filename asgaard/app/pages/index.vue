<script setup lang="ts">
</script>

<template>
  <UDashboardPanel id="home">
    <template #header>
      <UDashboardNavbar title="Trading Platform">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="max-w-4xl mx-auto py-12 px-6">
        <div class="text-center mb-12">
          <h1 class="text-4xl font-bold mb-4">
            Trading Platform
          </h1>
          <p class="text-xl text-muted">
            Backtest, scan, and manage systematic trading strategies
          </p>
        </div>

        <div class="space-y-8">
          <UCard>
            <template #header>
              <h2 class="text-2xl font-semibold">
                Platform Overview
              </h2>
            </template>
            <div class="prose prose-sm max-w-none space-y-4">
              <p>
                A full-stack platform for developing, validating, and executing systematic trading strategies.
                Build strategies with a composable DSL, backtest across 500+ stocks over 20 years of data,
                validate with Monte Carlo simulations and walk-forward analysis, then track live trades with
                real-time position management.
              </p>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                <NuxtLink to="/backtesting" class="p-4 bg-elevated rounded-lg hover:bg-muted/50 transition-colors block no-underline">
                  <h4 class="font-semibold mb-2">
                    Backtesting
                  </h4>
                  <p class="text-sm text-muted">
                    Test strategies across all stocks with position sizing, entry delay, and position limits.
                    Analyze edge consistency, sector performance, excursion metrics, and exit reason breakdowns.
                  </p>
                </NuxtLink>
                <NuxtLink to="/scanner" class="p-4 bg-elevated rounded-lg hover:bg-muted/50 transition-colors block no-underline">
                  <h4 class="font-semibold mb-2">
                    Scanner
                  </h4>
                  <p class="text-sm text-muted">
                    Scan for live entry signals across the market. ATR-based position sizing, drawdown-responsive
                    risk scaling, option contract lookup, and automated exit monitoring.
                  </p>
                </NuxtLink>
                <NuxtLink to="/portfolio" class="p-4 bg-elevated rounded-lg hover:bg-muted/50 transition-colors block no-underline">
                  <h4 class="font-semibold mb-2">
                    Portfolio
                  </h4>
                  <p class="text-sm text-muted">
                    Track live positions with IBKR broker sync, multi-currency FX tracking,
                    equity curves, and real-time unrealized P&L across stocks and options.
                  </p>
                </NuxtLink>
                <NuxtLink to="/breadth" class="p-4 bg-elevated rounded-lg hover:bg-muted/50 transition-colors block no-underline">
                  <h4 class="font-semibold mb-2">
                    Market Breadth
                  </h4>
                  <p class="text-sm text-muted">
                    Monitor market and sector breadth with EMA overlays, Donchian bands,
                    sector heat comparison, and uptrend/downtrend regime detection.
                  </p>
                </NuxtLink>
                <NuxtLink to="/stock-data" class="p-4 bg-elevated rounded-lg hover:bg-muted/50 transition-colors block no-underline">
                  <h4 class="font-semibold mb-2">
                    Stock Data
                  </h4>
                  <p class="text-sm text-muted">
                    Explore individual stocks with interactive charts, technical indicators,
                    order block detection, and strategy signal evaluation.
                  </p>
                </NuxtLink>
                <NuxtLink to="/data-manager" class="p-4 bg-elevated rounded-lg hover:bg-muted/50 transition-colors block no-underline">
                  <h4 class="font-semibold mb-2">
                    Data Manager
                  </h4>
                  <p class="text-sm text-muted">
                    Manage stock data ingestion, refresh OHLCV quotes with pre-computed indicators,
                    and recalculate market breadth statistics.
                  </p>
                </NuxtLink>
              </div>

              <h3 class="text-lg font-semibold mt-6 mb-3">
                Strategy Engine
              </h3>
              <p class="text-sm text-muted">
                Strategies are built with a composable DSL combining entry conditions (uptrend, volatility contraction,
                order blocks, volume, Donchian breakouts, market/sector breadth) with exit conditions (EMA cross,
                ATR stop loss, stagnation exits). Strategies are auto-discovered and available via the API and
                MCP server for Claude AI integration.
              </p>
            </div>
          </UCard>

          <div class="flex justify-center gap-3">
            <UButton
              to="/backtesting"
              size="lg"
              icon="i-lucide-trending-up"
            >
              Start Backtesting
            </UButton>
            <UButton
              to="/scanner"
              size="lg"
              variant="outline"
              icon="i-lucide-scan"
            >
              Open Scanner
            </UButton>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
