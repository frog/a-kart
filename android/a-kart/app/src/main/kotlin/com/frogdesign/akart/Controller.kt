package com.frogdesign.akart

import android.content.Context
import android.util.Log
import com.frogdesign.akart.util.isMainThread
import com.parrot.arsdk.arcontroller.*
import com.parrot.arsdk.ardiscovery.*
import com.parrot.arsdk.arsal.ARNativeDataHelper
import rx.Observable
import rx.Subscriber
import rx.subscriptions.Subscriptions


/**

 */
class Controller(ctx: Context, service: ARDiscoveryDeviceService?) : ARDeviceControllerListener {

    private val deviceController: ARDeviceController
    private val jumpingSumo: ARFeatureJumpingSumo

    private var batteryLevel: Int? = -1

    init {
        if (service == null) throw IllegalArgumentException("Cannot connect to null service.")
        val productIdInt = service.productID
        val productId = ARDiscoveryService.getProductFromProductID(productIdInt)
        trace("ProductId: %s", productId.toString())

        if (ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_JS == productId) {
            try {
                val device = ARDiscoveryDevice()
                val netDeviceService = service.device as ARDiscoveryDeviceNetService

                device.initWifi(ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_JS,
                        netDeviceService.name,
                        netDeviceService.ip,
                        netDeviceService.port)

                deviceController = ARDeviceController(device)
                deviceController.addListener(this)
                jumpingSumo = deviceController.featureJumpingSumo

            } catch (e: ARDiscoveryException) {
                //multicatch
                throw RuntimeException(e)
            } catch (e: ARControllerException) {
                throw RuntimeException(e)
            }

        } else {
            throw IllegalArgumentException("I can only drive JumpingSumos!")
        }
    }

    fun start() {
        val error = deviceController.start()
        if (error.value != 0) {
            throw RuntimeException("Cannot start Controller for")
        }
    }

    fun stop() {
        val error = deviceController.stop()
        if (error.value != 0) {
            throw RuntimeException("Cannot stop Controller for")
        }
    }

    fun mediaStreamer(): Observable<ByteArray> {
        return Observable.create { subscriber ->
            trace("mediaStreamer.OnSusbascribe: MAIN? ", isMainThread())
            val listener = object : ARDeviceControllerStreamListener {

                private var data: ByteArray? = ByteArray(150000)

                override fun onFrameReceived(arDeviceController: ARDeviceController, arFrame: ARFrame) {
                    trace("mediaStreamer.onFrameReceived: MAIN? %b", isMainThread())
                    if (subscriber == null) return
                    if (!arFrame.isIFrame) return
                    if (data == null) {
                        data = arFrame.byteData
                    } else ARNativeDataHelper.copyData(arFrame, data)

                    subscriber.onNext(data)
                }

                var n : Int = 0;

                override fun onFrameTimeout(arDeviceController: ARDeviceController) {
                    Log.w(TAG, "onFrameTimeout" + ++n)
                }
            }
            deviceController.addStreamListener(listener)
            deviceController.featureJumpingSumo.sendMediaStreamingVideoEnable(ON)
            subscriber!!.add(Subscriptions.create {
                trace("mediastreamer.unsubscribe")
                deviceController.removeStreamListener(listener)
                deviceController.featureJumpingSumo.sendMediaStreamingVideoEnable(OFF)
            })
        }
    }

    fun speed(percentage: Float) {
        val actual = (SPEED_MAX * percentage).toByte()
        jumpingSumo.setPilotingPCMDSpeed(actual)
        jumpingSumo.setPilotingPCMDFlag(ON)
    }

    fun turn(percentage: Float) {
        val dataToBeSent = (-TURN_MAX * percentage).toByte()
        //trace("turn %d", dataToBeSent)
        if (dataToBeSent > -TURN_DEADZONE && dataToBeSent < TURN_DEADZONE) {
//            jumpingSumo.setPilotingPCMDTurn(OFF)
//            jumpingSumo.setPilotingPCMDFlag(ON)
            return
        }
        jumpingSumo.setPilotingPCMDTurn(dataToBeSent)
        //jumpingSumo.setPilotingPCMDFlag(ON)
    }

    fun neutral() {
        jumpingSumo.setPilotingPCMDSpeed(OFF)
        jumpingSumo.setPilotingPCMDTurn(OFF)
        jumpingSumo.setPilotingPCMDFlag(OFF)
    }

    override // called when the state of the device controller has changed
    fun onStateChanged(deviceController: ARDeviceController,
                       newState: ARCONTROLLER_DEVICE_STATE_ENUM?, error: ARCONTROLLER_ERROR_ENUM?) {
        if (newState != null) Log.i(TAG, "onStateChanged: " + newState.toString())
        if (error != null) Log.e(TAG, "onStateChanged: " + error.toString())
    }

    override fun onExtensionStateChanged(arDeviceController: ARDeviceController,
                                         newState: ARCONTROLLER_DEVICE_STATE_ENUM?,
                                         product: ARDISCOVERY_PRODUCT_ENUM, s: String,
                                         error: ARCONTROLLER_ERROR_ENUM?) {
        if (newState != null) Log.i(TAG, "onExtensionStateChanged: " + newState.toString() + ", " + composeDesc(product, s))
        if (error != null) Log.e(TAG, "onExtensionStateChanged: " + error.toString() + ", " + composeDesc(product, s))
    }

    private fun composeDesc(ardiscovery_product_enum: ARDISCOVERY_PRODUCT_ENUM, s: String): String {
        return "product: " + ardiscovery_product_enum.toString() + " , s: " + s
    }

    override fun onCommandReceived(deviceController: ARDeviceController,
                          commandKey: ARCONTROLLER_DICTIONARY_KEY_ENUM?, elementDictionary: ARControllerDictionary?) {
        if (commandKey == null) throw RuntimeException("Received a null command!")
        if (commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED) {
            if (elementDictionary != null) {
                val args = elementDictionary[ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY] ?: throw RuntimeException("Received a null args dictionary!")
                val batValue = args[ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT] as Int

                // do what you want with the battery level
                //trace("Battery: %d", batValue)
                if (batValue != null) batteryLevel = batValue.toInt()
            } else {
                Log.e(TAG, "elementDictionary is null")
            }
            if (battery != null) battery!!.onNext(batteryLevel)
        } else if (commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_WIFISIGNALCHANGED) {
           //
        } else if (commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_JUMPINGSUMO_NETWORKSTATE_LINKQUALITYCHANGED) {
            //
        } else {
            //Log.i(TAG, "Command: " + commandKey.name)
        }
    }

    private fun logAll(elems : ARControllerDictionary) {
        if (elems.entries != null) for ((k,v) in elems.entries) {
            Log.i(TAG, "(%s, %v)".format(k,v))
        }
    }

    private var battery: Subscriber<in Int>? = null

    fun batteryLevel(): Observable<Int> {
        return Observable.create { subscriber ->
            battery = subscriber
            subscriber.onNext(batteryLevel)
            subscriber.add(Subscriptions.create { battery = null })
        }
    }

    private fun trace(s: String, vararg args: Any) {
        if (TRACE) Log.d(TAG, s.format(args))
    }

    companion object {
        private val TAG = Controller::class.java.simpleName
        private val TRACE = false


        private val ON = 1.toByte()
        private val OFF = 0.toByte()

        private val TURN_MAX: Byte = 50
        private val TURN_DEADZONE: Byte = 10
        private val SPEED_MAX: Byte = 50
    }
}