import { format } from 'date-fns'
import type { Position } from '~/types'

export function usePositionFormatters() {
  const formatPositionName = (position: Position) => {
    if (position.instrumentType === 'OPTION') {
      return position.underlyingSymbol || position.symbol
    }
    return position.symbol
  }

  const formatOptionDetails = (position: Position) => {
    if (position.instrumentType !== 'OPTION') return null
    return `${position.optionType} $${position.strikePrice?.toFixed(2)} ${format(new Date(position.expirationDate!), 'MMM d, yyyy')}`
  }

  const formatCurrency = (value: number, currency = 'USD') => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value)
  }

  const formatDate = (date: string) => {
    return format(new Date(date), 'MMM d, yyyy')
  }

  return {
    formatPositionName,
    formatOptionDetails,
    formatCurrency,
    formatDate
  }
}
