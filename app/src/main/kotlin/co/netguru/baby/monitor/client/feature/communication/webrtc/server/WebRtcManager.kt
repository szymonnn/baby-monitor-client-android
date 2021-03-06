package co.netguru.baby.monitor.client.feature.communication.webrtc.server

import android.content.Context
import co.netguru.baby.monitor.client.feature.communication.webrtc.*
import co.netguru.baby.monitor.client.feature.communication.webrtc.observers.ConnectionObserver
import co.netguru.baby.monitor.client.feature.communication.websocket.Message
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.webrtc.*
import timber.log.Timber

class WebRtcManager constructor(
    private val sendMessage: (Message) -> Unit
) {

    private lateinit var peerConnectionFactory: PeerConnectionFactory

    private var cameraVideoCapturer: CameraVideoCapturer? = null
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack
    private lateinit var audioSource: AudioSource
    private lateinit var audioTrack: AudioTrack

    private var peerConnection: PeerConnection? = null
    private var compositeDisposable: CompositeDisposable = CompositeDisposable()
    private lateinit var connectionObserver: ConnectionObserver

    private val eglBase: EglBase by lazy { EglBase.create() }
    private val sharedContext: EglBase.Context by lazy { eglBase.eglBaseContext }
    var cameraEnabled = true
        set(isEnabled) {
            field = isEnabled
            cameraVideoCapturer?.let { enableVideo(isEnabled, it) }
        }

    private fun createCameraCapturer(cameraEnumerator: CameraEnumerator) =
        cameraEnumerator.deviceNames.asSequence()
            // prefer back-facing cameras
            .sortedBy { deviceName ->
                if (cameraEnumerator.isBackFacing(deviceName)) {
                    1
                } else {
                    2
                }
            }
            .mapNotNull { deviceName ->
                cameraEnumerator.createCapturer(deviceName, null)
            }
            .first()

    private fun enableVideo(isEnabled: Boolean, videoCapturer: CameraVideoCapturer) {
        if (isEnabled) {
            Timber.i("enableVideo")
            videoCapturer.startCapture(
                VIDEO_WIDTH,
                VIDEO_HEIGHT,
                VIDEO_FRAMERATE
            )
        } else {
            Timber.i("disableVideo")
            videoCapturer.stopCapture()
        }
    }

    fun beginCapturing(context: Context) {
        Timber.d("beginCapturing()")

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
            .apply {
                setVideoHwAccelerationOptions(sharedContext, sharedContext)
            }
        cameraVideoCapturer = createCameraCapturer(Camera2Enumerator(context))
        videoSource = peerConnectionFactory.createVideoSource(cameraVideoCapturer)
        videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)
        cameraVideoCapturer?.let { enableVideo(true, it) }
        createConnection()
    }

    private fun createConnection() {
        connectionObserver = ConnectionObserver()

        peerConnection = peerConnectionFactory.createPeerConnection(
            emptyList(),
            connectionObserver
        )
        val stream = peerConnectionFactory.createLocalMediaStream("stream")
        stream.addTrack(audioTrack)
        stream.addTrack(videoTrack)
        peerConnection?.addStream(stream)

        listenForIceCandidates(connectionObserver.streamObservable)
    }

    private fun listenForIceCandidates(streamObservable: Observable<StreamState>) {
        streamObservable
            .subscribeOn(Schedulers.io())
            .ofType(OnIceCandidatesChange::class.java)
            .subscribeBy(onNext = { iceCandidateChange ->
                if (iceCandidateChange.iceCandidateState is OnIceCandidateAdded) {
                    handleIceCandidate(
                        iceCandidateChange.iceCandidateState.iceCandidate
                    )
                }
            }, onError = { throwable -> throwable.printStackTrace() })
            .addTo(compositeDisposable)
    }

    private fun handleIceCandidate(iceCandidate: IceCandidate) {
        sendMessage(
            Message(
                iceCandidate = Message.IceCandidateData(
                    iceCandidate.sdp,
                    iceCandidate.sdpMid,
                    iceCandidate.sdpMLineIndex
                )
            )
        )
    }

    fun stopCapturing() {
        Timber.d("stopCapturing()")
        compositeDisposable.dispose()
        audioSource.dispose()
        videoSource.dispose()
        cameraVideoCapturer?.dispose()
        peerConnection?.dispose()
        peerConnectionFactory.dispose()
    }

    fun acceptOffer(offer: String) {
        Timber.i("acceptOffer($offer)")
        connectionObserver.onAcceptOffer()
        peerConnection?.run {
            setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offer))
                .doOnComplete { Timber.d("Offer set as a remote description.") }
                .andThen(createAnswer())
                .doOnSuccess { Timber.d("Answer created.") }
                .flatMapCompletable { answer: SessionDescription ->
                    sendMessage(
                        Message(
                            sdpAnswer = Message.SdpData(
                                sdp = answer.description,
                                type = answer.type.canonicalForm()
                            )
                        )
                    )
                    peerConnection?.setLocalDescription(answer)
                }
                .doOnComplete { Timber.d("Answer set as a local description.") }
                .subscribeBy(onError = {
                    connectionObserver.onSetDescriptionError()
                    sendMessage(
                        Message(
                            sdpError = it.message
                        )
                    )
                    Timber.e(it)
                })
        }?.addTo(compositeDisposable)
    }

    fun addIceCandidate(iceCandidateData: Message.IceCandidateData) {
        peerConnection?.addIceCandidate(
            IceCandidate(
                iceCandidateData.sdpMid,
                iceCandidateData.sdpMLineIndex,
                iceCandidateData.sdp
            )
        )
    }

    fun addSurfaceView(surfaceViewRenderer: SurfaceViewRenderer) {
        surfaceViewRenderer.init(sharedContext, null)
        videoTrack.addSink(surfaceViewRenderer)
    }

    fun getConnectionObservable(): Observable<RtcConnectionState> =
        connectionObserver.streamObservable.ofType(ConnectionState::class.java)
            .flatMap { Observable.just(it.connectionState) }

    companion object {
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_WIDTH = 320
        private const val VIDEO_FRAMERATE = 30
    }
}
