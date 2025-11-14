#!/usr/bin/env python3
"""
Batch backtest runner that processes stocks in batches and aggregates results.
"""

import json
import subprocess
import sys
from collections import defaultdict
from typing import List, Dict, Any

# All stock symbols
SYMBOLS = ["AAL", "AAP", "AAPL", "ABBV", "ABT", "ACN", "ADBE", "ADI", "ADM", "ADP", "ADSK", "AEE", "AEP", "AES", "AIG", "AIZ", "AJG", "AKAM", "ALB", "ALGN", "ALL", "AMAT", "AMD", "AMGN", "AMP", "AMT", "AMZN", "ANET", "AON", "AOS", "APA", "APD", "APH", "APTV", "ARE", "ATO", "AVB", "AVGO", "AVY", "AWK", "AXP", "AZO", "BA", "BAC", "BALL", "BAX", "BBY", "BDX", "BEN", "BIIB", "BK", "BKNG", "BKR", "BLK", "BMY", "BR", "BSX", "BSY", "BXP", "C", "CAG", "CAH", "CARR", "CAT", "CB", "CBOE", "CCI", "CCL", "CDNS", "CE", "CEG", "CF", "CHD", "CHRW", "CHTR", "CI", "CL", "CLX", "CMC", "CMCSA", "CME", "CMI", "CMS", "CNC", "CNP", "COF", "COO", "COP", "COST", "CPB", "CPT", "CRM", "CRWD", "CSCO", "CSX", "CTAS", "CTSH", "CTVA", "CVS", "CVX", "CZR", "D", "DD", "DDOG", "DE", "DG", "DGX", "DHI", "DHR", "DIS", "DKS", "DLR", "DLTR", "DOV", "DOW", "DTE", "DUK", "DVN", "DXC", "DXCM", "EA", "EBAY", "ECL", "ED", "EIX", "EL", "ELV", "EMR", "ENPH", "EOG", "EPAM", "EQIX", "EQR", "EQT", "ERIE", "ES", "ESS", "ETN", "ETR", "ETSY", "EXC", "EXPD", "EXPE", "EXR", "F", "FANG", "FCX", "FDS", "FDX", "FE", "FMC", "FOX", "FOXA", "FRT", "FTNT", "GD", "GE", "GEN", "GILD", "GIS", "GLW", "GM", "GOOG", "GOOGL", "GPC", "GS", "GWW", "HAL", "HBAN", "HCA", "HD", "HIG", "HLT", "HOLX", "HON", "HOOD", "HPE", "HPQ", "HST", "HSY", "HUBB", "HUM", "HWM", "IBM", "ICE", "IDXX", "IFF", "ILMN", "INCY", "INTC", "INTU", "INVH", "IP", "IPG", "IPGP", "IRM", "ISRG", "IT", "ITW", "IVZ", "J", "JCI", "JKHY", "JNJ", "JPM", "K", "KDP", "KEX", "KHC", "KIM", "KKR", "KLAC", "KMB", "KMI", "KO", "KR", "L", "LEG", "LEN", "LH", "LHX", "LKQ", "LLY", "LMND", "LMT", "LOW", "LRCX", "LULU", "LYB", "LYV", "MA", "MAA", "MAR", "MAS", "MCD", "MCHP", "MCO", "MDB", "MDLZ", "MDT", "MET", "META", "MGM", "MKC", "MKTX", "MLM", "MMC", "MO", "MOH", "MOS", "MPC", "MPWR", "MRK", "MRNA", "MRVL", "MS", "MSFT", "MTB", "MTD", "MU", "NCLH", "NDAQ", "NEE", "NEM", "NET", "NFLX", "NKE", "NOC", "NOW", "NRG", "NSC", "NUE", "NVDA", "NWSA", "NXPI", "O", "ODFL", "OKE", "OKTA", "OMC", "ON", "ORCL", "ORLY", "OTIS", "OXY", "PANW", "PAYC", "PAYX", "PEG", "PEP", "PFE", "PG", "PGR", "PH", "PHM", "PINS", "PKG", "PLD", "PLTR", "PM", "PNC", "PNR", "PNW", "POOL", "PPG", "PPL", "PRU", "PSA", "PSX", "PTCT", "PWR", "QCOM", "QQQ", "RCL", "REG", "REGN", "RF", "RHI", "RMD", "ROK", "ROL", "ROP", "ROST", "RTX", "SBAC", "SBUX", "SCHW", "SEE", "SHW", "SJM", "SLB", "SNA", "SNPS", "SO", "SPG", "SPGI", "SRE", "STE", "STX", "STZ", "SWKS", "SYF", "SYK", "SYY", "T", "TAP", "TDG", "TEL", "TER", "TGT", "TJX", "TMO", "TMUS", "TPR", "TROW", "TRV", "TSLA", "TSN", "TT", "TTWO", "TXN", "TXT", "TYL", "UAA", "UAL", "UHS", "ULTA", "UNH", "UNP", "UPS", "URI", "USB", "V", "VFC", "VICI", "VIRT", "VLO", "VMC", "VNO", "VRSN", "VRTX", "VTR", "VZ", "WAT", "WBD", "WDC", "WEC", "WELL", "WFC", "WHR", "WM", "WMB", "WMT", "WRB", "WY", "WYNN", "XEL", "XOM", "XYL", "YUM", "ZBH", "ZBRA", "ZION", "ZS"]

BATCH_SIZE = 20  # Process 20 stocks at a time

def chunk_list(lst: List[str], n: int) -> List[List[str]]:
    """Split list into chunks of size n"""
    for i in range(0, len(lst), n):
        yield lst[i:i + n]

def run_backtest_batch(symbols: List[str], entry_strategy: str, exit_strategy: str,
                       start_date: str, end_date: str) -> Dict[str, Any]:
    """Run backtest for a batch of symbols using curl to call the MCP server"""
    symbol_str = ",".join(symbols)

    # Since we're using MCP protocol, we need to craft a proper JSON-RPC request
    # However, this is complex. Instead, let's just parse the output we'll get from running
    # the backtest through the Spring Boot app directly via HTTP

    # For now, let's return a placeholder and use a simpler approach
    return None

def aggregate_results(all_results: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Aggregate results from multiple batches"""
    all_trades = []
    stock_profits = defaultdict(lambda: {"profit": 0.0, "trades": 0})
    sector_profits = defaultdict(lambda: {"profit": 0.0, "trades": 0})
    exit_reasons = defaultdict(int)

    total_winning = 0
    total_losing = 0
    total_win_amount = 0.0
    total_loss_amount = 0.0

    for result in all_results:
        if result is None:
            continue

        # Aggregate trades
        trades = result.get("trades", [])
        all_trades.extend(trades)

        # Aggregate by stock
        for stock_data in result.get("stockProfitability", []):
            symbol = stock_data["symbol"]
            profit_str = stock_data["profitPercentage"].rstrip("%")
            profit = float(profit_str)
            stock_profits[symbol]["profit"] += profit
            stock_profits[symbol]["trades"] += 1

        # Aggregate by sector (from trades)
        for trade in trades:
            sector = trade.get("sector", "Unknown")
            profit = float(trade["profit"])
            sector_profits[sector]["profit"] += profit
            sector_profits[sector]["trades"] += 1

        # Aggregate exit reasons
        for reason_data in result.get("exitReasons", []):
            reason = reason_data["reason"]
            count = reason_data["count"]
            exit_reasons[reason] += count

        # Aggregate performance
        perf = result.get("performance", {})
        total_winning += perf.get("winningTrades", 0)
        total_losing += perf.get("losingTrades", 0)

    # Calculate aggregated metrics
    total_trades = total_winning + total_losing
    win_rate = (total_winning / total_trades * 100) if total_trades > 0 else 0

    # Sort stocks by profit
    sorted_stocks = sorted(
        [(k, v["profit"], v["trades"]) for k, v in stock_profits.items()],
        key=lambda x: x[1],
        reverse=True
    )

    # Sort sectors by profit
    sorted_sectors = sorted(
        [(k, v["profit"], v["trades"]) for k, v in sector_profits.items()],
        key=lambda x: x[1],
        reverse=True
    )

    return {
        "totalTrades": total_trades,
        "winRate": f"{win_rate:.2f}%",
        "totalWinning": total_winning,
        "totalLosing": total_losing,
        "topStocks": sorted_stocks[:20],
        "bottomStocks": sorted_stocks[-20:],
        "sectorPerformance": sorted_sectors,
        "exitReasons": dict(exit_reasons),
        "allTrades": all_trades
    }

def main():
    print("Starting batch backtest for all stocks...")
    print(f"Total stocks: {len(SYMBOLS)}")
    print(f"Batch size: {BATCH_SIZE}")
    print(f"Total batches: {len(list(chunk_list(SYMBOLS, BATCH_SIZE)))}\n")

    # Note: This script structure is ready, but we need to call the MCP tool
    # from within Claude Code, not from a standalone Python script
    # We'll need to modify this approach

    print("Note: This script needs to be run from within Claude Code context")
    print("to access the MCP runBacktest tool.")

if __name__ == "__main__":
    main()