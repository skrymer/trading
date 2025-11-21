package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.LocalDate

class DebugRocTest {
    private val logger = LoggerFactory.getLogger(DebugRocTest::class.java)
    
    @Test
    fun `debug ROC with new calculation`() {
        val calculator = OrderBlockCalculator()
        val baseDate = LocalDate.of(2025, 1, 1)
        val quotes = listOf(
            createQuote(baseDate, 100.0, 100.5, 101.0, 99.0),
            createQuote(baseDate.plusDays(1), 100.5, 101.0, 102.0, 100.0),
            createQuote(baseDate.plusDays(2), 101.0, 101.5, 102.5, 100.5),
            createQuote(baseDate.plusDays(3), 101.5, 102.0, 103.0, 101.0),
            createQuote(baseDate.plusDays(4), 102.0, 102.5, 103.5, 101.5),
            createQuote(baseDate.plusDays(5), 102.5, 103.0, 104.0, 102.0),
            createQuote(baseDate.plusDays(6), 103.0, 103.5, 104.5, 102.5),
            createQuote(baseDate.plusDays(7), 103.5, 104.0, 105.0, 103.0),
            createQuote(baseDate.plusDays(8), 104.0, 104.5, 105.5, 103.5),
            createQuote(baseDate.plusDays(9), 104.5, 105.0, 106.0, 104.0),
            createQuote(baseDate.plusDays(10), 105.0, 105.5, 106.5, 104.5),
            createQuote(baseDate.plusDays(11), 105.5, 106.0, 107.0, 105.0),
            createQuote(baseDate.plusDays(12), 106.0, 106.5, 107.5, 105.5),
            createQuote(baseDate.plusDays(13), 106.5, 107.0, 108.0, 106.0),
            createQuote(baseDate.plusDays(14), 107.0, 107.5, 108.5, 106.5),
            createQuote(baseDate.plusDays(15), 108.0, 107.5, 109.0, 107.0),
            createQuote(baseDate.plusDays(16), 108.0, 109.0, 110.0, 107.5),
            createQuote(baseDate.plusDays(17), 109.0, 110.0, 111.0, 108.5),
            createQuote(baseDate.plusDays(18), 110.0, 120.0, 121.0, 109.5),
            createQuote(baseDate.plusDays(19), 140.0, 141.0, 142.0, 139.0)
        )
        
        val threshold = 28.0 / 100.0

        logger.info("Threshold: $threshold")
        logger.info("=" + "=".repeat(79))

        for (i in 5 until quotes.size) {
            val currentOpen = quotes[i].openPrice
            val prevOpen = quotes[i - 4].openPrice
            val roc = ((currentOpen - prevOpen) / prevOpen) * 100.0

            val prevCurrentOpen = quotes[i - 1].openPrice
            val prevPrevOpen = quotes[i - 5].openPrice
            val prevRoc = ((prevCurrentOpen - prevPrevOpen) / prevPrevOpen) * 100.0

            if (i >= 18) {
                logger.info("Index $i:")
                logger.info("  Current: open=$currentOpen, prevOpen(i-4)=$prevOpen, ROC=$roc")
                logger.info("  Previous: open=$prevCurrentOpen, prevOpen(i-5)=$prevPrevOpen, prevROC=$prevRoc")
                logger.info("  Bullish crossing check: prevRoc < threshold && roc > threshold")
                logger.info("    ${prevRoc} < ${threshold} && ${roc} > ${threshold}")
                logger.info("    = ${prevRoc < threshold} && ${roc > threshold} = ${prevRoc < threshold && roc > threshold}")
            }
        }

        val blocks = calculator.calculateOrderBlocks(quotes)
        logger.info("\nTotal blocks detected: ${blocks.size}")
        blocks.forEach { block ->
            logger.info("  ${block.orderBlockType}: start=${block.startDate}, ROC=${block.rateOfChange}")
        }
    }
    
    private fun createQuote(
        date: LocalDate,
        open: Double,
        close: Double,
        high: Double,
        low: Double
    ): StockQuote {
        return StockQuote(
            symbol = "TEST",
            date = date,
            openPrice = open,
            closePrice = close,
            high = high,
            low = low
        )
    }
}
