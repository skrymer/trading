<template>
  <UDashboardPanel id="breadth">
    <template #header>
      <UDashboardNavbar title="Breadth Analysis">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <div class="flex items-center gap-2">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
              Time Range:
            </label>
            <USelectMenu
              v-model="timeRange"
              :items="[
                { label: '1 Week', value: 0.25 },
                { label: '1 Month', value: 1 },
                { label: '3 Months', value: 3 },
                { label: '6 Months', value: 6 },
                { label: '1 Year', value: 12 },
                { label: '2 Years', value: 24 },
                { label: 'All Time', value: 1000 }
              ]"
              value-attribute="value"
              class="w-36"
            />
          </div>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-12">
        <UIcon name="i-heroicons-arrow-path" class="animate-spin text-4xl" />
      </div>

      <div v-else class="space-y-6">
        <!-- Section A: Market Breadth Chart -->
        <div v-if="marketBreadthSeries.length > 0">
          <h3 class="text-lg font-semibold mb-2">
            Market Breadth
          </h3>
          <ChartsLineChart
            :series="marketBreadthSeries"
            :categories="marketBreadthCategories"
            :line-colors="['#eab308', '#10b981', '#ef4444']"
            :height="400"
            :percent-mode="true"
          />
        </div>

        <!-- Section B: Sector Heat Summary -->
        <div v-if="sectorHeatData.length > 0">
          <h3 class="text-lg font-semibold mb-2">
            Sector Heat Summary
          </h3>
          <UCard>
            <UTable :data="sectorHeatData" :columns="heatColumns" />
          </UCard>
        </div>

        <!-- Section C: Sector Breadth Charts -->
        <div v-if="Object.keys(sectorBreadthMap).length > 0">
          <h3 class="text-lg font-semibold mb-2">
            Sector Breadth
          </h3>
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div v-for="sector in SECTORS" :key="sector">
              <h4
                class="text-md font-medium mb-2 flex items-center gap-2 cursor-pointer hover:text-primary"
                @click="openSectorModal(sector)"
              >
                {{ SECTOR_NAMES[sector] || sector }} ({{ sector }})
                <UBadge v-if="sectorEmaStatus(sector) === 'hot'" color="success" variant="subtle">
                  🔥
                </UBadge>
                <UBadge v-else-if="sectorEmaStatus(sector) === 'cold'" color="error" variant="subtle">
                  ❄️
                </UBadge>
              </h4>
              <ChartsLineChart
                v-if="sectorSeries(sector).length > 0"
                :series="sectorSeries(sector)"
                :categories="sectorCategories(sector)"
                :line-colors="['#eab308', '#10b981', '#ef4444']"
                :height="300"
                :percent-mode="true"
              />
            </div>
          </div>
        </div>

        <!-- Fullscreen Sector Modal -->
        <UModal
          v-model:open="sectorModalOpen"
          fullscreen
          :title="sectorModalTitle"
        >
          <template #body>
            <ChartsLineChart
              v-if="sectorModalSector && sectorSeries(sectorModalSector).length > 0"
              :series="sectorSeries(sectorModalSector)"
              :categories="sectorCategories(sectorModalSector)"
              :line-colors="['#eab308', '#10b981', '#ef4444']"
              :height="800"
              :percent-mode="true"
            />
          </template>
        </UModal>
      </div>
    </template>
  </UDashboardPanel>
</template>

<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { MarketBreadthDaily, SectorBreadthDaily } from '~/types'

definePageMeta({
  layout: 'default'
})

const SECTORS = ['XLB', 'XLC', 'XLE', 'XLF', 'XLI', 'XLK', 'XLP', 'XLRE', 'XLU', 'XLV', 'XLY']

const SECTOR_NAMES: Record<string, string> = {
  XLB: 'Materials',
  XLC: 'Communication Services',
  XLE: 'Energy',
  XLF: 'Financials',
  XLI: 'Industrials',
  XLK: 'Technology',
  XLP: 'Consumer Staples',
  XLRE: 'Real Estate',
  XLU: 'Utilities',
  XLV: 'Health Care',
  XLY: 'Consumer Discretionary'
}

// State
const loading = ref(false)
const timeRange = ref<{ label: string, value: number }>({ label: '3 Months', value: 3 })
const marketBreadthData = ref<MarketBreadthDaily[]>([])
const sectorBreadthMap = ref<Record<string, SectorBreadthDaily[]>>({})
const sectorModalOpen = ref(false)
const sectorModalSector = ref<string | null>(null)

const sectorModalTitle = computed(() => {
  if (!sectorModalSector.value) return ''
  const name = SECTOR_NAMES[sectorModalSector.value] || sectorModalSector.value
  return `${name} (${sectorModalSector.value})`
})

function openSectorModal(sector: string) {
  sectorModalSector.value = sector
  sectorModalOpen.value = true
}

// Cutoff date computed
const cutoffDate = computed(() => {
  const today = new Date()
  const cutoff = new Date()
  const months = timeRange.value.value
  const days = Math.round(months * 30)
  cutoff.setDate(today.getDate() - days)
  return cutoff
})

// Filtered market breadth
const filteredMarketBreadth = computed(() => {
  return marketBreadthData.value.filter((d) => {
    return new Date(d.quoteDate) >= cutoffDate.value
  })
})

// Market breadth series/categories
const marketBreadthSeries = computed(() => {
  if (filteredMarketBreadth.value.length === 0) return []
  return [
    { name: 'Uptrend %', data: filteredMarketBreadth.value.map(d => d.breadthPercent) },
    { name: 'EMA 10', data: filteredMarketBreadth.value.map(d => d.ema10) },
    { name: 'EMA 20', data: filteredMarketBreadth.value.map(d => d.ema20) }
  ]
})

const marketBreadthCategories = computed(() => {
  return filteredMarketBreadth.value.map((d) => {
    const date = new Date(d.quoteDate)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  })
})

// Filtered sector breadth helper
function filteredSectorBreadth(sector: string): SectorBreadthDaily[] {
  const data = sectorBreadthMap.value[sector] || []
  return data.filter(d => new Date(d.quoteDate) >= cutoffDate.value)
}

// Sector EMA status helper
function sectorEmaStatus(sector: string): 'hot' | 'cold' | null {
  const filtered = filteredSectorBreadth(sector)
  if (filtered.length === 0) return null
  const latest = filtered[filtered.length - 1]
  if (!latest) return null
  if (latest.ema10 > latest.ema20) return 'hot'
  if (latest.ema10 < latest.ema20) return 'cold'
  return null
}

// Sector series/categories helpers
function sectorSeries(sector: string) {
  const filtered = filteredSectorBreadth(sector)
  if (filtered.length === 0) return []
  return [
    { name: 'Uptrend %', data: filtered.map(d => d.bullPercentage) },
    { name: 'EMA 10', data: filtered.map(d => d.ema10) },
    { name: 'EMA 20', data: filtered.map(d => d.ema20) }
  ]
}

function sectorCategories(sector: string) {
  return filteredSectorBreadth(sector).map((d) => {
    const date = new Date(d.quoteDate)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  })
}

// Section B: Sector Heat Summary
interface SectorHeatRow {
  sector: string
  bullPct: number
  marketPct: number
  difference: number
  status: string
}

const sectorHeatData = computed<SectorHeatRow[]>(() => {
  const marketFiltered = filteredMarketBreadth.value
  if (marketFiltered.length === 0) return []

  const latestMarket = marketFiltered[marketFiltered.length - 1]
  if (!latestMarket) return []
  const marketPct = latestMarket.breadthPercent

  const rows: SectorHeatRow[] = []
  for (const sector of SECTORS) {
    const filtered = filteredSectorBreadth(sector)
    if (filtered.length === 0) continue
    const latest = filtered[filtered.length - 1]
    if (!latest) continue
    const diff = latest.bullPercentage - marketPct
    rows.push({
      sector,
      bullPct: latest.bullPercentage,
      marketPct,
      difference: diff,
      status: diff > 0 ? 'Hotter' : 'Cooler'
    })
  }

  return rows.sort((a, b) => b.difference - a.difference)
})

const heatColumns: TableColumn<SectorHeatRow>[] = [
  {
    accessorKey: 'sector',
    header: 'Sector',
    cell: ({ row }) => `${SECTOR_NAMES[row.original.sector] || row.original.sector} (${row.original.sector})`
  },
  {
    accessorKey: 'bullPct',
    header: 'Bull %',
    cell: ({ row }) => `${row.original.bullPct.toFixed(1)}%`
  },
  {
    accessorKey: 'difference',
    header: 'Difference',
    cell: ({ row }) => {
      const diff = row.original.difference
      const sign = diff > 0 ? '+' : ''
      return `${sign}${diff.toFixed(1)}%`
    }
  },
  {
    accessorKey: 'status',
    header: 'Status'
  }
]

// Fetch all data
const fetchAllData = async () => {
  loading.value = true
  try {
    const marketPromise = $fetch<MarketBreadthDaily[]>('/udgaard/api/breadth/market-daily')
    const sectorPromises = SECTORS.map(sector =>
      $fetch<SectorBreadthDaily[]>(`/udgaard/api/breadth/sector-daily/${sector}`)
        .then(data => ({ sector, data }))
        .catch(() => ({ sector, data: [] as SectorBreadthDaily[] }))
    )

    const [marketData, ...sectorResults] = await Promise.all([marketPromise, ...sectorPromises])
    marketBreadthData.value = marketData

    const map: Record<string, SectorBreadthDaily[]> = {}
    for (const result of sectorResults) {
      map[result.sector] = result.data
    }
    sectorBreadthMap.value = map
  } catch (error) {
    console.error('Failed to fetch breadth data:', error)
    useToast().add({
      title: 'Error',
      description: 'Failed to load breadth data',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchAllData()
})
</script>
