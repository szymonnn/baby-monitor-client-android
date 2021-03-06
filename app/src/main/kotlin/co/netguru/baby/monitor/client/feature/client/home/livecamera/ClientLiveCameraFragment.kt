package co.netguru.baby.monitor.client.feature.client.home.livecamera

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import co.netguru.baby.monitor.client.R
import co.netguru.baby.monitor.client.common.base.BaseFragment
import co.netguru.baby.monitor.client.common.extensions.showSnackbarMessage
import co.netguru.baby.monitor.client.feature.analytics.Screen
import co.netguru.baby.monitor.client.feature.babycrynotification.CryingActionIntentService
import co.netguru.baby.monitor.client.feature.client.home.BackButtonState
import co.netguru.baby.monitor.client.feature.client.home.ClientHomeViewModel
import co.netguru.baby.monitor.client.feature.communication.webrtc.ConnectionState
import co.netguru.baby.monitor.client.feature.communication.webrtc.RtcConnectionState
import co.netguru.baby.monitor.client.feature.communication.webrtc.StreamState
import co.netguru.baby.monitor.client.feature.communication.websocket.RxWebSocketClient
import kotlinx.android.synthetic.main.fragment_client_live_camera.*
import timber.log.Timber
import java.net.URI
import javax.inject.Inject

class ClientLiveCameraFragment : BaseFragment() {
    override val layoutResource = R.layout.fragment_client_live_camera
    override val screen: Screen = Screen.CLIENT_LIVE_CAMERA

    @Inject
    internal lateinit var factory: ViewModelProvider.Factory

    private val viewModel by lazy {
        ViewModelProviders.of(requireActivity(), factory)[ClientHomeViewModel::class.java]
    }

    private val fragmentViewModel by lazy {
        ViewModelProviders.of(this, factory)[ClientLiveCameraFragmentViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setBackButtonState(
            BackButtonState(
                true,
                shouldShowSnoozeDialogOnBack()
            )
        )
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.selectedChildAvailability.observe(
            viewLifecycleOwner,
            Observer { it?.let(::onAvailabilityChange) })
        fragmentViewModel.streamState.observe(viewLifecycleOwner, Observer {
            handleStreamStateChange(it)
        })
    }

    override fun onStart() {
        super.onStart()
        maybeStartCall()
    }

    override fun onStop() {
        super.onStop()
        fragmentViewModel.endCall()
    }

    private fun shouldShowSnoozeDialogOnBack() =
        arguments?.getBoolean(CryingActionIntentService.SHOULD_SHOW_SNOOZE_DIALOG) == true

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setBackButtonState(
            BackButtonState(
                shouldBeVisible = false,
                shouldShowSnoozeDialog = false
            )
        )
    }

    private fun onAvailabilityChange(connectionAvailable: Boolean) {
        Timber.d("onAvailabilityChange($connectionAvailable)")
        if (connectionAvailable) {
            maybeStartCall()
        } else {
            fragmentViewModel.endCall()
        }
    }

    private fun maybeStartCall() {
        if (!fragmentViewModel.callInProgress.get()) startCall(viewModel.rxWebSocketClient)
    }

    private fun startCall(rxWebSocketClient: RxWebSocketClient) {
        val serverUri = URI.create(viewModel.selectedChild.value?.address ?: return)
        fragmentViewModel.startCall(
            requireActivity().applicationContext,
            liveCameraRemoteRenderer,
            serverUri,
            rxWebSocketClient
        )
    }

    private fun handleStreamStateChange(streamState: StreamState) {
        when ((streamState as? ConnectionState)?.connectionState) {
            RtcConnectionState.Connected -> streamProgressBar.isVisible = false
            RtcConnectionState.Checking -> streamProgressBar.isVisible = true
            RtcConnectionState.Error -> handleBabyDeviceSdpError()
            else -> Unit
        }
    }

    private fun handleBabyDeviceSdpError() {
        showSnackbarMessage(R.string.stream_error)
        requireActivity().onBackPressed()
    }
}
