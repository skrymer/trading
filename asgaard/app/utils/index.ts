const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const currencyFormatterNoDecimals = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0
})

export function formatUsd(value: number, decimals: boolean = true): string {
  return decimals ? currencyFormatter.format(value) : currencyFormatterNoDecimals.format(value)
}

export function formatSignedUsd(value: number, decimals: boolean = true): string {
  const formatted = formatUsd(value, decimals)
  return value > 0 ? `+${formatted}` : formatted
}

export function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

export function randomFrom<T>(array: T[]): T {
  return array[Math.floor(Math.random() * array.length)]!
}
