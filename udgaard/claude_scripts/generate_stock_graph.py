#!/usr/bin/env python3
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from datetime import datetime

# NVDA stock data from the API
dates = [
    "2025-09-29", "2025-09-30", "2025-10-01", "2025-10-02", "2025-10-03",
    "2025-10-06", "2025-10-07", "2025-10-08", "2025-10-09", "2025-10-10",
    "2025-10-13", "2025-10-14", "2025-10-15", "2025-10-16", "2025-10-17",
    "2025-10-20", "2025-10-21", "2025-10-22", "2025-10-23", "2025-10-24",
    "2025-10-27", "2025-10-28", "2025-10-29"
]

close_prices = [
    181.85, 186.58, 187.24, 188.89, 187.62,
    185.54, 185.04, 189.11, 192.57, 183.16,
    188.32, 180.03, 179.83, 181.81, 183.22,
    182.64, 181.16, 180.28, 182.16, 186.26,
    191.49, 201.03, 201.03
]

high_prices = [
    184.0, 187.35, 188.14, 191.05, 190.36,
    187.23, 189.06, 189.6, 195.3, 195.62,
    190.1099, 184.8, 184.87, 183.28, 184.1,
    185.2, 182.785, 183.44, 183.03, 187.47,
    192.0, 203.15, 203.15
]

low_prices = [
    180.32, 181.48, 183.9, 188.06, 185.38,
    183.33, 184.0, 186.54, 191.06, 182.05,
    185.96, 179.7, 177.29, 179.77, 179.75,
    181.73, 179.8, 176.76, 179.7901, 183.5,
    188.4318, 191.91, 191.91
]

# Convert dates to datetime objects
date_objects = [datetime.strptime(d, "%Y-%m-%d") for d in dates]

# Create the figure and axis
fig, ax = plt.subplots(figsize=(14, 7))

# Plot the close price line
ax.plot(date_objects, close_prices, color='#2962FF', linewidth=2, label='Close Price', marker='o', markersize=4)

# Fill area between high and low
ax.fill_between(date_objects, low_prices, high_prices, alpha=0.2, color='#90CAF9', label='Daily Range (High-Low)')

# Formatting
ax.set_xlabel('Date', fontsize=12, fontweight='bold')
ax.set_ylabel('Price ($)', fontsize=12, fontweight='bold')
ax.set_title('NVDA Stock Price (Sep 29 - Oct 29, 2025)', fontsize=16, fontweight='bold', pad=20)

# Format x-axis
ax.xaxis.set_major_formatter(mdates.DateFormatter('%b %d'))
ax.xaxis.set_major_locator(mdates.DayLocator(interval=2))
plt.xticks(rotation=45, ha='right')

# Add grid
ax.grid(True, alpha=0.3, linestyle='--')

# Add legend
ax.legend(loc='upper left', fontsize=10)

# Add some statistics
min_price = min(low_prices)
max_price = max(high_prices)
start_price = close_prices[0]
end_price = close_prices[-1]
change_pct = ((end_price - start_price) / start_price) * 100

stats_text = f'Start: ${start_price:.2f}\nEnd: ${end_price:.2f}\nChange: +{change_pct:.2f}%\nHigh: ${max_price:.2f}\nLow: ${min_price:.2f}'
ax.text(0.02, 0.98, stats_text, transform=ax.transAxes,
        fontsize=10, verticalalignment='top',
        bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

# Adjust layout to prevent label cutoff
plt.tight_layout()

# Save the figure
output_file = '/home/sonni/development/git/trading/udgaard/nvda_stock_chart.png'
plt.savefig(output_file, dpi=300, bbox_inches='tight')
print(f"Chart saved to: {output_file}")

# Display the plot
plt.show()