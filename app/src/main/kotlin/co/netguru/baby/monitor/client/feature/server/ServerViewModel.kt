package co.netguru.baby.monitor.client.feature.server

import androidx.lifecycle.*
import co.netguru.baby.monitor.client.common.ISchedulersProvider
import co.netguru.baby.monitor.client.common.SingleLiveEvent
import co.netguru.baby.monitor.client.data.DataRepository
import co.netguru.baby.monitor.client.data.communication.websocket.ClientConnectionStatus
import co.netguru.baby.monitor.client.data.server.CameraState
import co.netguru.baby.monitor.client.data.splash.AppState
import co.netguru.baby.monitor.client.feature.communication.nsd.NsdServiceManager
import co.netguru.baby.monitor.client.feature.communication.webrtc.RtcConnectionState
import co.netguru.baby.monitor.client.feature.communication.webrtc.server.WebRtcService
import co.netguru.baby.monitor.client.feature.communication.websocket.Message
import co.netguru.baby.monitor.client.feature.communication.websocket.WebSocketServerService
import dagger.Lazy
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ServerViewModel @Inject constructor(
    private val nsdServiceManager: NsdServiceManager,
    private val dataRepository: DataRepository,
    private val receiveFirebaseTokenUseCase: Lazy<ReceiveFirebaseTokenUseCase>,
    private val schedulersProvider: ISchedulersProvider
) : ViewModel() {

    private val mutableShouldDrawerBeOpen = MutableLiveData<Boolean>()
    internal val shouldDrawerBeOpen: LiveData<Boolean> = mutableShouldDrawerBeOpen
    private val mutableTimer = MutableLiveData<Long>()
    private var timerDisposable: Disposable? = null
    private val compositeDisposable = CompositeDisposable()

    private val mutableRtcConnectionStatus = MutableLiveData<RtcConnectionState>()
    private val mutableCameraState = MutableLiveData(
        CameraState(
            previewEnabled = true,
            streamingEnabled = false
        )
    )
    internal val nsdState = nsdServiceManager.nsdStateLiveData
    internal val cameraState: LiveData<CameraState> = mutableCameraState
    val rtcConnectionStatus: LiveData<RtcConnectionState> = mutableRtcConnectionStatus
    private val mutableBabyNameStatus = MutableLiveData<String>()
    val babyNameStatus: LiveData<String> = mutableBabyNameStatus

    private val mutablePulsatingViewStatus = MutableLiveData<ClientConnectionStatus>()
    val pulsatingViewStatus: LiveData<ClientConnectionStatus> = mutablePulsatingViewStatus

    private val mutablePairingCodeLiveData = MutableLiveData<String>()
    internal val pairingCodeLiveData: LiveData<String> = mutablePairingCodeLiveData

    internal val webSocketAction = SingleLiveEvent<String>()

    internal val previewingVideo = Transformations.map(cameraState) { it.previewEnabled }
        .distinctUntilChanged()

    internal val timer: LiveData<Long> =
        mutableTimer

    internal fun resetTimer() {
        timerDisposable?.dispose()
        timerDisposable = Observable.intervalRange(
            1, VIDEO_PREVIEW_TOTAL_TIME, 0, 1, TimeUnit.SECONDS,
            schedulersProvider.computation()
        )
            .observeOn(schedulersProvider.mainThread())
            .subscribeBy(
                onNext = { elapsedSeconds ->
                    val secondsLeft = VIDEO_PREVIEW_TOTAL_TIME - elapsedSeconds
                    mutableTimer.postValue(secondsLeft)
                },
                onComplete = {
                    mutableTimer.postValue(null)
                    toggleVideoPreview(false)
                }
            )
    }

    internal fun registerNsdService() {
        nsdServiceManager.registerService()
    }

    internal fun unregisterNsdService() {
        nsdServiceManager.unregisterService()
    }

    internal fun saveConfiguration() {
        dataRepository.saveConfiguration(AppState.SERVER)
            .subscribeOn(schedulersProvider.io())
            .subscribeBy(
                onComplete = { Timber.i("state saved") },
                onError = Timber::e
            ).addTo(compositeDisposable)
    }

    fun handleWebSocketServerBinder(binder: WebSocketServerService.Binder) {
        Timber.d("handleWebSocketServerBinder($binder)")

        compositeDisposable += binder.clientConnectionStatus().subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { connectionStatus ->
                    mutablePulsatingViewStatus.postValue(connectionStatus)
                },
                onError = { Timber.e(it) }
            )

        compositeDisposable += binder.messages()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (ws, message) ->
                message.action?.let {
                    handleMessageAction(it)
                }
                message.pushNotificationsToken?.let {
                    receiveFirebaseToken(ws.remoteSocketAddress.address.hostAddress, it)
                }
                message.babyName?.let { name ->
                    mutableBabyNameStatus.postValue(name)
                }
                message.pairingCode?.let {
                    mutablePairingCodeLiveData.postValue(it)
                }
            }
    }

    private fun handleMessageAction(action: String) {
        webSocketAction.postValue(action)
    }

    fun approvePairingCode(binder: WebSocketServerService.Binder) {
        binder.sendMessage(
            Message(
                pairingApproved = true
            )
        )
    }

    fun disapprovePairingCode(binder: WebSocketServerService.Binder) {
        binder.sendMessage(
            Message(
                pairingApproved = false
            )
        )
        mutablePairingCodeLiveData.postValue("")
    }

    fun handleRtcServerConnectionState(webRtcServiceBinder: WebRtcService.Binder) {
        compositeDisposable += webRtcServiceBinder.getConnectionObservable()
            .subscribeOn(schedulersProvider.io())
            .subscribeBy(
                onNext = {
                    handleStreamState(it)
                    mutableRtcConnectionStatus.postValue(it)
                },
                onError = { mutableRtcConnectionStatus.postValue(RtcConnectionState.Error) }
            )
    }

    fun toggleDrawer(shouldBeOpened: Boolean) {
        mutableShouldDrawerBeOpen.postValue(shouldBeOpened)
    }

    fun toggleVideoPreview(shouldShowPreview: Boolean) {
        handleCameraState(previewEnabled = shouldShowPreview)
        if (!shouldShowPreview && timerDisposable?.isDisposed == false) timerDisposable?.dispose()
    }

    override fun onCleared() {
        compositeDisposable.dispose()
        super.onCleared()
    }

    private fun handleCameraState(
        previewEnabled: Boolean = mutableCameraState.value?.previewEnabled == true,
        streamingEnabled: Boolean = mutableCameraState.value?.streamingEnabled == true
    ) {
        mutableCameraState.postValue(CameraState(previewEnabled, streamingEnabled))
    }

    private fun handleStreamState(it: RtcConnectionState?) {
        when (it) {
            RtcConnectionState.Connected -> handleCameraState(streamingEnabled = true)
            RtcConnectionState.Disconnected -> handleCameraState(streamingEnabled = false)
        }
    }

    private fun receiveFirebaseToken(ipAddress: String, token: String) {
        compositeDisposable += receiveFirebaseTokenUseCase.get()
            .receiveToken(ipAddress = ipAddress, token = token)
            .subscribeOn(Schedulers.io())
            .subscribeBy(
                onComplete = {
                    Timber.d("Firebase token saved for address $ipAddress.")
                },
                onError = { error ->
                    Timber.w(error, "Couldn't save Firebase token.")
                }
            )
    }

    companion object {
        const val VIDEO_PREVIEW_TOTAL_TIME = 65L
    }
}
