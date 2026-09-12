package com.example.data.remote

import android.util.Log
import com.example.data.model.BackupData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object CloudSyncService {
    private const val TAG = "CloudSyncService"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Push all local Room data to the remote Namecheap cPanel MySQL endpoint.
     */
    suspend fun pushToCloud(
        syncUrl: String,
        secretKey: String,
        backupData: BackupData
    ): CloudSyncResult = withContext(Dispatchers.IO) {
        val trimmedUrl = syncUrl.trim()
        val trimmedKey = secretKey.trim()

        if (trimmedUrl.isBlank()) {
            return@withContext CloudSyncResult(
                success = false,
                message = "Cloud Sync URL is empty. Please configure your server URL in Settings."
            )
        }

        try {
            val requestPayload = CloudSyncPushRequest(
                secretKey = trimmedKey,
                clientTimestamp = System.currentTimeMillis(),
                appVersion = "1.0",
                backupData = backupData
            )

            val pushAdapter = moshi.adapter(CloudSyncPushRequest::class.java)
            val jsonPayload = pushAdapter.toJson(requestPayload)

            val requestBody = jsonPayload.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(trimmedUrl)
                .post(requestBody)
                .addHeader("User-Agent", "NaadrielFarmPOS/1.0")
                .addHeader("X-Sync-Action", "PUSH")
                .addHeader("X-Api-Key", trimmedKey)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext CloudSyncResult(
                    success = false,
                    message = "Server returned HTTP ${response.code}: ${responseBody.take(150)}"
                )
            }

            val responseAdapter = moshi.adapter(CloudSyncResponse::class.java)
            val syncResponse = try {
                responseAdapter.fromJson(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse sync response: $responseBody", e)
                null
            }

            if (syncResponse != null && syncResponse.status.equals("success", ignoreCase = true)) {
                val count = backupData.products.size + backupData.customers.size + backupData.salesOrders.size
                CloudSyncResult(
                    success = true,
                    message = syncResponse.message.ifBlank { "Cloud database successfully updated with $count records." },
                    recordsCount = count
                )
            } else {
                CloudSyncResult(
                    success = false,
                    message = syncResponse?.message ?: "Server error: ${responseBody.take(150)}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync push error", e)
            CloudSyncResult(
                success = false,
                message = "Cloud connection failed: ${e.localizedMessage ?: "Unknown network error"}"
            )
        }
    }

    /**
     * Pull complete farm database from Namecheap MySQL endpoint to phone.
     */
    suspend fun pullFromCloud(
        syncUrl: String,
        secretKey: String
    ): CloudSyncResult = withContext(Dispatchers.IO) {
        val trimmedUrl = syncUrl.trim()
        val trimmedKey = secretKey.trim()

        if (trimmedUrl.isBlank()) {
            return@withContext CloudSyncResult(
                success = false,
                message = "Cloud Sync URL is empty. Please configure your server URL in Settings."
            )
        }

        try {
            val requestPayload = CloudSyncPullRequest(
                secretKey = trimmedKey,
                clientTimestamp = System.currentTimeMillis()
            )

            val pullAdapter = moshi.adapter(CloudSyncPullRequest::class.java)
            val jsonPayload = pullAdapter.toJson(requestPayload)

            val requestBody = jsonPayload.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(trimmedUrl)
                .post(requestBody)
                .addHeader("User-Agent", "NaadrielFarmPOS/1.0")
                .addHeader("X-Sync-Action", "PULL")
                .addHeader("X-Api-Key", trimmedKey)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext CloudSyncResult(
                    success = false,
                    message = "Server returned HTTP ${response.code}: ${responseBody.take(150)}"
                )
            }

            val responseAdapter = moshi.adapter(CloudSyncResponse::class.java)
            val syncResponse = try {
                responseAdapter.fromJson(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse pull response: $responseBody", e)
                null
            }

            if (syncResponse != null && syncResponse.status.equals("success", ignoreCase = true) && syncResponse.backupData != null) {
                val data = syncResponse.backupData
                val count = data.products.size + data.customers.size + data.salesOrders.size
                CloudSyncResult(
                    success = true,
                    message = syncResponse.message.ifBlank { "Retrieved $count farm records from cloud MySQL database." },
                    backupData = data,
                    recordsCount = count
                )
            } else {
                CloudSyncResult(
                    success = false,
                    message = syncResponse?.message ?: "Server error: ${responseBody.take(150)}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync pull error", e)
            CloudSyncResult(
                success = false,
                message = "Failed to pull cloud database: ${e.localizedMessage ?: "Unknown network error"}"
            )
        }
    }

    /**
     * Test connection to the Namecheap endpoint.
     */
    suspend fun testConnection(
        syncUrl: String,
        secretKey: String
    ): CloudSyncResult = withContext(Dispatchers.IO) {
        val trimmedUrl = syncUrl.trim()
        val trimmedKey = secretKey.trim()

        if (trimmedUrl.isBlank()) {
            return@withContext CloudSyncResult(
                success = false,
                message = "Please enter your Namecheap Sync URL (e.g. https://yourdomain.com/api/sync.php)"
            )
        }

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .get()
                .addHeader("User-Agent", "NaadrielFarmPOS/1.0")
                .addHeader("X-Api-Key", trimmedKey)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                CloudSyncResult(
                    success = true,
                    message = "Connected to Namecheap API server successfully! (HTTP ${response.code})"
                )
            } else {
                CloudSyncResult(
                    success = false,
                    message = "Server reached but returned HTTP ${response.code}: ${responseBody.take(100)}"
                )
            }
        } catch (e: Exception) {
            CloudSyncResult(
                success = false,
                message = "Connection failed: ${e.localizedMessage ?: "Check URL and internet"}"
            )
        }
    }
}
