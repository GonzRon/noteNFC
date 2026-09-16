package com.loosecannon.servicetag

import android.app.Application
import com.loosecannon.servicetag.di.AppGraph

class NoteNfcApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
