package com.worknotifier.app

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.worknotifier.app.utils.AutoModeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen for controlling Bedtime mode from Android Auto.
 * Displays a button to toggle Bedtime mode on/off.
 */
class BedtimeScreen(carContext: CarContext) : Screen(carContext) {

    private var lastToggleTime: String? = null

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Work Notifier")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Toggle Bedtime Mode")
                    .setOnClickListener {
                        toggleBedtimeMode()
                    }
                    .build()
            )

        // Show feedback if toggle was recently executed
        lastToggleTime?.let { time ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Last toggled: $time")
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun toggleBedtimeMode() {
        // Toggle Bedtime mode
        AutoModeManager.toggleBedtimeModeManual()

        // Update last toggle time
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lastToggleTime = timeFormat.format(Date())

        // Refresh the screen to show feedback
        invalidate()
    }
}
