package com.worknotifier.app.utils

import android.graphics.Color

/**
 * Color palette for regex filter visualization.
 * Provides distinct, high-contrast colors for highlighting matched text.
 */
object FilterColorPalette {
    // 15 distinct colors with good contrast for text highlighting
    private val colors = listOf(
        0xFF4CAF50, // Green
        0xFF2196F3, // Blue
        0xFFFF9800, // Orange
        0xFFE91E63, // Pink
        0xFF9C27B0, // Purple
        0xFF00BCD4, // Cyan
        0xFFFFC107, // Amber
        0xFF795548, // Brown
        0xFF607D8B, // Blue Grey
        0xFFFF5722, // Deep Orange
        0xFF3F51B5, // Indigo
        0xFF009688, // Teal
        0xFFD32F2F, // Red
        0xFF1976D2, // Dark Blue
        0xFFAFB42B  // Lime
    )

    /**
     * Gets color for a specific filter index.
     * Cycles through the palette if index exceeds available colors.
     *
     * @param colorIndex The index (0-14+) to get color for
     * @return ARGB color value
     */
    fun getColorForIndex(colorIndex: Int): Long {
        return colors[colorIndex % colors.size]
    }

    /**
     * Gets ARGB color with specified alpha for background highlighting.
     * Uses 25% alpha (64/255) for subtle background.
     *
     * @param colorIndex The index to get color for
     * @return ARGB color with 25% alpha
     */
    fun getBackgroundColorWithAlpha(colorIndex: Int): Int {
        val color = getColorForIndex(colorIndex)
        val r = (color shr 16 and 0xFF).toInt()
        val g = (color shr 8 and 0xFF).toInt()
        val b = (color and 0xFF).toInt()
        val alpha = 64 // 25% alpha
        return Color.argb(alpha, r, g, b)
    }

    /**
     * Gets full opacity color for text or other elements.
     *
     * @param colorIndex The index to get color for
     * @return ARGB color with full opacity
     */
    fun getFullOpaqueColor(colorIndex: Int): Int {
        return getColorForIndex(colorIndex).toInt() or 0xFF000000.toInt()
    }

    /**
     * Total number of colors in palette.
     */
    fun getPaletteSize(): Int = colors.size
}
