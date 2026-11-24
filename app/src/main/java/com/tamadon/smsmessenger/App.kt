package com.tamadon.smsmessenger

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tamadon.smsmessenger.core.Contacts
import com.tamadon.smsmessenger.core.Loc
import com.tamadon.smsmessenger.core.Notifier
import com.tamadon.smsmessenger.core.PurgeWorker
import com.tamadon.smsmessenger.data.AppDb
import com.tamadon.smsmessenger.data.MIGRATION_1_2
import com.tamadon.smsmessenger.data.MIGRATION_2_3
import com.tamadon.smsmessenger.data.Prefs
import com.tamadon.smsmessenger.data.ProviderSync
import com.tamadon.smsmessenger.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repo
        private set

    /** Process-wide scope for work that must outlive any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        // Compose state used by the contact cache must be created on the main thread.
        Contacts.warmUp()
        val db = Room.databaseBuilder(this, AppDb::class.java, "sms.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        // Creating the repository starts collecting the home list right away on a background thread,
        // which also opens the database: the first screen then has its data ready when it appears.
        repo = Repo(this, db, appScope)

        // Everything else that is not needed to draw the first frame happens in the background.
        appScope.launch {
            // Pick up messages sent / received by another SMS app while it was the default one.
            ProviderSync.syncInto(this@App, repo)
            Notifier.createChannels(localized())
            Contacts.reload(this@App)
            WorkManager.getInstance(this@App).enqueueUniquePeriodicWork(
                "trash_purge",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }

    /** Application context using the language chosen in settings (for notifications and other background UI). */
    fun localized(): Context = Loc.wrap(this, prefs.language)

    companion object {
        lateinit var instance: App
            private set
    }
}

/** Process-wide UI state used by receivers to decide whether to post a notification. */
object AppState {
    @Volatile
    var viewingKey: String? = null
    var uiVisible by mutableStateOf(false)

    fun isViewing(key: String) = uiVisible && viewingKey == key
}
