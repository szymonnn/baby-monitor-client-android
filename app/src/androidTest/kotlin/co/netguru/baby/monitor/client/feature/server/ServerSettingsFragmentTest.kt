package co.netguru.baby.monitor.client.feature.server

import android.content.Intent
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.netguru.baby.monitor.client.R
import co.netguru.baby.monitor.client.common.TestViewModelFactory
import co.netguru.baby.monitor.client.data.DataRepository
import co.netguru.baby.monitor.client.feature.settings.ConfigurationViewModel
import co.netguru.baby.monitor.client.feature.settings.ServerSettingsFragment
import co.netguru.baby.monitor.client.feature.settings.SettingsViewModel
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.schibsted.spain.barista.interaction.BaristaClickInteractions.clickOn
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerSettingsFragmentTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val mockNavController = mock<NavController>()
    private val dataRepository = mock<DataRepository>()
    private val serverViewModel = mock<ServerViewModel>()
    private val settingsViewModel = SettingsViewModel(dataRepository)
    private val configurationViewModel = mock<ConfigurationViewModel>()

    @Before
    fun setup() {
        whenever(configurationViewModel.resetInProgress).thenReturn(MutableLiveData(false))
    }

    @Test
    fun shouldEnableSendRecordSwitch() {
        whenever(configurationViewModel.isUploadEnabled()).thenReturn(true)
        launchSettingsScenario()
        onView(withId(R.id.sendRecordingsSw)).check(matches(isChecked()))
    }

    @Test
    fun shouldDisableSendRecordSwitch() {
        whenever(configurationViewModel.isUploadEnabled()).thenReturn(true)
        launchSettingsScenario()
        onView(withId(R.id.sendRecordingsSw)).check(matches(isChecked()))
    }

    @Test
    fun shouldShowNetguruLanding() {
        Intents.init()
        launchSettingsScenario()
        clickOn(R.id.secondPartTv)
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse("https://www.netguru.com/"))
            )
        )
        Intents.release()
    }

    @Test
    fun shouldShowPlayStore() {
        Intents.init()
        launchSettingsScenario()
        clickOn(R.id.rateUsBtn)
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse("market://details?id=com.netguru.babyguard.debug"))
            )
        )
        Intents.release()
    }

    @Test
    fun shouldCloseDrawer() {
        launchSettingsScenario()
        clickOn(R.id.closeIbtn)
        verify(serverViewModel).toggleDrawer(false)
    }

    @Test
    fun shouldLogout() {
        launchSettingsScenario()
        clickOn(R.id.settingsLogoutBtn)
        verify(configurationViewModel).resetApp(any())
    }

    @Test
    fun shouldEnableUpload() {
        launchSettingsScenario()
        onView(withId(R.id.sendRecordingsSw)).perform(ViewActions.swipeRight())
        verify(configurationViewModel).setUploadEnabled(true)
    }

    private fun launchSettingsScenario(): FragmentScenario<ServerSettingsFragment> {
        return launchFragmentInContainer {
            ServerSettingsFragment().also { fragment ->
                fragment.factory =
                    TestViewModelFactory(serverViewModel, settingsViewModel, configurationViewModel)
                fragment.viewLifecycleOwnerLiveData.observeForever { viewLifecycleOwner ->
                    if (viewLifecycleOwner != null) {
                        Navigation.setViewNavController(fragment.requireView(), mockNavController)
                    }
                }
            }
        }
    }
}