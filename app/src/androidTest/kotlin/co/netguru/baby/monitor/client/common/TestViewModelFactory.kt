package co.netguru.baby.monitor.client.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TestViewModelFactory(vararg viewModels: ViewModel) : ViewModelProvider.Factory {
    private val viewModelsArray = viewModels
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        viewModelsArray.forEach {
            if (modelClass.isAssignableFrom(it::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return it as T
            }
        }
        throw IllegalArgumentException("Couldn't create $modelClass.")
    }
}