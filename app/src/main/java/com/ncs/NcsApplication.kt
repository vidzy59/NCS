package com.ncs

import android.app.Application
import com.ncs.core.MemoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NcsApplication : Application() {

    lateinit var memoryStore: MemoryStore
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        memoryStore = MemoryStore(this)
        scope = CoroutineScope(Dispatchers.IO)
    }
}
