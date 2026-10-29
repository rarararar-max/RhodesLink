package com.rhodes.privatechat.ui.startup

import kotlin.math.min

/** Shared by hit testing and drawing, so screen size cannot offset the sensing area. */
internal data class StartupTouchRegion(val centerX: Float, val centerY: Float, val radius: Float) {
    fun contains(x: Float, y: Float): Boolean =
        (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY) <= radius * radius
}
internal fun startupTouchRegion(width: Float, height: Float) =
    StartupTouchRegion(width * .5f, height * .41f, min(width * .35f, height * .2f) * .8f)
