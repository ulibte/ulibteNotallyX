package com.philkes.notallyx.utils

import android.app.Application
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DataSchemaMigrations"

fun Application.checkForMigrations() {
    val preferences = NotallyXPreferences.getInstance(this)
    val dataSchemaId = preferences.dataSchemaId.value
    var newDataSchemaId = dataSchemaId

    MainScope().launch {
        withContext(Dispatchers.IO) {
            if (dataSchemaId < 1) {
                moveAttachments(preferences)
                newDataSchemaId = 1
            }
            preferences.setDataSchemaId(newDataSchemaId)
        }
    }
}

private fun Application.moveAttachments(preferences: NotallyXPreferences) {
    val toPrivate = !preferences.dataInPublicFolder.value
    log(
        TAG,
        "Running migration 1: Moving attachments to ${if(toPrivate) "private" else "public"} folder",
    )
    migrateAllAttachments(toPrivate)
}
