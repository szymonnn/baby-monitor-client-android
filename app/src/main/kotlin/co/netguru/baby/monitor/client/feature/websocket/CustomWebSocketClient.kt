package co.netguru.baby.monitor.client.feature.websocket

import co.netguru.baby.monitor.client.feature.websocket.ConnectionStatus.*
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import timber.log.Timber
import java.net.URI
import java.util.concurrent.TimeUnit

class CustomWebSocketClient(
        serverUrl: String,
        private val onAvailabilityChange: (ConnectionStatus) -> Unit
) : WebSocketClient(URI(serverUrl)) {

    private val compositeDisposable = CompositeDisposable()
    private var availability: ConnectionStatus = UNKNOWN

    init {
        Completable.fromAction {
            connectBlocking(5, TimeUnit.SECONDS)
        }.timeout(5, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribeBy(
                onComplete = {
                    Timber.i("Complete")
                },
                onError = {
                    Timber.e(it)
                    notifyAvailabilityChange(DISCONNECTED)
                }
        ).addTo(compositeDisposable)
    }

    fun closeClient() {
        Completable.fromAction {
            close(1000)
        }.subscribeOn(Schedulers.io()).subscribeBy(
                onComplete = {
                    Timber.i("Closed")
                },
                onError = {
                    notifyAvailabilityChange(DISCONNECTED)
                }
        ).addTo(compositeDisposable)
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        notifyAvailabilityChange(CONNECTED)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Timber.i("onClose")
        notifyAvailabilityChange(DISCONNECTED)
    }

    override fun onMessage(message: String?) {
        Timber.i("onMessage: $message")
    }

    override fun onError(ex: Exception?) {
        Timber.e(ex)
        notifyAvailabilityChange(DISCONNECTED)
    }

    internal fun sendMessage(string: String) {
        if (availability == CONNECTED) {
            send(string)
        }
    }

    internal fun onDestroy() {
        closeClient()
        compositeDisposable.dispose()
    }

    private fun notifyAvailabilityChange(availability: ConnectionStatus) {
        if (availability != this.availability) {
            this.availability = availability
            onAvailabilityChange(availability)
        }
    }
}