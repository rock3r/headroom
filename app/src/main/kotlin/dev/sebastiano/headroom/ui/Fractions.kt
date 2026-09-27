package dev.sebastiano.headroom.ui

private const val FULL_PERCENT = 100.0

/** A percentage from 0 to 100 as the 0 to 1 fraction progress indicators take. */
fun Double.asFraction(): Float = (this / FULL_PERCENT).toFloat()
