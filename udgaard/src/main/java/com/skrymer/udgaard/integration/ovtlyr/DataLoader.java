package com.skrymer.udgaard.integration.ovtlyr;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.skrymer.udgaard.model.MarketSymbol;
import com.skrymer.udgaard.repository.MarketBreadthRepository;

/**
 * Loads data from ovtlyr into mongo.
 */
@Component
public class DataLoader {

    private final MarketBreadthRepository marketBreadthRepository;
    private final OvtlyrClient ovtlyrClient;

    public DataLoader(OvtlyrClient ovtlyrClient, MarketBreadthRepository marketBreadthRepository){
        this.ovtlyrClient = ovtlyrClient;
        this.marketBreadthRepository = marketBreadthRepository;
    }

    public void loadData(){
        try {
            loadMarkBreadthForAllSectors();            
        } catch (Exception e) {
            // TODO: handle exception logging
        }
    }

    private void loadMarkBreadthForAllSectors(){
        Arrays.asList(MarketSymbol.values()).forEach(symbol -> {
			var response = ovtlyrClient.getMarketBreadth(((MarketSymbol)symbol).name());
			marketBreadthRepository.save(response.toModel());
			System.out.println(response);
		});
    }
}
