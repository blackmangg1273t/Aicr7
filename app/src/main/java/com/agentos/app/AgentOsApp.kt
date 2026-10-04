package com.agentos.app

import android.app.Application
import com.agentos.app.core.logging.Logger
import com.agentos.app.di.agentsModule
import com.agentos.app.di.coreModule
import com.agentos.app.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AgentOsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@AgentOsApp)
                modules(coreModule, agentsModule, uiModule)
            }
        }
        Logger.i("App", "AgentOS started (v${BuildConfig.VERSION_NAME}, API ${android.os.Build.VERSION.SDK_INT})")
    }
}
