<script setup lang="ts">
</script>

<template>
  <UDashboardPanel id="home">
    <template #header>
      <UDashboardNavbar title="Trading Strategy Backtesting Platform">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="max-w-4xl mx-auto py-12 px-6">
        <div class="text-center mb-12">
          <h1 class="text-4xl font-bold mb-4">
            Trading Strategy Backtesting
          </h1>
          <p class="text-xl text-muted">
            Advanced backtesting platform for systematic trading strategies
          </p>
        </div>

        <div class="space-y-8">
          <UCard>
            <template #header>
              <h2 class="text-2xl font-semibold">
                About This Platform
              </h2>
            </template>
            <div class="prose prose-sm max-w-none space-y-4">
              <p>
                This platform provides comprehensive backtesting capabilities for trading strategies on QQQ and other instruments.
                Analyze historical performance, optimize entry and exit strategies, and evaluate risk-adjusted returns with leverage.
              </p>

              <h3 class="text-lg font-semibold mt-6 mb-3">
                Key Features
              </h3>
              <ul class="space-y-2">
                <li><strong>Strategy Testing:</strong> Backtest multiple entry and exit strategies including PlanEtf, PlanAlpha, and custom strategies</li>
                <li><strong>Leverage Analysis:</strong> Simulate performance with 1x to 5x leverage to understand risk and return profiles</li>
                <li><strong>Performance Metrics:</strong> Track CAGR, win rate, max drawdown, edge, and other critical metrics</li>
                <li><strong>Equity Curve Visualization:</strong> Real-time equity curve charts showing portfolio growth over time</li>
                <li><strong>Trade Analysis:</strong> Detailed trade-by-trade breakdown with entry/exit dates, profit/loss, and exit reasons</li>
                <li><strong>Market Context:</strong> Incorporates heatmaps, EMAs, ATR, and order blocks</li>
              </ul>

              <h3 class="text-lg font-semibold mt-6 mb-3">
                Strategy Components
              </h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                <div class="p-4 bg-elevated rounded-lg">
                  <h4 class="font-semibold mb-2">
                    Entry Strategies
                  </h4>
                  <ul class="text-sm space-y-1 text-muted">
                    <li>• PlanEtf - Multi-factor entry with order blocks</li>
                    <li>• PlanAlpha - Alpha-focused systematic entry</li>
                    <li>• PlanBeta - Beta-focused market entry</li>
                    <li>• SimpleBuySignal - Basic signal-based entry</li>
                  </ul>
                </div>
                <div class="p-4 bg-elevated rounded-lg">
                  <h4 class="font-semibold mb-2">
                    Exit Strategies
                  </h4>
                  <ul class="text-sm space-y-1 text-muted">
                    <li>• PlanEtf - Composite exit with profit targets</li>
                    <li>• PlanMoney - Money management exits</li>
                    <li>• SellSignal - Signal-based exits</li>
                    <li>• ATR-based stops and trailing exits</li>
                  </ul>
                </div>
              </div>

              <h3 class="text-lg font-semibold mt-6 mb-3">
                Recent Performance
              </h3>
              <p class="text-sm text-muted">
                <strong>PlanEtf Strategy (2020-2025):</strong> Starting with $100,000, the PlanEtf strategy achieved
                $248,248 (+148%) with QQQ (1x) or $1,241,994 (+1,142%) with TQQQ (3x leverage).
                The strategy maintained exceptional risk management with only -4.22% max drawdown on QQQ and -12.66% on TQQQ,
                achieving a 67% win rate over 70 trades.
              </p>
            </div>
          </UCard>

          <div class="text-center">
            <UButton
              to="/backtesting"
              size="lg"
              icon="i-lucide-trending-up"
            >
              Start Backtesting
            </UButton>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
