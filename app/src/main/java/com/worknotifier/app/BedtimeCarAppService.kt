package com.worknotifier.app

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Car App Service for controlling Bedtime mode from Android Auto screen.
 */
class BedtimeCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Allow all hosts (Android Auto and Android Automotive OS)
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return BedtimeSession()
    }

    /**
     * Session manages the app lifecycle and screens.
     */
    inner class BedtimeSession : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            return BedtimeScreen(carContext)
        }
    }
}
