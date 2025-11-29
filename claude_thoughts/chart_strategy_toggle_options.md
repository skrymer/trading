# Strategy Toggle UI Options for Stock Charts

Comprehensive guide for allowing users to switch between different trading strategies on stock charts.

---

## 1. Dropdown Selector (Simple & Clean)

### Option A: Single Strategy Dropdown

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">{{ symbol }} Backtest Results</h3>

        <!-- Strategy selector -->
        <USelectMenu
          v-model="selectedStrategy"
          :options="strategies"
          placeholder="Select strategy"
          value-attribute="id"
          option-attribute="name"
          class="w-64"
        >
          <template #label>
            <span class="flex items-center gap-2">
              <UIcon name="i-heroicons-chart-bar" />
              {{ selectedStrategy?.name || 'Select Strategy' }}
            </span>
          </template>

          <template #option="{ option }">
            <div class="flex items-center justify-between w-full">
              <div>
                <div class="font-medium">{{ option.name }}</div>
                <div class="text-xs text-gray-500">
                  {{ option.trades }} trades • {{ option.winRate }}% win rate
                </div>
              </div>
              <UBadge
                :color="option.returnPct > 0 ? 'green' : 'red'"
                variant="subtle"
              >
                {{ option.returnPct > 0 ? '+' : '' }}{{ option.returnPct }}%
              </UBadge>
            </div>
          </template>
        </USelectMenu>
      </div>
    </template>

    <!-- Chart with selected strategy data -->
    <Line :data="chartData" :options="chartOptions" class="h-96" />
  </UCard>
</template>

<script setup lang="ts">
import { Line } from 'vue-chartjs'

const strategies = ref([
  {
    id: 'vegard',
    name: 'VegardPlanEtf',
    trades: 33,
    winRate: 63.64,
    returnPct: 823.78,
    edge: 8.47,
    cooldown: 10
  },
  {
    id: 'ovtlyr',
    name: 'OvtlyrPlanEtf',
    trades: 43,
    winRate: 58.14,
    returnPct: 660.32,
    edge: 5.72,
    cooldown: 0
  },
  {
    id: 'planAlpha',
    name: 'PlanAlpha',
    trades: 28,
    winRate: 71.43,
    returnPct: 542.15,
    edge: 6.85,
    cooldown: 0
  }
])

const selectedStrategy = ref(strategies.value[0])

// Load backtest data when strategy changes
watch(selectedStrategy, async (newStrategy) => {
  await loadBacktestData(newStrategy.id)
})

const chartData = computed(() => ({
  // Use selectedStrategy data
  datasets: [
    priceDataset.value,
    ...getStrategyMarkers(selectedStrategy.value)
  ]
}))
</script>
```

**Pros:**
- ✓ Simple and intuitive
- ✓ Familiar UI pattern
- ✓ Shows strategy stats in dropdown
- ✓ Clean header layout

**Cons:**
- ✗ Can only view one at a time
- ✗ No side-by-side comparison

---

## 2. Button Group Toggle (Quick Switch)

### Option B: Horizontal Button Tabs

```vue
<template>
  <UCard>
    <template #header>
      <div class="space-y-3">
        <h3 class="text-lg font-semibold">{{ symbol }} Strategy Comparison</h3>

        <!-- Strategy toggle buttons -->
        <UButtonGroup size="sm" class="w-full">
          <UButton
            v-for="strategy in strategies"
            :key="strategy.id"
            :color="selectedStrategy?.id === strategy.id ? 'primary' : 'gray'"
            @click="selectedStrategy = strategy"
            class="flex-1"
          >
            <div class="flex flex-col items-center">
              <span class="font-medium">{{ strategy.name }}</span>
              <span class="text-xs opacity-75">
                {{ strategy.returnPct > 0 ? '+' : '' }}{{ strategy.returnPct.toFixed(0) }}%
              </span>
            </div>
          </UButton>
        </UButtonGroup>

        <!-- Selected strategy details -->
        <div class="flex items-center gap-4 text-sm text-gray-600">
          <span>{{ selectedStrategy.trades }} trades</span>
          <span>{{ selectedStrategy.winRate }}% win rate</span>
          <span>{{ selectedStrategy.edge }}% edge</span>
          <span v-if="selectedStrategy.cooldown > 0">
            {{ selectedStrategy.cooldown }} day cooldown
          </span>
        </div>
      </div>
    </template>

    <Line :data="chartData" :options="chartOptions" class="h-96" />
  </UCard>
</template>

<script setup lang="ts">
const strategies = ref([
  {
    id: 'vegard',
    name: 'VegardPlanEtf',
    trades: 33,
    winRate: 63.64,
    returnPct: 823.78,
    edge: 8.47,
    cooldown: 10
  },
  {
    id: 'ovtlyr',
    name: 'OvtlyrPlanEtf',
    trades: 43,
    winRate: 58.14,
    returnPct: 660.32,
    edge: 5.72,
    cooldown: 0
  }
])

const selectedStrategy = ref(strategies.value[0])
</script>
```

**Pros:**
- ✓ Very fast switching
- ✓ Shows return at a glance
- ✓ Clean visual hierarchy

**Cons:**
- ✗ Limited space for many strategies
- ✗ Can get crowded on mobile

---

## 3. Multi-Select Comparison (Overlay Multiple Strategies)

### Option C: Checkbox Multi-Select with Overlay

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">Strategy Comparison</h3>

        <!-- Multi-select strategies -->
        <UPopover :popper="{ placement: 'bottom-end' }">
          <UButton
            icon="i-heroicons-adjustments-horizontal"
            trailing-icon="i-heroicons-chevron-down"
            color="gray"
          >
            Strategies ({{ selectedStrategies.length }})
          </UButton>

          <template #panel>
            <div class="p-4 space-y-2 min-w-[300px]">
              <div
                v-for="strategy in strategies"
                :key="strategy.id"
                class="flex items-center justify-between p-2 rounded hover:bg-gray-100 cursor-pointer"
                @click="toggleStrategy(strategy)"
              >
                <div class="flex items-center gap-3">
                  <UCheckbox
                    :model-value="isStrategySelected(strategy.id)"
                    :color="strategy.color"
                  />
                  <div>
                    <div class="font-medium">{{ strategy.name }}</div>
                    <div class="text-xs text-gray-500">
                      {{ strategy.trades }} trades •
                      {{ strategy.returnPct > 0 ? '+' : '' }}{{ strategy.returnPct.toFixed(1) }}%
                    </div>
                  </div>
                </div>
                <div
                  class="w-3 h-3 rounded-full"
                  :style="{ backgroundColor: strategy.color }"
                />
              </div>

              <UDivider />

              <div class="flex gap-2">
                <UButton
                  size="xs"
                  color="gray"
                  block
                  @click="selectAllStrategies"
                >
                  Select All
                </UButton>
                <UButton
                  size="xs"
                  color="gray"
                  block
                  @click="clearAllStrategies"
                >
                  Clear All
                </UButton>
              </div>
            </div>
          </template>
        </UPopover>
      </div>
    </template>

    <!-- Chart with multiple strategies overlaid -->
    <Line :data="chartData" :options="chartOptions" class="h-96" />

    <!-- Strategy legend -->
    <template #footer>
      <div class="flex flex-wrap gap-2">
        <UBadge
          v-for="strategy in selectedStrategies"
          :key="strategy.id"
          :style="{ backgroundColor: strategy.color }"
          class="cursor-pointer"
          @click="removeStrategy(strategy)"
        >
          <div class="flex items-center gap-2">
            <span>{{ strategy.name }}</span>
            <UIcon name="i-heroicons-x-mark" class="w-3 h-3" />
          </div>
        </UBadge>
      </div>
    </template>
  </UCard>
</template>

<script setup lang="ts">
const strategies = ref([
  {
    id: 'vegard',
    name: 'VegardPlanEtf',
    trades: 33,
    winRate: 63.64,
    returnPct: 823.78,
    color: '#3b82f6'  // Blue
  },
  {
    id: 'ovtlyr',
    name: 'OvtlyrPlanEtf',
    trades: 43,
    winRate: 58.14,
    returnPct: 660.32,
    color: '#10b981'  // Green
  },
  {
    id: 'planAlpha',
    name: 'PlanAlpha',
    trades: 28,
    winRate: 71.43,
    returnPct: 542.15,
    color: '#f59e0b'  // Orange
  }
])

const selectedStrategies = ref([strategies.value[0]])

function toggleStrategy(strategy) {
  const index = selectedStrategies.value.findIndex(s => s.id === strategy.id)
  if (index >= 0) {
    selectedStrategies.value.splice(index, 1)
  } else {
    selectedStrategies.value.push(strategy)
  }
}

function isStrategySelected(id: string) {
  return selectedStrategies.value.some(s => s.id === id)
}

const chartData = computed(() => ({
  datasets: [
    // Price data
    priceDataset.value,

    // Overlay each selected strategy
    ...selectedStrategies.value.flatMap(strategy => [
      {
        label: `${strategy.name} - Entries`,
        data: getEntryPoints(strategy.id),
        pointStyle: 'triangle',
        pointRadius: 6,
        pointBackgroundColor: strategy.color,
        showLine: false
      },
      {
        label: `${strategy.name} - Exits`,
        data: getExitPoints(strategy.id),
        pointStyle: 'triangle',
        pointRotation: 180,
        pointRadius: 6,
        pointBackgroundColor: strategy.color,
        showLine: false
      }
    ])
  ]
}))
</script>
```

**Pros:**
- ✓ Compare multiple strategies at once
- ✓ Color-coded for clarity
- ✓ Flexible selection
- ✓ Great for analysis

**Cons:**
- ✗ Can get cluttered with many strategies
- ✗ More complex UI
- ✗ Overlapping markers can be confusing

---

## 4. Tabs with Strategy Details

### Option D: Full Tab Interface

```vue
<template>
  <UCard>
    <UTabs v-model="selectedTab" :items="tabs">
      <!-- Tab content for each strategy -->
      <template #item="{ item }">
        <div class="space-y-4">
          <!-- Strategy stats -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <UCard>
              <div class="text-sm text-gray-600">Total Return</div>
              <div class="text-2xl font-bold" :class="item.strategy.returnPct > 0 ? 'text-green-600' : 'text-red-600'">
                {{ item.strategy.returnPct > 0 ? '+' : '' }}{{ item.strategy.returnPct.toFixed(2) }}%
              </div>
            </UCard>

            <UCard>
              <div class="text-sm text-gray-600">Win Rate</div>
              <div class="text-2xl font-bold">{{ item.strategy.winRate }}%</div>
            </UCard>

            <UCard>
              <div class="text-sm text-gray-600">Total Trades</div>
              <div class="text-2xl font-bold">{{ item.strategy.trades }}</div>
            </UCard>

            <UCard>
              <div class="text-sm text-gray-600">Edge</div>
              <div class="text-2xl font-bold">{{ item.strategy.edge }}%</div>
            </UCard>
          </div>

          <!-- Chart -->
          <Line
            :data="getChartDataForStrategy(item.strategy)"
            :options="chartOptions"
            class="h-96"
          />

          <!-- Trade list -->
          <UCard>
            <template #header>
              <h4 class="font-semibold">Recent Trades</h4>
            </template>
            <UTable
              :rows="item.strategy.recentTrades"
              :columns="tradeColumns"
            />
          </UCard>
        </div>
      </template>
    </UTabs>
  </UCard>
</template>

<script setup lang="ts">
import { Line } from 'vue-chartjs'

const strategies = ref([
  {
    id: 'vegard',
    name: 'VegardPlanEtf',
    trades: 33,
    winRate: 63.64,
    returnPct: 823.78,
    edge: 8.47,
    maxDrawdown: 30.44,
    recentTrades: []
  },
  {
    id: 'ovtlyr',
    name: 'OvtlyrPlanEtf',
    trades: 43,
    winRate: 58.14,
    returnPct: 660.32,
    edge: 5.72,
    maxDrawdown: 19.55,
    recentTrades: []
  }
])

const tabs = computed(() => strategies.value.map(strategy => ({
  label: strategy.name,
  icon: 'i-heroicons-chart-bar',
  badge: `${strategy.returnPct > 0 ? '+' : ''}${strategy.returnPct.toFixed(0)}%`,
  strategy
})))

const selectedTab = ref(0)

const tradeColumns = [
  { key: 'entryDate', label: 'Entry' },
  { key: 'exitDate', label: 'Exit' },
  { key: 'profit', label: 'Profit' },
  { key: 'reason', label: 'Exit Reason' }
]
</script>
```

**Pros:**
- ✓ Dedicated space per strategy
- ✓ Can show detailed stats
- ✓ Clean organization
- ✓ Shows trade lists

**Cons:**
- ✗ Can't compare side-by-side
- ✗ More clicks to switch
- ✗ Takes more vertical space

---

## 5. Side-by-Side Comparison View

### Option E: Split Screen Comparison

```vue
<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
    <!-- Strategy 1 -->
    <UCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold">Strategy A</h3>
            <USelectMenu
              v-model="strategyA"
              :options="strategies"
              option-attribute="name"
              class="mt-2"
            />
          </div>
          <UBadge
            :color="strategyA.returnPct > 0 ? 'green' : 'red'"
            size="lg"
          >
            {{ strategyA.returnPct > 0 ? '+' : '' }}{{ strategyA.returnPct.toFixed(1) }}%
          </UBadge>
        </div>
      </template>

      <Line :data="getChartData(strategyA)" :options="chartOptions" class="h-64" />

      <template #footer>
        <div class="grid grid-cols-3 gap-2 text-sm">
          <div>
            <div class="text-gray-600">Trades</div>
            <div class="font-semibold">{{ strategyA.trades }}</div>
          </div>
          <div>
            <div class="text-gray-600">Win Rate</div>
            <div class="font-semibold">{{ strategyA.winRate }}%</div>
          </div>
          <div>
            <div class="text-gray-600">Edge</div>
            <div class="font-semibold">{{ strategyA.edge }}%</div>
          </div>
        </div>
      </template>
    </UCard>

    <!-- Strategy 2 -->
    <UCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold">Strategy B</h3>
            <USelectMenu
              v-model="strategyB"
              :options="strategies"
              option-attribute="name"
              class="mt-2"
            />
          </div>
          <UBadge
            :color="strategyB.returnPct > 0 ? 'green' : 'red'"
            size="lg"
          >
            {{ strategyB.returnPct > 0 ? '+' : '' }}{{ strategyB.returnPct.toFixed(1) }}%
          </UBadge>
        </div>
      </template>

      <Line :data="getChartData(strategyB)" :options="chartOptions" class="h-64" />

      <template #footer>
        <div class="grid grid-cols-3 gap-2 text-sm">
          <div>
            <div class="text-gray-600">Trades</div>
            <div class="font-semibold">{{ strategyB.trades }}</div>
          </div>
          <div>
            <div class="text-gray-600">Win Rate</div>
            <div class="font-semibold">{{ strategyB.winRate }}%</div>
          </div>
          <div>
            <div class="text-gray-600">Edge</div>
            <div class="font-semibold">{{ strategyB.edge }}%</div>
          </div>
        </div>
      </template>
    </UCard>

    <!-- Comparison summary -->
    <UCard class="lg:col-span-2">
      <template #header>
        <h3 class="font-semibold">Head-to-Head Comparison</h3>
      </template>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="text-center p-4 rounded-lg bg-gray-50">
          <div class="text-sm text-gray-600 mb-2">Better Returns</div>
          <div class="text-xl font-bold" :class="strategyA.returnPct > strategyB.returnPct ? 'text-blue-600' : 'text-green-600'">
            {{ strategyA.returnPct > strategyB.returnPct ? strategyA.name : strategyB.name }}
          </div>
          <div class="text-sm text-gray-500">
            {{ Math.abs(strategyA.returnPct - strategyB.returnPct).toFixed(1) }}% difference
          </div>
        </div>

        <div class="text-center p-4 rounded-lg bg-gray-50">
          <div class="text-sm text-gray-600 mb-2">Better Win Rate</div>
          <div class="text-xl font-bold" :class="strategyA.winRate > strategyB.winRate ? 'text-blue-600' : 'text-green-600'">
            {{ strategyA.winRate > strategyB.winRate ? strategyA.name : strategyB.name }}
          </div>
          <div class="text-sm text-gray-500">
            {{ Math.abs(strategyA.winRate - strategyB.winRate).toFixed(1) }}% difference
          </div>
        </div>

        <div class="text-center p-4 rounded-lg bg-gray-50">
          <div class="text-sm text-gray-600 mb-2">Better Risk-Adjusted</div>
          <div class="text-xl font-bold" :class="getReturnDrawdownRatio(strategyA) > getReturnDrawdownRatio(strategyB) ? 'text-blue-600' : 'text-green-600'">
            {{ getReturnDrawdownRatio(strategyA) > getReturnDrawdownRatio(strategyB) ? strategyA.name : strategyB.name }}
          </div>
          <div class="text-sm text-gray-500">
            {{ Math.abs(getReturnDrawdownRatio(strategyA) - getReturnDrawdownRatio(strategyB)).toFixed(2) }} ratio difference
          </div>
        </div>
      </div>
    </UCard>
  </div>
</template>

<script setup lang="ts">
const strategyA = ref(strategies.value[0])
const strategyB = ref(strategies.value[1])

function getReturnDrawdownRatio(strategy) {
  return strategy.returnPct / strategy.maxDrawdown
}
</script>
```

**Pros:**
- ✓ Direct visual comparison
- ✓ See both strategies simultaneously
- ✓ Shows winner in each category
- ✓ Great for decision making

**Cons:**
- ✗ Requires more screen space
- ✗ Limited to 2 strategies
- ✗ Can be overwhelming on mobile

---

## 6. Toggle with Comparison Mode

### Option F: Single View + Comparison Toggle

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <div class="space-y-2">
          <h3 class="text-lg font-semibold">{{ symbol }} Strategy Analysis</h3>

          <!-- View mode toggle -->
          <UButtonGroup size="sm">
            <UButton
              :color="viewMode === 'single' ? 'primary' : 'gray'"
              @click="viewMode = 'single'"
            >
              Single View
            </UButton>
            <UButton
              :color="viewMode === 'compare' ? 'primary' : 'gray'"
              @click="viewMode = 'compare'"
            >
              Compare ({{ selectedStrategies.length }})
            </UButton>
          </UButtonGroup>
        </div>

        <!-- Strategy selector (changes based on mode) -->
        <USelectMenu
          v-if="viewMode === 'single'"
          v-model="selectedStrategy"
          :options="strategies"
          option-attribute="name"
          class="w-64"
        />

        <UPopover v-else>
          <UButton trailing-icon="i-heroicons-chevron-down" color="gray">
            Select Strategies
          </UButton>
          <template #panel>
            <div class="p-4 space-y-2">
              <UCheckbox
                v-for="strategy in strategies"
                :key="strategy.id"
                v-model="strategy.selected"
                :label="strategy.name"
              />
            </div>
          </template>
        </UPopover>
      </div>
    </template>

    <!-- Single strategy view -->
    <div v-if="viewMode === 'single'">
      <Line :data="singleChartData" :options="chartOptions" class="h-96" />
    </div>

    <!-- Comparison view -->
    <div v-else>
      <Line :data="comparisonChartData" :options="comparisonChartOptions" class="h-96" />

      <!-- Comparison table -->
      <UTable
        :rows="comparisonTableData"
        :columns="comparisonColumns"
        class="mt-4"
      />
    </div>
  </UCard>
</template>

<script setup lang="ts">
const viewMode = ref<'single' | 'compare'>('single')

const selectedStrategy = ref(strategies.value[0])

const selectedStrategies = computed(() =>
  strategies.value.filter(s => s.selected)
)

const comparisonTableData = computed(() => [
  {
    metric: 'Total Return',
    ...Object.fromEntries(
      selectedStrategies.value.map(s => [s.id, `${s.returnPct.toFixed(2)}%`])
    )
  },
  {
    metric: 'Win Rate',
    ...Object.fromEntries(
      selectedStrategies.value.map(s => [s.id, `${s.winRate}%`])
    )
  },
  {
    metric: 'Total Trades',
    ...Object.fromEntries(
      selectedStrategies.value.map(s => [s.id, s.trades])
    )
  },
  {
    metric: 'Edge per Trade',
    ...Object.fromEntries(
      selectedStrategies.value.map(s => [s.id, `${s.edge}%`])
    )
  }
])

const comparisonColumns = computed(() => [
  { key: 'metric', label: 'Metric' },
  ...selectedStrategies.value.map(s => ({
    key: s.id,
    label: s.name
  }))
])
</script>
```

**Pros:**
- ✓ Flexible single/multi view
- ✓ Includes comparison table
- ✓ User chooses view mode
- ✓ Space efficient

**Cons:**
- ✗ More complex state management
- ✗ Requires toggling modes

---

## 7. Slide-Over Panel (Mobile-Friendly)

### Option G: Strategy Panel Drawer

```vue
<template>
  <div>
    <!-- Main chart -->
    <UCard>
      <template #header>
        <div class="flex items-center justify-between">
          <h3 class="text-lg font-semibold">{{ selectedStrategy.name }}</h3>

          <UButton
            icon="i-heroicons-adjustments-horizontal"
            color="gray"
            @click="isStrategyPanelOpen = true"
          >
            Change Strategy
          </UButton>
        </div>
      </template>

      <Line :data="chartData" :options="chartOptions" class="h-96" />
    </UCard>

    <!-- Strategy selection slide-over -->
    <USlideover v-model="isStrategyPanelOpen">
      <UCard
        class="flex flex-col flex-1"
        :ui="{
          body: { base: 'flex-1' },
          ring: '',
          divide: 'divide-y divide-gray-100'
        }"
      >
        <template #header>
          <div class="flex items-center justify-between">
            <h3 class="text-base font-semibold">Select Strategy</h3>
            <UButton
              color="gray"
              variant="ghost"
              icon="i-heroicons-x-mark"
              @click="isStrategyPanelOpen = false"
            />
          </div>
        </template>

        <!-- Strategy list -->
        <div class="space-y-2">
          <div
            v-for="strategy in strategies"
            :key="strategy.id"
            class="p-4 rounded-lg border-2 cursor-pointer transition-all"
            :class="selectedStrategy.id === strategy.id
              ? 'border-primary-500 bg-primary-50'
              : 'border-gray-200 hover:border-gray-300'"
            @click="selectStrategy(strategy)"
          >
            <div class="flex items-start justify-between mb-2">
              <div>
                <h4 class="font-semibold">{{ strategy.name }}</h4>
                <p class="text-sm text-gray-600">{{ strategy.description }}</p>
              </div>
              <UIcon
                v-if="selectedStrategy.id === strategy.id"
                name="i-heroicons-check-circle"
                class="w-5 h-5 text-primary-500"
              />
            </div>

            <!-- Strategy stats -->
            <div class="grid grid-cols-2 gap-2 text-sm mt-3">
              <div>
                <div class="text-gray-600">Return</div>
                <div class="font-semibold" :class="strategy.returnPct > 0 ? 'text-green-600' : 'text-red-600'">
                  {{ strategy.returnPct > 0 ? '+' : '' }}{{ strategy.returnPct.toFixed(1) }}%
                </div>
              </div>
              <div>
                <div class="text-gray-600">Win Rate</div>
                <div class="font-semibold">{{ strategy.winRate }}%</div>
              </div>
              <div>
                <div class="text-gray-600">Trades</div>
                <div class="font-semibold">{{ strategy.trades }}</div>
              </div>
              <div>
                <div class="text-gray-600">Edge</div>
                <div class="font-semibold">{{ strategy.edge }}%</div>
              </div>
            </div>

            <!-- Performance badge -->
            <UBadge
              :color="getRankColor(strategy.rank)"
              variant="subtle"
              class="mt-3"
            >
              {{ getRankLabel(strategy.rank) }}
            </UBadge>
          </div>
        </div>

        <template #footer>
          <UButton
            block
            @click="applyStrategy"
          >
            Apply Strategy
          </UButton>
        </template>
      </UCard>
    </USlideover>
  </div>
</template>

<script setup lang="ts">
const isStrategyPanelOpen = ref(false)
const selectedStrategy = ref(strategies.value[0])
const tempStrategy = ref(selectedStrategy.value)

function selectStrategy(strategy) {
  tempStrategy.value = strategy
}

function applyStrategy() {
  selectedStrategy.value = tempStrategy.value
  isStrategyPanelOpen.value = false
  loadBacktestData(selectedStrategy.value.id)
}

function getRankColor(rank: number) {
  if (rank === 1) return 'green'
  if (rank === 2) return 'blue'
  return 'gray'
}

function getRankLabel(rank: number) {
  if (rank === 1) return '🏆 Best Performer'
  if (rank === 2) return '⭐ Strong Performer'
  return 'Good Performer'
}
</script>
```

**Pros:**
- ✓ Mobile-friendly
- ✓ Rich strategy info
- ✓ Clean main view
- ✓ Good for many strategies

**Cons:**
- ✗ Requires opening panel
- ✗ Can't compare directly

---

## Recommended Implementation

### Best Approach: **Hybrid Toggle System**

Combine the best of multiple options:

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <!-- Title and view mode -->
        <div class="flex items-center gap-4">
          <h3 class="text-lg font-semibold">{{ symbol }} Strategy Analysis</h3>

          <UButtonGroup size="xs" class="hidden md:flex">
            <UButton
              :color="viewMode === 'single' ? 'primary' : 'gray'"
              @click="viewMode = 'single'"
              icon="i-heroicons-chart-bar"
            >
              Single
            </UButton>
            <UButton
              :color="viewMode === 'overlay' ? 'primary' : 'gray'"
              @click="viewMode = 'overlay'"
              icon="i-heroicons-chart-bar-square"
            >
              Overlay
            </UButton>
            <UButton
              :color="viewMode === 'sidebyside' ? 'primary' : 'gray'"
              @click="viewMode = 'sidebyside'"
              icon="i-heroicons-squares-2x2"
            >
              Side by Side
            </UButton>
          </UButtonGroup>
        </div>

        <!-- Strategy controls (changes based on view mode) -->
        <div class="flex items-center gap-2">
          <!-- Single view: dropdown -->
          <USelectMenu
            v-if="viewMode === 'single'"
            v-model="primaryStrategy"
            :options="strategies"
            value-attribute="id"
            option-attribute="name"
            class="w-48"
          >
            <template #label>
              <span class="flex items-center gap-2">
                <div
                  class="w-3 h-3 rounded-full"
                  :style="{ backgroundColor: primaryStrategy.color }"
                />
                {{ primaryStrategy.name }}
              </span>
            </template>
          </USelectMenu>

          <!-- Overlay view: multi-select -->
          <UPopover v-else-if="viewMode === 'overlay'">
            <UButton
              trailing-icon="i-heroicons-chevron-down"
              color="gray"
            >
              Strategies ({{ selectedStrategies.length }})
            </UButton>
            <template #panel>
              <div class="p-4 space-y-2 min-w-[280px]">
                <div
                  v-for="strategy in strategies"
                  :key="strategy.id"
                  class="flex items-center gap-3 p-2 rounded hover:bg-gray-50 cursor-pointer"
                  @click="toggleStrategy(strategy)"
                >
                  <UCheckbox
                    :model-value="isSelected(strategy.id)"
                    :color="strategy.color"
                  />
                  <div
                    class="w-3 h-3 rounded-full flex-shrink-0"
                    :style="{ backgroundColor: strategy.color }"
                  />
                  <span class="flex-1">{{ strategy.name }}</span>
                  <span class="text-xs text-gray-500">
                    {{ strategy.returnPct > 0 ? '+' : '' }}{{ strategy.returnPct.toFixed(0) }}%
                  </span>
                </div>
              </div>
            </template>
          </UPopover>

          <!-- Side by side: two dropdowns -->
          <template v-else>
            <USelectMenu
              v-model="strategyA"
              :options="strategies"
              option-attribute="name"
              placeholder="Strategy A"
              class="w-40"
            />
            <span class="text-gray-400">vs</span>
            <USelectMenu
              v-model="strategyB"
              :options="strategies"
              option-attribute="name"
              placeholder="Strategy B"
              class="w-40"
            />
          </template>
        </div>
      </div>
    </template>

    <!-- Dynamic chart based on view mode -->
    <component
      :is="chartComponent"
      :strategies="activeStrategies"
      :price-data="priceData"
      class="h-96"
    />

    <!-- Strategy legend (for overlay mode) -->
    <template v-if="viewMode === 'overlay'" #footer>
      <div class="flex flex-wrap gap-2">
        <UBadge
          v-for="strategy in selectedStrategies"
          :key="strategy.id"
          :style="{
            backgroundColor: strategy.color,
            color: 'white'
          }"
        >
          {{ strategy.name }}: {{ strategy.returnPct > 0 ? '+' : '' }}{{ strategy.returnPct.toFixed(1) }}%
        </UBadge>
      </div>
    </template>
  </UCard>
</template>

<script setup lang="ts">
const viewMode = ref<'single' | 'overlay' | 'sidebyside'>('single')

const strategies = ref([
  {
    id: 'vegard',
    name: 'VegardPlanEtf',
    color: '#3b82f6',
    trades: 33,
    winRate: 63.64,
    returnPct: 823.78,
    edge: 8.47
  },
  {
    id: 'ovtlyr',
    name: 'OvtlyrPlanEtf',
    color: '#10b981',
    trades: 43,
    winRate: 58.14,
    returnPct: 660.32,
    edge: 5.72
  },
  {
    id: 'planAlpha',
    name: 'PlanAlpha',
    color: '#f59e0b',
    trades: 28,
    winRate: 71.43,
    returnPct: 542.15,
    edge: 6.85
  }
])

// Single view
const primaryStrategy = ref(strategies.value[0])

// Overlay view
const selectedStrategies = ref([strategies.value[0]])

function toggleStrategy(strategy) {
  const index = selectedStrategies.value.findIndex(s => s.id === strategy.id)
  if (index >= 0) {
    if (selectedStrategies.value.length > 1) {
      selectedStrategies.value.splice(index, 1)
    }
  } else {
    selectedStrategies.value.push(strategy)
  }
}

function isSelected(id: string) {
  return selectedStrategies.value.some(s => s.id === id)
}

// Side by side view
const strategyA = ref(strategies.value[0])
const strategyB = ref(strategies.value[1])

// Active strategies based on mode
const activeStrategies = computed(() => {
  switch (viewMode.value) {
    case 'single':
      return [primaryStrategy.value]
    case 'overlay':
      return selectedStrategies.value
    case 'sidebyside':
      return [strategyA.value, strategyB.value]
    default:
      return [primaryStrategy.value]
  }
})

// Chart component based on mode
const chartComponent = computed(() => {
  switch (viewMode.value) {
    case 'single':
      return SingleStrategyChart
    case 'overlay':
      return OverlayStrategyChart
    case 'sidebyside':
      return SideBySideChart
    default:
      return SingleStrategyChart
  }
})
</script>
```

---

## Summary & Recommendation

For your backtesting platform, I recommend the **Hybrid Toggle System**:

### Features:
1. **Three view modes:**
   - Single: Focus on one strategy (dropdown selector)
   - Overlay: Compare multiple strategies on same chart (multi-select)
   - Side by Side: Compare two strategies directly (dual dropdowns)

2. **Adaptive UI:**
   - Controls change based on view mode
   - Clean and intuitive
   - Works on mobile and desktop

3. **Color-coded:**
   - Each strategy gets a unique color
   - Easy to distinguish in overlay mode
   - Consistent across all views

4. **Performance-focused:**
   - Shows returns in selectors
   - Quick visual comparison
   - Stats available on hover

Would you like me to implement this for your backtesting page?
