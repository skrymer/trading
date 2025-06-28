<template>
  <v-container   max-width="900">
    <div>
      <v-row>
        <v-col cols="12" class="fill-width">
            <apexchart type="line" :options="chartOptions" :series="series" />
        </v-col>
      </v-row>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import axios from 'axios'

const chartOptions = ref({
  chart: {
    id: 'trades',
  },
  xaxis: {
    categories: [1991, 1992, 1993, 1994, 1995, 1996, 1997, 1998],
  },
});

const series = ref<[{name: String, data: number[] | string[]}]>([{
  name: 'winning-trades',
  data: [30, 40, 45, 50, 49, 60, 70, 81],
}]);

async function fetchTrades() {
  const resp = await axios.get('http://localhost:8080/api/report?stock=TSLA')
  const backTestreport = resp.data
  const entryDates = backTestreport.winningTrades?.map((it: { entryQuote: {date: String} }) => {
    return it.entryQuote.date
  })
  const profit = backTestreport.winningTrades?.map((it: { profit: string }) => {
    return it.profit
  })

  chartOptions.value = {
    ...chartOptions.value,    
    xaxis: {
      categories: entryDates
    }
  }
  series.value = [{
    ...series.value[0],
    data: profit 
  }]
  console.log(entryDates);
}

onMounted(async () => {
    await fetchTrades()
})

</script>
