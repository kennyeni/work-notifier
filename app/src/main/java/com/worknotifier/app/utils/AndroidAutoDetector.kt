package com.worknotifier.app.utils

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * Utility class to detect Android Auto connection status.
 *
 * Uses the official CarConnection API from androidx.car.app to detect when
 * the device is connected to Android Auto (projection mode) or Android Automotive OS (native).
 *
 * Connection Types:
 * - CONNECTION_TYPE_NOT_CONNECTED (0): Not connected to any car head unit
 * - CONNECTION_TYPE_NATIVE (1): Running natively on Android Automotive OS
 * - CONNECTION_TYPE_PROJECTION (2): Connected to Android Auto (projection mode)
 */
object AndroidAutoDetector {

    private var carConnection: CarConnection? = null
    private var connectionTypeLiveData: LiveData<Int>? = null
    private var currentConnectionType: Int = CarConnection.CONNECTION_TYPE_NOT_CONNECTED
    private val observers = mutableListOf<Observer<Int>>()

    /**
     * Initialize the Android Auto detector with application context.
     * Should be called once during application startup.
     */
    fun initialize(context: Context) {
        if (carConnection == null) {
            carConnection = CarConnection(context.applicationContext)
            connectionTypeLiveData = carConnection?.type

            // Observe connection type changes
            val observer = Observer<Int> { connectionType ->
                currentConnectionType = connectionType ?: CarConnection.CONNECTION_TYPE_NOT_CONNECTED
            }
            observers.add(observer)
            connectionTypeLiveData?.observeForever(observer)
        }
    }

    /**
     * Check if currently connected to Android Auto.
     *
     * @return true if connected to Android Auto (projection) or Android Automotive OS (native)
     */
    fun isConnectedToAndroidAuto(): Boolean {
        return currentConnectionType == CarConnection.CONNECTION_TYPE_PROJECTION ||
               currentConnectionType == CarConnection.CONNECTION_TYPE_NATIVE
    }

    /**
     * Check if connected in projection mode (Android Auto).
     *
     * @return true if connected in projection mode
     */
    fun isProjectionMode(): Boolean {
        return currentConnectionType == CarConnection.CONNECTION_TYPE_PROJECTION
    }

    /**
     * Check if running natively on Android Automotive OS.
     *
     * @return true if running on Android Automotive OS
     */
    fun isNativeMode(): Boolean {
        return currentConnectionType == CarConnection.CONNECTION_TYPE_NATIVE
    }

    /**
     * Get the current connection type.
     *
     * @return CONNECTION_TYPE_NOT_CONNECTED, CONNECTION_TYPE_NATIVE, or CONNECTION_TYPE_PROJECTION
     */
    fun getConnectionType(): Int {
        return currentConnectionType
    }

    /**
     * Get a human-readable connection status string.
     */
    fun getConnectionStatusString(): String {
        return when (currentConnectionType) {
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "Not Connected"
            CarConnection.CONNECTION_TYPE_NATIVE -> "Android Automotive OS"
            CarConnection.CONNECTION_TYPE_PROJECTION -> "Android Auto (Projection)"
            else -> "Unknown"
        }
    }

    /**
     * Clean up observers. Call this when the app is shutting down.
     */
    fun cleanup() {
        observers.forEach { observer ->
            connectionTypeLiveData?.removeObserver(observer)
        }
        observers.clear()
        carConnection = null
        connectionTypeLiveData = null
        currentConnectionType = CarConnection.CONNECTION_TYPE_NOT_CONNECTED
    }
}
