package com.monte.indoorpositioning;
/*
 MIT License

 Copyright (c) 2017 Montvydas

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

import static android.hardware.SensorManager.getAltitude;

/**
 * Created by monte on 28/03/2017.
 *
 * Motion sensors are being handled in the class. This includes accelerometer, gyro, magnetometer
 * and barometer sensors. The class provides nicer access to the sensors and handles some of the
 * sensor data processing (e.g. automatically detects floor changes, applies filters for accelerometer
 * data, calculates the angles from rotation vector).
 */
public class MotionSensorManager implements SensorEventListener{
    private OnMotionSensorManagerListener motionSensorManagerListener;  // Information to the activities is being passed throughthe listener
    private SensorManager sensorManager;        // Sensor manager is used to get access to all sensors
    private Sensor rotationSensor;              // rotation vector sensor (magneto + accelero + gyro)
    private Sensor linearAccelSensor;
    private Sensor gameRotationSensor;          // accelero + gyro only
    private Sensor pressureSensor;              // barometer sensor

    // PI and TWO_PI constants are used to translate degrees into range from 0 up to 360˚
    private final static double PI = Math.PI;
    private final static double TWO_PI = PI*2;

    /**
     * Constructor of the class. Initialises al of the sensors.
     * @param context
     */
    public MotionSensorManager(Context context) {
        // Get instance of the sensor manager and then access all of the required sensors.
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        // 4 sensors are being accessed as follows:

        // rotation vector is used to know the direction of the north + pitch and roll.
        // This is used i nthe beginning when need to calibrate the gameRotationSensor
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        // Linear acceleration is used to detect steps and calculate the walked distance
        // It gives acceleration in phone's axis x, y & z
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // the sensor use only gyros and accelerometer. Needs to be calibrated to know where the north
        // is at the beginning but indoors works better than magnetometer
        gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);

        // get the pressure at the user location. Is used to know when user goes up or down the floor
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    /**
     * Allow setting the listener through this function
     * @param motionSensorManagerListener
     */
    public void setOnMotionSensorManagerListener(OnMotionSensorManagerListener motionSensorManagerListener){
        this.motionSensorManagerListener = motionSensorManagerListener;
    }

    /**
     * Stop sensor updates.
     */
    public void unregisterMotionSensors(){
        sensorManager.unregisterListener(this);
    }

    /**
     * Register updates for each of the sensors one by one.
     */
    public void registerMotionSensors(){
        // Register the sensor listener to start recording rotation measurement values
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gameRotationSensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // When sensors are being updated, call certain functions.
        switch (event.sensor.getType()){
            case Sensor.TYPE_ROTATION_VECTOR:
                // rotation vector
                getRotationFromRotationVector(event.values.clone(), false);
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                // rotation vector
                getRotationFromRotationVector(event.values.clone(), true);
                break;
            case Sensor.TYPE_PRESSURE:
                //barometer
                getFloorChanges(event.values.clone());
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                getWorldLinearAcceleration(event.values.clone());
                break;
        }
    }

    float[] rotationMatrix = new float[16];
    boolean isRotationMatrixAvailable = false;

    private void getWorldLinearAcceleration(float[] rawLinearAccelValues)
    {
        if (motionSensorManagerListener != null){
            motionSensorManagerListener.onLinearAccValueUpdated(rawLinearAccelValues);
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

//    private List<Float> pressureFilter = new ArrayList<>();         // Pressure values are being filtered
    private long previousTime = System.currentTimeMillis();         // Need time updates
    private List<Float> altitudeHistory = new ArrayList<>();        // collected altitude values in history
    private float pressure = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
    private boolean isFirstPressureReading = true;
    /**
     * Function used to check for floor changes.
     * @param rawPressureValues
     */
    public void getFloorChanges (float[] rawPressureValues){
        // Firstly add the values to the value filter. This is used
        // to smooth them. 20 values are being smoothed.
        final float alpha = 0.9f;
        if (isFirstPressureReading){
            pressure = rawPressureValues[0];
            isFirstPressureReading = false;
        } else {
            pressure = alpha * pressure + (1 - alpha) * rawPressureValues[0];
        }

        // get the current time and check that half of a second passed since the last time the sensor was updated.
        long currTime = System.currentTimeMillis();
        if (previousTime + 500 < currTime){
            // Get the averaged value fromthe filter list
//            float millibars_of_pressure = calculateAverage(pressureFilter);
            // Use Google provided formula for finding the altitude from pressure values
            float altitude = getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);

            // Collect 30 values in the altitude history list, which is equivalent to 15 seconds of capturing
            altitudeHistory.add(altitude);
            if (altitudeHistory.size() > 30){
                altitudeHistory.remove(0);
            }

            // first is the value at the moment, last is value 15 seconds before now.
            float first = altitude;
            float last = altitudeHistory.get(0);

            // Check that if the value difference is 2m, then determine if user went up or down.
            // Call the listener method.
            if (first - last > 2) {
                if (motionSensorManagerListener != null){
                    motionSensorManagerListener.onFloorChange(true);
                }
                // remove all of the values because floor was updated!
                altitudeHistory.clear();
            } else if (first - last < -2) {
                if (motionSensorManagerListener != null){
                    motionSensorManagerListener.onFloorChange(false);
                }
                altitudeHistory.clear();
            }
            // update the previous time
            previousTime = currTime;
        }
    }

    /**
     * function to calculate the list of floats average
     * @param vals
     * @return
     */
    public static float calculateAverage(List <Float> vals) {
        float sum = 0;
        // go through all of the list and add the values, the divide by the size of the list
        if(!vals.isEmpty()) {
            for (Float mark : vals) {
                sum += mark;
            }
            return sum / vals.size();
        }
        return sum;
    }

    /**
     * get the rotation from the rotation vector. This is used when we receive type_rotation_vector values
     * @param rawRotationValues
     */
    private void getRotationFromRotationVector(float[] rawRotationValues, boolean isGyroOnly) {
        //All this is used to get smooth behaviour of a compass
        float[] rotationMatrix = new float[16];
        float[] remappedRotationMatrix = new float[16];
        float[] orientation = new float[3];

        // Sensor returns a vector value and we need to translate that to rotation matrix using the provided method
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rawRotationValues);
        this.rotationMatrix = rotationMatrix;
        isRotationMatrixAvailable = true;
        // Remap coordinate system to eliminate gimbal lock completely.
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_X, remappedRotationMatrix);
        // From the rotation matrix we can get actual orientation / rotation
        SensorManager.getOrientation(remappedRotationMatrix, orientation);

        // orientation[0] represents yaw or simply the degrees. It must be minused as we remaped coordinate system
        // orientation[2] is pitch and orientation[1] is roll.
        // All values need to be mapped from 0 to TWO_PI (0 - 360˚)
        float degree = (float) mod(orientation[0] + TWO_PI + PI * 3 / 2, TWO_PI);
        float roll = (float) mod(orientation[1] + TWO_PI, TWO_PI);
        float pitch = ((float) mod(orientation[2] + TWO_PI + PI, TWO_PI));

        // Translate from radians to degrees for all of the angles
        degree *= 180.0 / PI;
        roll *= 180 / PI;
        pitch *= 180 / PI;

        // Call the listener method to tell that value was updated
        if (motionSensorManagerListener != null) {
            if (isGyroOnly) {
                motionSensorManagerListener.onGyroOrientationCalculated(new float[]{degree, roll, pitch});
            } else {
                motionSensorManagerListener.onRotationOrientationCalculated(new float[]{degree, roll, pitch});
            }
        }

    }

    /**
     * mod function written in a nicer way.
     * @param a
     * @param b
     * @return
     */
    private double mod(double a, double b){
        return a % b;
    }

    /**
     * Functions which calculates the next location of the user based on the motion sensors only.
     * @param degree
     * @param latLng
     * @param scale
     * @return
     */
    public static LatLng getLatLngFromMotion(float degree, LatLng latLng, double scale) {
        // Get the weights in X and Y directions.
        float weightX = (float) Math.cos(Math.toRadians(degree));
        float weightY = (float) Math.sin(Math.toRadians(degree));

        // latitude and longitude have different weight factors
        double lat = latLng.latitude + weightX * 0.000009 * scale;
        double lng = latLng.longitude + weightY * 0.000016 * scale;

        return new LatLng(lat, lng);
    }

    /**
     * Listeners for the activities to know when the data changed.
     */
    public interface OnMotionSensorManagerListener{
        void onLinearAccValueUpdated(float[] acceleration);
        void onGyroOrientationCalculated(float[] orientation);
        void onRotationOrientationCalculated(float[] orientation);
        void onFloorChange(boolean offset);
    }
}
