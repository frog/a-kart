package com.frogdesign.akart.model;

import android.support.annotation.DrawableRes
import com.frogdesign.akart.R
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService
import org.artoolkit.ar.base.ARToolKit

data class Position(var x: Float, var y: Float, var z: Float) {
    operator infix fun divAssign(d: Int) {
        x /= d.toFloat();
        y /= d.toFloat();
        z /= d.toFloat();
    }
}

/**
 * In the a-kart game the car is identified by two marker glued on the rear
 * of the cars, one on the left and one on the right of the car
 *
 */
data class Car(val id: String, val lrMarkers: Pair<Int, Int>, @DrawableRes val resId : Int) {

    var associatedDevice : ARDiscoveryDeviceService? = null
    var leftAR: Int = -1;
    var rightAR: Int = -1;

    fun isDetected(arScene: ARToolKit): Boolean = isLeftMarkerVisible(arScene) || isRightMarkerVisible(arScene)

    private fun isLeftMarkerVisible(arScene: ARToolKit) = leftAR >= 0 && arScene.queryMarkerVisible(leftAR)
    private fun isRightMarkerVisible(arScene: ARToolKit) = rightAR >= 0 && arScene.queryMarkerVisible(rightAR)

    fun estimatePosition(arScene: ARToolKit): Position {
        var pos = Position(0f,0f,0f);
        if (!isDetected(arScene)) return pos;
        var sides : Int = 0;
        val X_OFFSET = 0f;
        val X_BIAS = 0f;
        val Y_BIAS = 10f;
        if(isLeftMarkerVisible(arScene)) {
            var matrix : FloatArray = arScene.queryMarkerTransformation(leftAR)
            sides++
            pos.x += (matrix[12] + X_OFFSET)
            pos.y += matrix[13]
            pos.z += matrix[14]
        }

        if(isRightMarkerVisible(arScene)) {
            var matrix : FloatArray = arScene.queryMarkerTransformation(rightAR)
            sides++;
            pos.x += (matrix[12] - X_OFFSET)
            pos.y += matrix[13]
            pos.z += matrix[14]
        }

        pos /= sides
        //scaled to nullify depth
        var depth : Float = pos.z / -80
        pos.x /= depth
        pos.y /= depth

        pos.x += X_BIAS
        pos.y += Y_BIAS

        val X_FACTOR = 15f
        val Y_FACTOR = 18f
        pos.x *= X_FACTOR
        pos.y *= Y_FACTOR
        return pos;
    }
}

object Cars {
    @JvmField
    val all = listOf(
            Car("gargamella", Pair(0, 1), R.drawable.banana),
            Car("taxiguerrilla", Pair(2, 3), R.drawable.banana),
            Car("gianni", Pair(4, 5), R.drawable.banana),
            Car("carlo", Pair(6, 7), R.drawable.banana)
    );

    fun retrieveRelatedTo(dev: ARDiscoveryDeviceService) : Car? {
        val c = Cars.all.find { c -> dev.name.equals(c.id) }
        c?.associatedDevice = dev
        return c
    }
}

data class BoxFace(val id: String, val markerValue: Int) {

    var markerID = -1

    fun isDetected(arScene: ARToolKit): Boolean = isMarkerVisible(arScene)
    private fun isMarkerVisible(arScene: ARToolKit) = markerID >= 0 && arScene.queryMarkerVisible(markerID)

    fun estimatePosition(arScene: ARToolKit): Position {
        var pos : Position = Position(0f,0f,0f);
        if (!isMarkerVisible(arScene)) return pos;
        val X_OFFSET = 0f;
        val X_BIAS = 0f;
        val Y_BIAS = 10f;

        var matrix : FloatArray = arScene.queryMarkerTransformation(markerID)

        pos.x += (matrix[12] + X_OFFSET)
        pos.y += matrix[13]
        pos.z += matrix[14]

        //scaled to nullify depth
        var depth : Float = pos.z / -80
        pos.x /= depth
        pos.y /= depth

        pos.x += X_BIAS
        pos.y += Y_BIAS

        val X_FACTOR = 15f
        val Y_FACTOR = 18f
        pos.x *= X_FACTOR
        pos.y *= Y_FACTOR
        return pos;
    }
}



object BoxFaces {
    @JvmField
    val all = listOf(
            BoxFace("shot_random", 31),
            BoxFace("ffwd", 30),
            BoxFace("slow_all", 29)
    )
}
