# Sonar Lab Order Block Algorithm - Analysis and Comparison

## Executive Summary

After investigating Sonar Lab's order block detection algorithm and comparing it with our current implementation, I found that both approaches share the **same core concept** but differ in their **detection triggers** and **validation requirements**.

**Core Concept (SAME):**
- **Bullish Order Block**: Last bearish candle before a bullish impulse move
- **Bearish Order Block**: Last bullish candle before a bearish impulse move

**Key Differences:**
1. **Detection Trigger**: Sonar Lab requires Break of Structure (BOS), we use ROC momentum
2. **Market Structure**: Sonar Lab integrates with swing highs/lows, we work independently
3. **Volume Requirements**: Sonar Lab explicitly looks for high volume areas, we calculate volume strength

---

## Sonar Lab's Approach

### 1. Break of Structure (BOS) Requirement

**Fundamental Rule:** An order block is only valid AFTER a Break of Structure occurs.

**Bullish BOS:**
- Price closes ABOVE a previous swing high
- Confirms uptrend continuation
- System then looks backward for high volume areas

**Bearish BOS:**
- Price closes BELOW a previous swing low
- Confirms downtrend continuation
- System then looks backward for high volume areas

### 2. Detection Algorithm

```
1. Monitor market structure (swing highs/lows)
2. Detect Break of Structure (BOS):
   - Bullish BOS: Close > Previous Swing High
   - Bearish BOS: Close < Previous Swing Low
3. After BOS confirmed:
   - Look backward for high volume candles
   - Identify the candle that originated the move
   - For Bullish BOS: Find last bearish candle before the impulse
   - For Bearish BOS: Find last bullish candle before the impulse
4. Mark that candle's high/low range as the order block
```

### 3. Sensitivity Parameter

**Default: 28** (same as our implementation)

**Purpose:** Controls the threshold for Rate of Change detection
- Higher sensitivity (e.g., 50) = Fewer, stronger order blocks
- Lower sensitivity (e.g., 20) = More order blocks detected

**Timeframe Relationship:**
- Lower timeframes → Lower sensitivity (less price movement)
- Higher timeframes → Higher sensitivity (more price movement required)

### 4. Additional Settings

**Min Candles in Structure:**
- Controls how many candles form a valid swing high/low
- Prevents noise from minor fluctuations

**BOS Type:**
- **Close**: Requires candle close beyond level (more conservative)
- **Wick**: Allows wick to touch level (more aggressive)

---

## Our Current Implementation

### 1. ROC-Based Detection

**Detection Trigger:** Rate of Change (ROC) momentum crossover

```kotlin
// Algorithm from OrderBlockCalculator.kt
1. Calculate ROC: (open - open[4]) / open[4] * 100
2. Detect crossings:
   - Bullish: ROC crosses ABOVE +sensitivity% (e.g., +28%)
   - Bearish: ROC crosses BELOW -sensitivity% (e.g., -28%)
3. On crossing detected:
   - Look back 4-15 bars for origin candle
   - For Bearish OB: Find last bullish (green) candle
   - For Bullish OB: Find last bearish (red) candle
4. Create order block with that candle's high/low range
```

### 2. Key Features

**Spacing Rules:**
- Same-type spacing: 5 bars minimum between same-type order blocks
- Cross-type spacing: 5 bars minimum between opposite-type order blocks

**Volume Tracking:**
- Calculates volume strength: currentVolume / avgVolume(20)
- Stores with each order block for filtering/ranking

**Mitigation Detection:**
- Tracks when order blocks become invalid
- Bullish OB mitigated: Price closes below low
- Bearish OB mitigated: Price closes above high

### 3. Advantages

✅ **No swing high/low calculation needed** - Simpler, faster
✅ **ROC is objective** - Clear mathematical threshold
✅ **Volume strength tracked** - Can filter weak blocks
✅ **Mitigation dates recorded** - Knows when blocks expire

---

## Detailed Comparison

| Feature | Sonar Lab | Our Implementation |
|---------|-----------|-------------------|
| **Core Concept** | Last opposite candle before impulse | ✅ Same |
| **Detection Trigger** | Break of Structure (BOS) | ROC momentum crossover |
| **Requires Swing High/Low** | ✅ Yes | ❌ No |
| **Sensitivity Parameter** | ✅ Default 28 | ✅ Default 28 |
| **Origin Candle Lookback** | ✅ Yes (range not specified) | ✅ Yes (4-15 bars) |
| **Volume Analysis** | ✅ High volume areas | ✅ Volume strength ratio |
| **Mitigation Tracking** | ❓ Unknown | ✅ Yes |
| **Close vs Wick** | ✅ Configurable | ✅ Uses close for mitigation |
| **Market Structure Integration** | ✅ Required (BOS) | ❌ Independent |

---

## Key Insights

### Similarities

1. **Same Order Block Definition**: Both identify the "last opposite candle before impulse"
2. **Same Sensitivity Concept**: Both use 28% as default threshold
3. **Same Lookback Approach**: Both look backward to find origin candle
4. **Volume Consideration**: Both factor in volume (Sonar Lab: high volume areas, Us: volume strength)

### Differences

1. **Detection Philosophy**:
   - **Sonar Lab**: Structure-first (requires BOS)
   - **Us**: Momentum-first (ROC crossover)

2. **Complexity**:
   - **Sonar Lab**: More complex (needs swing high/low detection + BOS logic)
   - **Us**: Simpler (direct ROC calculation)

3. **False Positives**:
   - **Sonar Lab**: Fewer (BOS acts as confirmation filter)
   - **Us**: Potentially more (no structure requirement)

4. **Responsiveness**:
   - **Sonar Lab**: Slightly delayed (waits for BOS)
   - **Us**: Immediate (ROC crossing is instant)

---

## When Each Approach Excels

### Sonar Lab's BOS Approach is Better When:

✅ **Trend trading** - BOS confirms trend continuation
✅ **Avoiding whipsaws** - BOS filters out weak moves
✅ **Swing trading** - Structure-based entries are cleaner
✅ **Following institutional flow** - BOS indicates commitment

### Our ROC Approach is Better When:

✅ **Early detection needed** - No waiting for BOS
✅ **Momentum trading** - ROC directly measures momentum
✅ **Short-term trading** - Faster signals
✅ **Backtesting speed** - No complex swing calculations needed

---

## Recommendations

### Option 1: Keep Current Implementation (RECOMMENDED)

**Rationale:**
- Our ROC approach is **mathematically sound**
- Simpler and faster to calculate
- Already produces valid order blocks (same definition as Sonar Lab)
- Can be filtered by volume strength if needed

**Action:** None required, current implementation is solid

---

### Option 2: Add BOS Detection (ENHANCEMENT)

**Implementation:**
1. Add swing high/low detection algorithm
2. Detect BOS events (price breaks swing levels)
3. Create new order block detector that uses BOS instead of ROC
4. Compare both approaches in backtesting

**Complexity:** High
**Benefit:** May reduce false positives, better trend alignment
**Risk:** More complex code, slower calculation

**Files to Create:**
```
SwingHighLowDetector.kt
BreakOfStructureDetector.kt
BosOrderBlockCalculator.kt
```

---

### Option 3: Hybrid Approach (BEST OF BOTH)

**Implementation:**
1. Keep current ROC-based order block detection
2. Add BOS detection as optional **filter/validator**
3. Allow strategies to choose:
   - `orderBlock(requireBos = false)` → All order blocks (current)
   - `orderBlock(requireBos = true)` → Only blocks with BOS confirmation

**Complexity:** Medium
**Benefit:** Flexibility for different trading styles
**Trade-off:** Can test both approaches

---

## Technical Details: Break of Structure Detection

If we decide to implement BOS detection, here's the algorithm:

### Swing High/Low Detection

```kotlin
/**
 * Detect swing high: High[i] > High[i-n] AND High[i] > High[i+n]
 * where n = lookback period (e.g., 5-10 bars)
 */
fun detectSwingHigh(quotes: List<StockQuote>, index: Int, lookback: Int = 5): Boolean {
    if (index < lookback || index >= quotes.size - lookback) return false

    val currentHigh = quotes[index].high

    // Check left side (i-lookback to i-1)
    val leftHigher = (1..lookback).all { quotes[index - it].high < currentHigh }

    // Check right side (i+1 to i+lookback)
    val rightHigher = (1..lookback).all { quotes[index + it].high < currentHigh }

    return leftHigher && rightHigher
}

/**
 * Similar logic for swing low
 */
fun detectSwingLow(quotes: List<StockQuote>, index: Int, lookback: Int = 5): Boolean {
    if (index < lookback || index >= quotes.size - lookback) return false

    val currentLow = quotes[index].low

    val leftLower = (1..lookback).all { quotes[index - it].low > currentLow }
    val rightLower = (1..lookback).all { quotes[index + it].low > currentLow }

    return leftLower && rightLower
}
```

### BOS Detection

```kotlin
data class SwingLevel(
    val price: Double,
    val date: LocalDate,
    val type: SwingType
)

enum class SwingType { HIGH, LOW }

fun detectBreakOfStructure(
    quotes: List<StockQuote>,
    swingHighs: List<SwingLevel>,
    swingLows: List<SwingLevel>,
    index: Int
): BosEvent? {
    val currentQuote = quotes[index]

    // Find most recent swing high
    val lastSwingHigh = swingHighs
        .filter { it.date < currentQuote.date }
        .maxByOrNull { it.date }

    // Find most recent swing low
    val lastSwingLow = swingLows
        .filter { it.date < currentQuote.date }
        .maxByOrNull { it.date }

    // Bullish BOS: Close above last swing high
    if (lastSwingHigh != null && currentQuote.closePrice > lastSwingHigh.price) {
        return BosEvent(
            date = currentQuote.date!!,
            type = BosType.BULLISH,
            level = lastSwingHigh.price
        )
    }

    // Bearish BOS: Close below last swing low
    if (lastSwingLow != null && currentQuote.closePrice < lastSwingLow.price) {
        return BosEvent(
            date = currentQuote.date!!,
            type = BosType.BEARISH,
            level = lastSwingLow.price
        )
    }

    return null
}
```

---

## Conclusion

**Our current implementation is fundamentally sound and follows the same order block concept as Sonar Lab.** The main difference is we use ROC momentum as the detection trigger, while Sonar Lab uses Break of Structure.

**Recommendation:** Keep current implementation for now. If we want to enhance it later, add BOS detection as an optional filter rather than replacing the ROC approach entirely.

**Why This is Good:**
1. ✅ Simpler and faster calculation
2. ✅ Same order block definition (last opposite candle)
3. ✅ Already tracks volume strength
4. ✅ Already tracks mitigation
5. ✅ Can add BOS filtering later if needed

**Next Steps:**
1. Use current order block implementation in strategies
2. Monitor performance in backtesting
3. If false positives are an issue, consider adding BOS filter
4. Document any issues or improvements discovered

---

## References

**Sonar Lab Resources:**
- TrendSpider Order Blocks Documentation
- Sonar Lab SMC Guide
- Free Order Block Finder Guide

**ICT (Inner Circle Trader) Concepts:**
- Order Block Definition: Last opposite candle before impulse
- Break of Structure: Price closing beyond swing high/low
- Smart Money Concepts: Institutional order flow tracking

**Our Implementation:**
- `OrderBlockCalculator.kt` - ROC-based detection algorithm
- `OrderBlock.kt` - Order block entity with volume tracking
- Default sensitivity: 28% (matches Sonar Lab)
- Lookback window: 4-15 bars

---

*Generated: 2025-12-05*
*Investigation completed by Claude Code*
