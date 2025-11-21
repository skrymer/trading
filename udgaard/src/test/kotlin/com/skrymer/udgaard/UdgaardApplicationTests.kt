package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.model.strategy.PlanAlphaEntryStrategy
import com.skrymer.udgaard.model.strategy.PlanMoneyExitStrategy
import com.skrymer.udgaard.service.BacktestService
import com.skrymer.udgaard.service.MarketBreadthService
import com.skrymer.udgaard.service.StockService
import de.siegmar.fastcsv.writer.CsvWriter
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

@SpringBootTest
internal class UdgaardApplicationTests {

    @Test
    fun contextLoads() {
    }

}
