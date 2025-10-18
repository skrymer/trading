type ISODate = `${number}-${number}-${number}`; // "2025-09-17"

export function isBetweenDateOnly(
  d: ISODate,
  a: ISODate,
  b: ISODate,
  inclusive: "both" | "none" | "start" | "end" = "both"
): boolean {
  const [lo, hi] = a <= b ? [a, b] : [b, a];
  if (inclusive === "both") return lo <= d && d <= hi;
  if (inclusive === "none") return lo <  d && d <  hi;
  if (inclusive === "start") return lo <= d && d <  hi;
  return lo <  d && d <= hi; // "end"
}