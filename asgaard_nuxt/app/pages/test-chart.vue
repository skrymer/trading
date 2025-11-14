<script setup lang="ts">
const series = [{
  name: 'Test Series',
  data: [100000, 105000, 103000, 110000, 115000, 112000, 120000]
}]

const categories = [
  'Jan 1',
  'Jan 2',
  'Jan 3',
  'Jan 4',
  'Jan 5',
  'Jan 6',
  'Jan 7'
]
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Line Chart Test">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-4">
        <h2 class="text-xl font-bold mb-4">Simple Line Chart Test</h2>

        <ChartsLineChart
          :series="series"
          :categories="categories"
          :height="400"
          :show-legend="false"
          y-axis-label="Value ($)"
        />

        <div class="mt-4">
          <h3 class="font-semibold mb-2">Test Data:</h3>
          <pre class="bg-gray-100 dark:bg-gray-800 p-4 rounded">{{ JSON.stringify({ series, categories }, null, 2) }}</pre>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
