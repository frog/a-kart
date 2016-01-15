package com.frogdesign.akart

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import butterknife.bindView
import com.frogdesign.akart.model.Cars
import com.frogdesign.akart.util.*
import com.frogdesign.akart.view.AimView
import com.frogdesign.akart.view.VerticalSeekBar
import com.frogdesign.arsdk.Controller
import com.frogdesign.arsdk.TestUtils
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService
import org.artoolkit.ar.base.ARToolKit
import org.artoolkit.ar.base.NativeInterface
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class PlayActivity : AppCompatActivity() {

    companion object {
        private val TAG = PlayActivity::class.java.simpleName
        const val EXTRA_DEVICE = "PlayActivity.extra_device"
        const val DEBUG = true
    }

    private val image: ImageView by bindView(R.id.image)
    private val targets: AimView by bindView(R.id.aim)
    private val gasPedal: VerticalSeekBar by bindView(R.id.gasPedal)
    private val battery: TextView by bindView(R.id.battery)

    private var controller: Controller? = null

    private val trackedSubscriptions = TrackedSubscriptions();

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val device = intent.getParcelableExtra<ARDiscoveryDeviceService>(EXTRA_DEVICE)
        controller = Controller(baseContext, device)
        gasPedal.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                trace("Progress: %d fromUser %b", progress, fromUser)
                if (progress != 0) controller!!.speed(progress / seekBar.max.toFloat())
                else controller!!.neutral()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                controller!!.neutral()
                seekBar.progress = 0
            }
        })
    }

    override fun onStart() {
        super.onStart()
        controller!!.start()

        val FAKE_PRODUCER = false
        var bitmapByteArrayProducer = if (!FAKE_PRODUCER) controller!!.mediaStreamer()
        else {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.test)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()
            TestUtils.constantProducer(byteArray, 60)
        }

        val byteArrayProducer: Observable<ByteArray> =
                bitmapByteArrayProducer.sample(33, TimeUnit.MILLISECONDS)

        val bitmapProducer: Observable<Bitmap> = byteArrayProducer.map(CachedBitmapDecoder())

        var bitmapSubscription =
                bitmapProducer.andAsync()
                        .filter { b ->
                            trace("filtering: %b", isMainThread())
                            image.setImageBitmap(b)
                            true
                        }
                        .sample(66, TimeUnit.MILLISECONDS)
                        .filter(BmpToYUVToARtoolkitConverter())
                        .andAsync()
                        .subscribe { onFrameProcessed() }
        trackedSubscriptions.track(bitmapSubscription);

        var steerSubscription = SteeringWheel(this).stream().subscribe { steer ->
            trace("steer: %b", isMainThread())
            controller?.turn(steer / 90f)
        }
        trackedSubscriptions.track(steerSubscription);

        var batterySubscription = controller!!.batteryLevel()
                .observeOn(AndroidSchedulers.mainThread()).subscribe { bat -> battery?.text = Integer.toString(bat) }
        trackedSubscriptions.track(batterySubscription);
    }

    override fun onResume() {
        super.onResume()
        if (!ARToolKit.getInstance().initialiseNativeWithOptions(this.cacheDir.absolutePath, 16, 25)) {
            throw RuntimeException("e' tutto finito")
        }

        NativeInterface.arwSetPatternDetectionMode(NativeInterface.AR_MATRIX_CODE_DETECTION)
        NativeInterface.arwSetMatrixCodeType(NativeInterface.AR_MATRIX_CODE_3x3_HAMMING63)


        for (c in Cars.all) {
            val leftId = ARToolKit.getInstance().addMarker("single_barcode;" + c.lrMarkers.first + ";80")
            val rightId = ARToolKit.getInstance().addMarker("single_barcode;" + c.lrMarkers.second + ";80")
            c.leftAR = leftId
            c.rightAR = rightId
        }
        ARToolKit.getInstance().initialiseAR(640, 480, "Data/camera_para.dat", 0, true)
    }

    override fun onPause() {
        super.onPause()
        ARToolKit.getInstance().cleanup()
    }

    override fun onStop() {
        super.onStop()
        trackedSubscriptions.unsubAll()
        controller!!.stop()
    }

    fun onFrameProcessed() {
        targets.nullify()
        for (i in Cars.all.indices) {
            val c = Cars.all[i]
            if (c.isDetected(ARToolKit.getInstance())) {
                Log.i(TAG, "Car visibile! " + c.id)
                Log.i(TAG, "Position: " + c.estimatePosition(ARToolKit.getInstance()))
                val p = c.estimatePosition(ARToolKit.getInstance())
                targets.setTarget(c.id, p.x + targets.width / 2, -p.y + targets.height / 2)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.hideNavbar()
    }

    private fun trace(s: String, vararg args: Any) {
        if (DEBUG) Log.d(TAG, s.format(args))
    }
}