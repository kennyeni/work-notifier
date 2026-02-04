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

/**
 * Screen for controlling Bedtime mode from Android Auto.
 * Displays a button to toggle Bedtime mode on/off.
 */
class BedtimeScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return PaneTemplate.Builder(
            Pane.Builder()
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
                .build()
        )
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun toggleBedtimeMode() {
        // Toggle Bedtime mode
        AutoModeManager.toggleBedtimeModeManual()

        // Refresh the screen to show feedback
        invalidate()
    }
}
