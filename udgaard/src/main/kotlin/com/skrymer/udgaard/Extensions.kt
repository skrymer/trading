package com.skrymer.udgaard

fun Double.format(scale: Int) = "%.${scale}f".format(this)
