package com.skrymer.udgaard;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.skrymer.udgaard.integration.ovtlyr.DataLoader;
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient;
import com.skrymer.udgaard.model.MarketSymbol;
import com.skrymer.udgaard.model.Ovtlyr9EntryStrategy;
import com.skrymer.udgaard.repository.MarketBreadthRepository;
import com.skrymer.udgaard.repository.StockRepository;
import com.skrymer.udgaard.service.StockService;

@SpringBootTest
class UdgaardApplicationTests {

	@Autowired
	OvtlyrClient ovtlyrClient;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	MarketBreadthRepository marketBreadthRepository;

	@Autowired
	DataLoader dataLoader;

	@Autowired
	StockService stockService;

	@Test
	void contextLoads() {
	}

	@Test
	void loadStocks(){	
		var stock = stockRepository.findById("DDOG").orElseGet(() -> {
			var response = ovtlyrClient.getStockInformation("DDOG");
			var marketBreadth = marketBreadthRepository.findById(MarketSymbol.FULLSTOCK.name());
			var sectorMarketBreadth = marketBreadthRepository.findById(response.getSectorSymbol());
			var spy = ovtlyrClient.getStockInformation("SPY");
			return stockRepository.save(response.toModel(marketBreadth, sectorMarketBreadth, spy));	
		});

		var matchingQuotes = stock.getQuotesMatching(new Ovtlyr9EntryStrategy());

		System.out.println("================== %s ==================".formatted(stock.getSymbol()));
		System.out.println("Number of mathching stock quotes: %d".formatted(matchingQuotes.size()));
		matchingQuotes.forEach(quote -> {
			System.out.println("Date: %s".formatted(quote.getDate()));
		});
		System.out.println("==============================================");
	}

	@Test
	void loadMarketBreadth() {
		dataLoader.loadData();
	}

	@Test
	void testOvtlyr9EntryStrategy(){

	}

	@Test
	void getStock(){
		var stock = stockService.getStock("NVDA");

		System.out.println(stock);
	}

}

// await fetch("https://api.ovtlyr.com/v1.0/StockSymbol/GetAllDashboardChartBySymbolWithFiltersAndSort", {
//     "credentials": "omit",
//     "headers": {
//         "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0",
//         "Accept": "*/*",
//         "Accept-Language": "en-US,en;q=0.5",
//         "Content-Type": "application/json",
//         "UserId": "7273",
//         "Token": "j5huga412w_ae08079a-bde7-44a8-978d-e5906fea1046",
//         "ProjectId": "Ovtlyr.com_project1",
//         "Sec-Fetch-Dest": "empty",
//         "Sec-Fetch-Mode": "cors",
//         "Sec-Fetch-Site": "same-site"
//     },
//     "referrer": "https://ovtlyr.com/",
//     "body": "{\"stockSymbol\":\"SPY\",\"period\":\"All\",\"page_index\":0,\"page_size\":2000}",
//     "method": "POST",
//     "mode": "cors"
// });