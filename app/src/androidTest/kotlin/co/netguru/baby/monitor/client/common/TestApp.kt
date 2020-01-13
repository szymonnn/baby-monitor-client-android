package co.netguru.baby.monitor.client.common

import android.app.Application
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector

class TestApp : Application(), HasAndroidInjector {
    override fun androidInjector(): AndroidInjector<Any> = AndroidInjector { }
}