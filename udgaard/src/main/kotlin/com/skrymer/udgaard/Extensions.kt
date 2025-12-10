package com.skrymer.udgaard

import java.time.LocalDate

fun Double.format(scale: Int) = "%.${scale}f".format(this)

fun LocalDate.isBetween(
  a: LocalDate,
  b: LocalDate,
  inclusive: Boolean = true,
): Boolean {
  val (start, end) = if (a.isAfter(b)) b to a else a to b
  return if (inclusive) {
    !this.isBefore(start) && !this.isAfter(end)
  } else {
    this.isAfter(start) && this.isBefore(end)
  }
}
