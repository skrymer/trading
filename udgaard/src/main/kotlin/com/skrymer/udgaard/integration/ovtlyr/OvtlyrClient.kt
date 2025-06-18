package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrMarketBreadth
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OvtlyrClient {
    fun getStockInformation(symbol: String): OvtlyrStockInformation? {
        val restClient: RestClient = RestClient.builder()
            .baseUrl("https://api.ovtlyr.com/v1.0/StockSymbol/GetAllDashboardChartBySymbolWithFiltersAndSort")
            .build()
        val requestBody = "{\"stockSymbol\":\"${symbol}\",\"period\":\"All\",\"page_index\":0,\"page_size\":20000}"

        println("Fetching stock information for $symbol")

        // TODO extract properties
        return restClient.post()
            .header("Host", "api.ovtlyr.com")
            .header("Origin", "https://ovtlyr.com")
            .header("UserId", userId)
            .header("Token", token)
            .header("ProjectId", "Ovtlyr.com_project1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .toEntity<OvtlyrStockInformation?>(OvtlyrStockInformation::class.java)
            .getBody()
    }

    /**
     *
     * @param symbol - symbol of the market
     * @return
     */
    fun getMarketBreadth(symbol: String): OvtlyrMarketBreadth? {
        val restClient: RestClient = RestClient.builder()
            .baseUrl("https://ovtlyr.com/market-breadth?handler=GetFormulaDashboardPlotValues")
            .build()
        val requestBody: String =
            "{\"page_size\":2000,\"page_index\":0,\"period\":\"All\",\"stockSymbol\":\"${symbol}\"}"

        // TODO extract preoprties
        return restClient.post()
            .header("Host", "api.ovtlyr.com")
            .header("Origin", "https://ovtlyr.com")
            .cookie("UserId", "GlXBX/Fz8FdGaAM6ewG6aQ==")
            .cookie(
                "Token",
                "bHLj4DV1toLEOjxwBiStiIYkCod5Amw4b9qrVA9vixDkcCX8A+0pQZXWc/4qtw9FukzAuKKWO0xnDI/wI2b959/2MHHY6aRiQLut6rOSuce+G0cWhzjAtD4GnsVSqsC4"
            )
            .header("ProjectId", "Ovtlyr.com_project1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .toEntity(OvtlyrMarketBreadth::class.java)
            .getBody()
    }

    companion object {
        private const val userId = "7273"
        private const val token = "Wryjy2eFJh_1106bff6-87c1-4d25-adb3-16fbe65c1322"
    }
} // curl 'https://ovtlyr.com/market-breadth?handler=GetFormulaDashboardPlotValues' \
//   -X POST \
//   -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0' \
//   -H 'Accept: application/json, text/javascript, */*; q=0.01' \
//   -H 'Accept-Language: en-US,en;q=0.5' \
//   -H 'Accept-Encoding: gzip, deflate, br, zstd' \
//   -H 'Content-Type: application/json;charset=utf-8' \
//   -H 'RequestVerificationToken: CfDJ8GXGlkMHfD5HpJJNCqXYp9KA2lDkehIP33cg4mbyoNGpn24MAtCu870u6jizklTp-76vd9hH47ZqVwGL52hxNxl2tgFaIYJLJd2DhUuKg7iVRnAHD6SD5EzI3c53vhkBBSX_1tbdftsOgBxQ_Uz068w' \
//   -H 'X-Requested-With: XMLHttpRequest' \
//   -H 'Origin: https://ovtlyr.com' \
//   -H 'Connection: keep-alive' \
//   -H 'Referer: https://ovtlyr.com/market-breadth' \
//   -H 'Cookie: UserId=GlXBX%2FFz8FdGaAM6ewG6aQ%3D%3D; Token=vzm46i%2FDXJ0UWN2u%2FgosOSdKByOF1RcfcL9nSc7XfUOjPaWF43eB1%2FvA9nEyEszFl8cJU97qARP7o09wDdX1arIjzJq8tS1cq0Dpacp3de8fvoSEosBookeUGSim2bRO; DefaultSymbol=NVDA; _ga_821K94VTNJ=GS2.1.s1747633277$o1$g0$t1747633294$j0$l0$h0; _ga=GA1.1.1310915477.1747633277; _gcl_au=1.1.492273990.1747633278; .AspNetCore.Antiforgery.lviGJlCXgNE=CfDJ8GXGlkMHfD5HpJJNCqXYp9KXodDz7s4RYCePAy-SimycIrW7Le7vWZ5m_fiistHsThBDPz2-87s04w6OlgALiDTWJ2Pj8wDfslv1ELTueuMvoRZX-2WjwSyP9uXGc8VdsB0EwJQI5EkOMiZjqU01H70' \
//   -H 'Sec-Fetch-Dest: empty' \
//   -H 'Sec-Fetch-Mode: cors' \
//   -H 'Sec-Fetch-Site: same-origin' \
//   -H 'TE: trailers' \
//   --data-raw '{"page_size":2000,"page_index":0,"period":"All","stockSymbol":"FullStock"}'

