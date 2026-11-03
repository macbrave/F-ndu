package com.example.network

import android.util.Log
import com.example.model.HeartbeatPayload
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

class MqttManager(
    private val moshi: Moshi = Moshi.Builder().build()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client: MqttAsyncClient? = null
    private val adapter = moshi.adapter(HeartbeatPayload::class.java)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingHeartbeats = MutableSharedFlow<HeartbeatPayload>(extraBufferCapacity = 64)
    val incomingHeartbeats: SharedFlow<HeartbeatPayload> = _incomingHeartbeats.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()

    private var activeRoomId: String? = null
    private var myDeviceId: String = UUID.randomUUID().toString().take(8)

    val publicBrokers = listOf(
        "tcp://broker.hivemq.com:1883",
        "tcp://broker.emqx.io:1883",
        "tcp://test.mosquitto.org:1883"
    )

    fun connect(
        roomId: String,
        deviceId: String,
        deviceName: String,
        brokerUrl: String = publicBrokers[0],
        onConnected: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        disconnect()

        this.activeRoomId = roomId.trim().lowercase()
        this.myDeviceId = deviceId

        val clientId = "NetAlert_${deviceId}_${System.currentTimeMillis() % 10000}"
        val sanitizedRoom = activeRoomId ?: "default"

        try {
            val persistence = MemoryPersistence()
            val mqttClient = MqttAsyncClient(brokerUrl, clientId, persistence)
            client = mqttClient

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 10

                // Setup Last Will and Testament (LWT)
                val willTopic = "netalert/rooms/$sanitizedRoom/will"
                val willPayload = HeartbeatPayload(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    role = "UNKNOWN",
                    timestamp = System.currentTimeMillis(),
                    sequenceNumber = 0L,
                    batteryPercentage = 0,
                    isCharging = false,
                    networkType = "NONE",
                    isEmergencyWill = true,
                    message = "BROKER_UNEXPECTED_DISCONNECT"
                )
                val willBytes = adapter.toJson(willPayload).toByteArray(Charsets.UTF_8)
                setWill(willTopic, willBytes, 1, false)
            }

            mqttClient.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d("MqttManager", "MQTT connected (reconnect=$reconnect) to $serverURI")
                    _isConnected.value = true
                    subscribeToRoom(sanitizedRoom)
                    onConnected?.invoke()
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w("MqttManager", "MQTT connection lost: ${cause?.message}")
                    _isConnected.value = false
                    _connectionErrors.tryEmit(cause?.message ?: "قطع ارتباط با سرور کارگزار")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (message == null) return
                    try {
                        val payloadStr = String(message.payload, Charsets.UTF_8)
                        val payload = adapter.fromJson(payloadStr)
                        if (payload != null) {
                            scope.launch {
                                _incomingHeartbeats.emit(payload)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MqttManager", "Error parsing MQTT payload", e)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttManager", "Connect call success")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val err = exception?.message ?: "خطا در اتصال به بروکر"
                    Log.e("MqttManager", "Connect call failed: $err")
                    _isConnected.value = false
                    _connectionErrors.tryEmit(err)
                    onError?.invoke(err)
                }
            })

        } catch (e: Exception) {
            val err = e.message ?: "خطای ناشناخته در راه اندازی کلاینت MQTT"
            Log.e("MqttManager", "Failed to init MQTT", e)
            _isConnected.value = false
            _connectionErrors.tryEmit(err)
            onError?.invoke(err)
        }
    }

    private fun subscribeToRoom(roomId: String) {
        val topic = "netalert/rooms/$roomId/#"
        try {
            client?.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttManager", "Subscribed to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MqttManager", "Subscribe failed for $topic: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e("MqttManager", "Exception during subscribe", e)
        }
    }

    fun publishHeartbeat(payload: HeartbeatPayload, onComplete: ((Boolean) -> Unit)? = null) {
        val room = activeRoomId ?: return
        val currentClient = client
        if (currentClient == null || !currentClient.isConnected) {
            onComplete?.invoke(false)
            return
        }

        try {
            val topic = if (payload.isEmergencyWill) {
                "netalert/rooms/$room/will"
            } else {
                "netalert/rooms/$room/heartbeat"
            }
            val json = adapter.toJson(payload)
            val message = MqttMessage(json.toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = false
            }

            currentClient.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    onComplete?.invoke(true)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MqttManager", "Publish heartbeat failed: ${exception?.message}")
                    onComplete?.invoke(false)
                }
            })
        } catch (e: Exception) {
            Log.e("MqttManager", "Publish exception", e)
            onComplete?.invoke(false)
        }
    }

    fun disconnect() {
        try {
            client?.let {
                if (it.isConnected) {
                    it.disconnect(2000)
                }
                it.close()
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "Error disconnecting MQTT", e)
        } finally {
            client = null
            _isConnected.value = false
            activeRoomId = null
        }
    }
}
