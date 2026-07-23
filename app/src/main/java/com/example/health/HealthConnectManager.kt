package com.example.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import com.example.data.local.entity.WaterLogEntry
import java.time.Instant

class HealthConnectManager(
    private val context: Context
) {

    // Health Connect client, created lazily
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    /**
     * Checks if the Health Connect SDK is available on this device.
     * Returns one of:
     * - HealthConnectClient.SDK_UNAVAILABLE
     * - HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
     * - HealthConnectClient.SDK_AVAILABLE
     */
    fun checkAvailability(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    private val isAvailable: Boolean
        get() = checkAvailability() == HealthConnectClient.SDK_AVAILABLE

    /**
     * Writes a single WaterLogEntry to Health Connect as a HydrationRecord.
     */
    suspend fun syncToHealthConnect(entry: WaterLogEntry) {
        if (!isAvailable) return

        val record = HydrationRecord(
            startTime = Instant.ofEpochMilli(entry.timestamp),
            startZoneOffset = null,
            endTime = Instant.ofEpochMilli(entry.timestamp),
            endZoneOffset = null,
            volume = Volume.milliliters(entry.amountMl.toDouble()),
            metadata = Metadata(
                clientRecordId = "waterapp_${entry.id}"
            )
        )

        try {
            healthConnectClient.insertRecords(listOf(record))
        } catch (e: Exception) {
            e.printStackTrace()
            // In a production app, handle specific Health Connect exceptions
        }
    }

    /**
     * Reads HydrationRecords from Health Connect within a time range,
     * filtering out records that originated from this app.
     */
    suspend fun pullExternalHydration(since: Instant, until: Instant): List<HydrationRecord> {
        if (!isAvailable) return emptyList()

        val request = ReadRecordsRequest(
            recordType = HydrationRecord::class,
            timeRangeFilter = TimeRangeFilter.between(since, until)
        )

        return try {
            val response = healthConnectClient.readRecords(request)
            response.records.filter { record ->
                record.metadata.dataOrigin.packageName != context.packageName
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
