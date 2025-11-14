#!/bin/bash

# Compare stock data from Ovtlyr (via backend API) with Yahoo Finance
# Uses curl and basic bash tools - no Python required

BACKEND_URL="http://localhost:8080"
SYMBOLS=("AAPL" "MSFT" "SPY")

# Get date range: Jan 1, 2020 to now
END_DATE=$(date +%s)
START_DATE=$(date -d "2020-01-01" +%s)

echo "Stock Data Comparison: Ovtlyr vs Yahoo Finance"
echo "============================================================"
echo ""

# Check if backend is running
if ! curl -s -f "$BACKEND_URL/actuator/health" > /dev/null 2>&1; then
    echo "❌ Cannot connect to backend at $BACKEND_URL"
    echo "   Make sure the backend is running with: cd udgaard && ./gradlew bootRun"
    exit 1
fi

echo "✅ Backend is running"
echo ""

for SYMBOL in "${SYMBOLS[@]}"; do
    echo "============================================================"
    echo "Comparing data for $SYMBOL"
    echo "============================================================"
    echo ""

    # Fetch from Ovtlyr via backend
    echo "Fetching $SYMBOL from Ovtlyr..."
    OVTLYR_DATA=$(curl -s "$BACKEND_URL/api/stocks/$SYMBOL")

    if [ -z "$OVTLYR_DATA" ]; then
        echo "  ❌ No data received from Ovtlyr"
        continue
    fi

    # Extract last 5 quotes
    echo "  Last 5 quotes from Ovtlyr:"
    echo "$OVTLYR_DATA" | jq -r '.quotes[-5:] | .[] | "\(.date): Open=\(.openPrice), Close=\(.closePrice), High=\(.high), Low=\(.low)"'

    echo ""

    # Fetch from Yahoo Finance
    echo "Fetching $SYMBOL from Yahoo Finance..."
    YAHOO_URL="https://query1.finance.yahoo.com/v7/finance/download/$SYMBOL?period1=$START_DATE&period2=$END_DATE&interval=1d&events=history"

    YAHOO_DATA=$(curl -s "$YAHOO_URL")

    if [ -z "$YAHOO_DATA" ]; then
        echo "  ❌ No data received from Yahoo Finance"
        continue
    fi

    # Extract last 5 quotes (skip header)
    echo "  Last 5 quotes from Yahoo Finance:"
    echo "$YAHOO_DATA" | tail -n 5 | while IFS=, read -r date open high low close adj_close volume; do
        echo "$date: Open=$open, Close=$close, High=$high, Low=$low"
    done

    echo ""
    echo "Sample comparison (most recent day):"

    # Get most recent Ovtlyr data
    OVTLYR_LAST=$(echo "$OVTLYR_DATA" | jq -r '.quotes[-1] | "Date: \(.date), Close: \(.closePrice)"')
    echo "  Ovtlyr:  $OVTLYR_LAST"

    # Get most recent Yahoo data
    YAHOO_LAST=$(echo "$YAHOO_DATA" | tail -n 1)
    echo "  Yahoo:   $YAHOO_LAST"

    echo ""
    echo "Note: Compare the dates and close prices manually"
    echo "      Small differences (<0.5%) are normal due to data source variations"
    echo ""
done

echo "============================================================"
echo "Comparison complete!"
echo ""
echo "To install Python tools for detailed comparison:"
echo "  sudo apt install python3-venv"
echo "  python3 -m venv venv"
echo "  source venv/bin/activate"
echo "  pip install yfinance pandas requests"
echo "  python3 compare_data_sources.py"
