package com.mmpoint.tubenotes

import android.app.Application
import com.mmpoint.tubenotes.data.Store

class TubeNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
