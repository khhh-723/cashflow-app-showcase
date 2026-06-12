package com.codex.suishouledger

import android.app.Application

class SuishouLedgerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
