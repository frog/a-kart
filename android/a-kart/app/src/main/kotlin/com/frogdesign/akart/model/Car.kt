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
        var pos : Position = Position(0f,0f,0f);
        if (!isDetected(arScene)) return pos;
        var sides : Int = 0;
        val X_OFFSET = 0f;
        val X_BIAS = 16f;
        val Y_BIAS = 25f;
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
            //yellow
            Car("gargamella", Pair(0, 1), R.drawable.banana),//, Scalar(50.0, 158.0, 160.0, 0.0)),
            //red
            Car("taxiguerrilla", Pair(2, 3), R.drawable.banana)//, Scalar(252.0, 205.0, 170.0, 0.0)),
//            //blue
//            Car("taxiguerrilla", Pair(2, 3)),//, Scalar(165.0, 155.0, 120.0, 0.0)),
//            //green
//            Car("taxiguerrilla", Pair(2, 3))// Scalar(93.0, 182.0, 147.0))
    );

    fun retrieveRelatedTo(dev: ARDiscoveryDeviceService) : Car? {
        val c = Cars.all.find { c -> dev.name.equals(c.id) }
        c?.associatedDevice = dev
        return c
    }
}