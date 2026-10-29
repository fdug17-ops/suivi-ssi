package fr.perso.mescalories

import android.app.Application
import fr.perso.mescalories.data.AppDatabase
import fr.perso.mescalories.data.ProfileStore
import fr.perso.mescalories.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MesCaloriesApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.create(this), ProfileStore(this))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repository.seedIfNeeded() }
    }
}
