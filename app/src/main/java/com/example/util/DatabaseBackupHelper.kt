package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupHelper {

    private const val TAG = "DatabaseBackupHelper"
    private const val PREFS_BACKUP = "farm_database_backups"
    private const val PREF_LAST_BACKUP_TIME = "last_backup_time"
    private const val PREF_LAST_BACKUP_STATUS = "last_backup_status"
    private const val PREF_LAST_BACKUP_COUNT = "last_backup_item_count"
    private const val PREF_LAST_BACKUP_PATH = "last_backup_file_path"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(BackupData::class.java).indent("  ")

    /**
     * Reads all tables directly from Room and constructs a BackupData object in memory.
     */
    suspend fun createBackupData(database: AppDatabase): BackupData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(now))

        val settings = database.appSettingsDao().getSettingsDirect()
        val products = database.productDao().getAllProductsDirect()
        val customers = database.customerDao().getAllCustomersDirect()
        val salesOrders = database.saleOrderDao().getAllOrdersDirect()
        val payments = database.paymentDao().getAllPaymentsDirect()
        val inventoryLogs = database.inventoryLogDao().getAllLogsDirect()

        val totalDebt = customers.sumOf { it.currentBalance }
        val totalRevenue = salesOrders.sumOf { it.totalAmount }

        val summary = BackupSummary(
            totalProducts = products.size,
            totalCustomers = customers.size,
            totalSalesOrders = salesOrders.size,
            totalPayments = payments.size,
            totalInventoryLogs = inventoryLogs.size,
            totalOutstandingDebt = totalDebt,
            totalRevenue = totalRevenue
        )

        BackupData(
            formatVersion = 1,
            appName = "Naadriel Enterprise Farm POS",
            exportedAt = now,
            exportedAtFormatted = dateFormatted,
            businessName = settings?.businessName ?: "Naadriel Enterprise",
            settings = settings,
            products = products,
            customers = customers,
            salesOrders = salesOrders,
            payments = payments,
            inventoryLogs = inventoryLogs,
            summary = summary
        )
    }

    /**
     * Reads all tables from Room and serializes them into a timestamped JSON file.
     * Stored in both external app files directory and internal files directory.
     */
    suspend fun exportDatabaseToJson(
        context: Context,
        database: AppDatabase,
        isAutoBackup: Boolean = false
    ): Pair<File, BackupData> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fileDateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(now))

        val backupData = createBackupData(database)
        val jsonString = backupAdapter.toJson(backupData)

        // 2. Prepare target directories (External storage & Internal storage)
        val extDir = try { context.getExternalFilesDir(null) } catch (e: Exception) { null }
        val externalBackupDir = if (extDir != null) File(extDir, "backups").apply { mkdirs() } else null
        val internalBackupDir = File(context.filesDir, "backups").apply { mkdirs() }

        val prefix = if (isAutoBackup) "naadriel_autobackup" else "naadriel_farm_backup"
        val timestampedFileName = "${prefix}_${fileDateSuffix}.json"
        val latestFileName = "naadriel_farm_backup_latest.json"

        // Save to internal storage (guaranteed)
        val internalTargetFile = File(internalBackupDir, timestampedFileName)
        internalTargetFile.writeText(jsonString)
        val internalLatestFile = File(internalBackupDir, latestFileName)
        internalLatestFile.writeText(jsonString)

        // Save to external storage if accessible
        val targetFile = if (externalBackupDir != null && externalBackupDir.exists()) {
            val extTarget = File(externalBackupDir, timestampedFileName)
            extTarget.writeText(jsonString)
            val extLatest = File(externalBackupDir, latestFileName)
            extLatest.writeText(jsonString)
            pruneOldBackups(externalBackupDir, maxKeep = 15)
            extTarget
        } else {
            internalTargetFile
        }

        pruneOldBackups(internalBackupDir, maxKeep = 15)

        // 3. Save status in SharedPreferences
        val totalItems = backupData.products.size + backupData.customers.size + backupData.salesOrders.size + backupData.payments.size
        saveBackupMetadata(
            context = context,
            timestamp = now,
            status = "Success • $totalItems items saved",
            itemCount = totalItems,
            filePath = targetFile.absolutePath
        )

        Log.d(TAG, "Database exported successfully to ${targetFile.absolutePath} ($totalItems records)")
        Pair(targetFile, backupData)
    }

    /**
     * Parse backup JSON string safely.
     */
    fun parseBackupJson(jsonString: String): BackupData? {
        return try {
            backupAdapter.fromJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing backup JSON", e)
            null
        }
    }

    /**
     * Restores full database from a parsed BackupData entity using Room transaction.
     */
    suspend fun restoreDatabase(
        database: AppDatabase,
        backupData: BackupData
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                // 1. Settings
                backupData.settings?.let { settings ->
                    database.appSettingsDao().saveSettings(settings)
                }

                // 2. Products
                if (backupData.products.isNotEmpty()) {
                    database.productDao().insertAll(backupData.products)
                }

                // 3. Customers
                if (backupData.customers.isNotEmpty()) {
                    database.customerDao().insertAll(backupData.customers)
                }

                // 4. Sales Orders
                if (backupData.salesOrders.isNotEmpty()) {
                    database.saleOrderDao().insertAll(backupData.salesOrders)
                }

                // 5. Payments
                if (backupData.payments.isNotEmpty()) {
                    database.paymentDao().insertAll(backupData.payments)
                }

                // 6. Inventory Logs
                if (backupData.inventoryLogs.isNotEmpty()) {
                    database.inventoryLogDao().insertAll(backupData.inventoryLogs)
                }
            }

            RestoreResult(
                success = true,
                message = "Restored successfully: ${backupData.products.size} products, ${backupData.customers.size} customers, ${backupData.salesOrders.size} sales, and ${backupData.payments.size} payment entries.",
                productsCount = backupData.products.size,
                customersCount = backupData.customers.size,
                salesCount = backupData.salesOrders.size,
                paymentsCount = backupData.payments.size,
                logsCount = backupData.inventoryLogs.size,
                settingsRestored = backupData.settings != null,
                backupTimestamp = backupData.exportedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore database from backup", e)
            RestoreResult(
                success = false,
                message = "Failed to restore backup: ${e.localizedMessage ?: "Unknown error"}",
                error = e.localizedMessage
            )
        }
    }

    /**
     * Reads a JSON backup from an Android Content Uri (e.g. from File Picker) and restores it.
     */
    suspend fun restoreFromUri(
        context: Context,
        uri: Uri,
        database: AppDatabase
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
            if (jsonString.isNullOrBlank()) {
                return@withContext RestoreResult(
                    success = false,
                    message = "The selected backup file is empty or could not be read."
                )
            }

            val backupData = parseBackupJson(jsonString)
                ?: return@withContext RestoreResult(
                    success = false,
                    message = "Invalid backup format. Ensure the selected file is a valid JSON backup exported from Naadriel Farm."
                )

            restoreDatabase(database, backupData)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from URI $uri", e)
            RestoreResult(
                success = false,
                message = "Failed to open or read backup: ${e.localizedMessage ?: "File read error"}",
                error = e.localizedMessage
            )
        }
    }

    /**
     * Writes backup JSON directly into a user-selected file Uri (from CreateDocument launcher).
     */
    suspend fun writeBackupToUri(
        context: Context,
        uri: Uri,
        backupData: BackupData
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = backupAdapter.toJson(backupData)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write backup to Uri $uri", e)
            false
        }
    }

    /**
     * Lists all saved local backup files on device storage.
     */
    fun getSavedBackupFiles(context: Context): List<BackupFileInfo> {
        val list = mutableListOf<BackupFileInfo>()
        val extDir = try { context.getExternalFilesDir(null) } catch (e: Exception) { null }
        val externalBackupDir = if (extDir != null) File(extDir, "backups") else null
        val internalBackupDir = File(context.filesDir, "backups")

        val files = mutableListOf<File>()
        try {
            if (externalBackupDir != null && externalBackupDir.exists()) {
                externalBackupDir.listFiles()?.filter { it.extension.lowercase() == "json" }?.let { files.addAll(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error listing external backups", e)
        }

        try {
            if (internalBackupDir.exists()) {
                internalBackupDir.listFiles()?.filter { it.extension.lowercase() == "json" }?.let { files.addAll(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error listing internal backups", e)
        }

        // Distinct by filename and sort newest first
        val distinctFiles = files.distinctBy { it.name }.sortedByDescending { it.lastModified() }
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        for (file in distinctFiles) {
            try {
                val sizeKb = file.length() / 1024.0
                val formattedSize = if (sizeKb < 1.0) "${file.length()} B" else String.format(Locale.US, "%.1f KB", sizeKb)
                val isAuto = file.name.contains("autobackup", ignoreCase = true)
                list.add(
                    BackupFileInfo(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        formattedSize = formattedSize,
                        timestamp = file.lastModified(),
                        formattedDate = sdf.format(Date(file.lastModified())),
                        isAutoBackup = isAuto
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing backup file item", e)
            }
        }
        return list
    }

    /**
     * Creates an Android Share Sheet Intent for the backup JSON file.
     */
    fun createShareIntent(context: Context, backupFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Naadriel Farm Database Backup (${backupFile.name})")
            putExtra(
                Intent.EXTRA_TEXT,
                "Here is the local database backup for Naadriel Enterprise Farm POS. You can restore this file anytime in the app's Settings screen."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun pruneOldBackups(dir: File, maxKeep: Int) {
        try {
            val timestampedFiles = dir.listFiles()
                ?.filter { it.name.startsWith("naadriel_") && !it.name.contains("latest") && it.extension == "json" }
                ?.sortedByDescending { it.lastModified() } ?: return

            if (timestampedFiles.size > maxKeep) {
                timestampedFiles.drop(maxKeep).forEach { oldFile ->
                    oldFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune old backups", e)
        }
    }

    private fun saveBackupMetadata(
        context: Context,
        timestamp: Long,
        status: String,
        itemCount: Int,
        filePath: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(PREF_LAST_BACKUP_TIME, timestamp)
            .putString(PREF_LAST_BACKUP_STATUS, status)
            .putInt(PREF_LAST_BACKUP_COUNT, itemCount)
            .putString(PREF_LAST_BACKUP_PATH, filePath)
            .apply()
    }

    fun getLastBackupInfo(context: Context): Triple<Long, String, Int> {
        val prefs = context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(PREF_LAST_BACKUP_TIME, 0L)
        val status = prefs.getString(PREF_LAST_BACKUP_STATUS, "No backup performed yet") ?: "No backup performed yet"
        val count = prefs.getInt(PREF_LAST_BACKUP_COUNT, 0)
        return Triple(timestamp, status, count)
    }
}
