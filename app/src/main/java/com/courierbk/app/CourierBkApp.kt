package com.courierbk.app

import android.app.Application

class CourierBkApp : Application() {

    override fun onCreate() {

        super.onCreate()

        LogUtil.init(
            this
        )
    }
}