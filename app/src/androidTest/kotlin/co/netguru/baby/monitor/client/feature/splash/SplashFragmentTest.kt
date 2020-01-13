package co.netguru.baby.monitor.client.feature.splash

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.netguru.baby.monitor.client.R
import co.netguru.baby.monitor.client.common.RxSchedulersOverrideRule
import co.netguru.baby.monitor.client.common.TestViewModelFactory
import co.netguru.baby.monitor.client.data.DataRepository
import co.netguru.baby.monitor.client.data.splash.AppState
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Single
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashFragmentTest {

    @get:Rule
    val schedulersRule = RxSchedulersOverrideRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val mockNavController = mock<NavController>()
    private val dataRepository = mock<DataRepository>()
    private val viewModel = SplashViewModel(dataRepository)

    @Test
    fun shouldShowOnboarding() {
        whenever(dataRepository.getSavedState()).thenReturn(Single.just(AppState.UNDEFINED))
        val scenario = launchSplashScenario()
        scenario.onFragment {
            verify(mockNavController).navigate(R.id.splashToOnboarding)
        }
    }

    @Test
    fun shouldShowServer() {
        whenever(dataRepository.getSavedState()).thenReturn(Single.just(AppState.SERVER))
        val scenario = launchSplashScenario()
        scenario.onFragment {
            verify(mockNavController).navigate(R.id.splashToServer)
        }
    }

    @Test
    fun shouldShowClient() {
        whenever(dataRepository.getSavedState()).thenReturn(Single.just(AppState.CLIENT))
        val scenario = launchSplashScenario()
        scenario.onFragment {
            verify(mockNavController).navigate(R.id.splashToClientHome)
        }
    }

    private fun launchSplashScenario(): FragmentScenario<SplashFragment> {
        return launchFragmentInContainer {
            SplashFragment().also { fragment ->
                fragment.factory = TestViewModelFactory(viewModel)
                fragment.viewLifecycleOwnerLiveData.observeForever { viewLifecycleOwner ->
                    if (viewLifecycleOwner != null) {
                        Navigation.setViewNavController(fragment.requireView(), mockNavController)
                    }
                }
            }
        }
    }
}