package com.test.testcompass;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Surface;
import android.view.WindowManager;

import com.test.testcompass.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // The rate sensor events will be delivered at. As the Android documentation states, this is only
    // a hint to the system and the events might actually be received faster or slower then this
    // specified rate. Since the minimum Android API levels about 9, we are able to set this value
    // ourselves rather than using one of the provided constants which deliver updates too quickly for
    // our use case. The default is set to 100ms
    static final int SENSOR_DELAY_MICROS = 100 * 1000;
    // Filtering coefficient 0 < ALPHA < 1
    private static final float ALPHA = 0.45f;

    @NonNull
    private WindowManager windowManager;
    @NonNull
    private SensorManager sensorManager;

    // Not all devices have a compassSensor
    @Nullable
    private Sensor rotationVectorSensor;
    private Sensor orientationSensor;
    @Nullable
    private Sensor gravitySensor;
    @Nullable
    private Sensor magneticFieldSensor;

    @NonNull
    private float[] truncatedRotationVectorValue = new float[4];
    @NonNull
    private float[] rotationMatrix = new float[9];
    private float[] rotationVectorValue;

    private long compassUpdateNextTimestamp;
    private long orientationUpdateNextTimestamp;
    private long gravityMagneticUpdateNextTimestamp;
    @Nullable
    private float[] gravityValues = new float[3];
    @Nullable
    private float[] magneticValues = new float[3];

    private int rotationValueCount;
    private int orientationValueCount;
    private int gravityValueCount;
    private int magneticValueCount;

    private ActivityMainBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = DataBindingUtil.setContentView(this, R.layout.activity_main);

        windowManager = getWindowManager();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SENSOR_DELAY_MICROS);
        }
        if (orientationSensor != null) {
            sensorManager.registerListener(this, orientationSensor, SENSOR_DELAY_MICROS);
        }
        if (gravitySensor != null && magneticFieldSensor != null) {
            sensorManager.registerListener(this, gravitySensor, SENSOR_DELAY_MICROS);
            sensorManager.registerListener(this, magneticFieldSensor, SENSOR_DELAY_MICROS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (rotationVectorSensor != null) {
            sensorManager.unregisterListener(this, rotationVectorSensor);
        }
        if (orientationSensor != null) {
            sensorManager.unregisterListener(this, orientationSensor);
        }
        if (gravitySensor != null && magneticFieldSensor != null) {
            sensorManager.unregisterListener(this, gravitySensor);
            sensorManager.unregisterListener(this, magneticFieldSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            ++rotationValueCount;
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime < compassUpdateNextTimestamp) {
                return;
            }

            rotationVectorValue = getRotationVectorFromSensorEvent(event);
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorValue);

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Remap the axes as if the device screen was the instrument panel,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.rotationVectorVert.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Assume the device screen was parallel to the ground,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                        break;
                }
                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.rotationVectorHori.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
                b.rotationVectorPitchRoll.setText(String.valueOf((float) Math.toDegrees(orientation[1])) + ", " + String.valueOf((float) Math.toDegrees(orientation[2])));
            }

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Remap the axes as if the device screen was upside down and facing back,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.rotationVectorVert2.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Remap the axes as if the device screen was face down,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                        break;
                }
                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.rotationVectorHori2.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            {
                int worldAxisForDeviceAxisX;
                int worldAxisForDeviceAxisY;

                // Assume the device screen was parallel to the ground,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                if (orientation[1] < -Math.PI / 4) {
                    // The pitch is less than -45 degrees.
                    // Remap the axes as if the device screen was the instrument panel,
                    // and adjust the rotation matrix for the device orientation.
                    switch (windowManager.getDefaultDisplay().getRotation()) {
                        case Surface.ROTATION_90:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                            break;
                        case Surface.ROTATION_180:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                            break;
                        case Surface.ROTATION_270:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                            break;
                        case Surface.ROTATION_0:
                        default:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                            break;
                    }

                    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                            worldAxisForDeviceAxisY, adjustedRotationMatrix);

                    // Transform rotation matrix into azimuth/pitch/roll
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation);
                } else if (orientation[1] > Math.PI / 4) {
                    // The pitch is larger than 45 degrees.
                    // Remap the axes as if the device screen was upside down and facing back,
                    // and adjust the rotation matrix for the device orientation.
                    switch (windowManager.getDefaultDisplay().getRotation()) {
                        case Surface.ROTATION_90:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                            break;
                        case Surface.ROTATION_180:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                            break;
                        case Surface.ROTATION_270:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                            break;
                        case Surface.ROTATION_0:
                        default:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                            break;
                    }

                    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                            worldAxisForDeviceAxisY, adjustedRotationMatrix);

                    // Transform rotation matrix into azimuth/pitch/roll
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation);
                } else if (Math.abs(orientation[2]) > Math.PI / 2) {
                    // The roll is less than -90 degrees, or is larger than 90 degrees.
                    // Remap the axes as if the device screen was face down,
                    // and adjust the rotation matrix for the device orientation.
                    switch (windowManager.getDefaultDisplay().getRotation()) {
                        case Surface.ROTATION_90:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                            break;
                        case Surface.ROTATION_180:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                            break;
                        case Surface.ROTATION_270:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                            break;
                        case Surface.ROTATION_0:
                        default:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                            break;
                    }

                    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                            worldAxisForDeviceAxisY, adjustedRotationMatrix);

                    // Transform rotation matrix into azimuth/pitch/roll
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation);
                }

                // The x-axis is all we care about here.
                b.rotationVectorFixed.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            compassUpdateNextTimestamp = currentTime + 500;
        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            ++orientationValueCount;
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime < orientationUpdateNextTimestamp) {
                return;
            }

            b.orientation.setText(String.valueOf((event.values[0] + 360) % 360));

            float adjustment = 0;
            switch (windowManager.getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_90:
                    adjustment = 90;
                    break;
                case Surface.ROTATION_180:
                    adjustment = 180;
                    break;
                case Surface.ROTATION_270:
                    adjustment = 270;
                    break;
            }
            b.orientationFixed.setText(String.valueOf((event.values[0] + adjustment + 360) % 360));

            orientationUpdateNextTimestamp = currentTime + 500;
        } else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                ++gravityValueCount;
                gravityValues = lowPassFilter(getRotationVectorFromSensorEvent(event), gravityValues);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                ++magneticValueCount;
                magneticValues = lowPassFilter(getRotationVectorFromSensorEvent(event), magneticValues);
            }

            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime < gravityMagneticUpdateNextTimestamp) {
                return;
            }

            SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues);

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Remap the axes as if the device screen was the instrument panel,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.accelerometerMagneticVert.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Assume the device screen was parallel to the ground,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.accelerometerMagneticHori.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
                b.accelerometerMagneticPitchRoll.setText(String.valueOf((float) Math.toDegrees(orientation[1])) + ", " + String.valueOf((float) Math.toDegrees(orientation[2])));
            }

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Remap the axes as if the device screen was upside down and facing back,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.accelerometerMagneticVert2.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            {
                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                // Remap the axes as if the device screen was face down,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                // The x-axis is all we care about here.
                b.accelerometerMagneticHori2.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            {
                int worldAxisForDeviceAxisX;
                int worldAxisForDeviceAxisY;

                // Assume the device screen was parallel to the ground,
                // and adjust the rotation matrix for the device orientation.
                switch (windowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                        break;
                }

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                SensorManager.getOrientation(adjustedRotationMatrix, orientation);

                if (orientation[1] < -Math.PI / 4) {
                    // The pitch is less than -45 degrees.
                    // Remap the axes as if the device screen was the instrument panel,
                    // and adjust the rotation matrix for the device orientation.
                    switch (windowManager.getDefaultDisplay().getRotation()) {
                        case Surface.ROTATION_90:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                            break;
                        case Surface.ROTATION_180:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                            break;
                        case Surface.ROTATION_270:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                            break;
                        case Surface.ROTATION_0:
                        default:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                            break;
                    }

                    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                            worldAxisForDeviceAxisY, adjustedRotationMatrix);

                    // Transform rotation matrix into azimuth/pitch/roll
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation);
                } else if (orientation[1] > Math.PI / 4) {
                    // The pitch is larger than 45 degrees.
                    // Remap the axes as if the device screen was upside down and facing back,
                    // and adjust the rotation matrix for the device orientation.
                    switch (windowManager.getDefaultDisplay().getRotation()) {
                        case Surface.ROTATION_90:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                            break;
                        case Surface.ROTATION_180:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                            break;
                        case Surface.ROTATION_270:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                            break;
                        case Surface.ROTATION_0:
                        default:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                            break;
                    }

                    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                            worldAxisForDeviceAxisY, adjustedRotationMatrix);

                    // Transform rotation matrix into azimuth/pitch/roll
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation);
                } else if (Math.abs(orientation[2]) > Math.PI / 2) {
                    // The roll is less than -90 degrees, or is larger than 90 degrees.
                    // Remap the axes as if the device screen was face down,
                    // and adjust the rotation matrix for the device orientation.
                    switch (windowManager.getDefaultDisplay().getRotation()) {
                        case Surface.ROTATION_90:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Y;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                            break;
                        case Surface.ROTATION_180:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_Y;
                            break;
                        case Surface.ROTATION_270:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_Y;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                            break;
                        case Surface.ROTATION_0:
                        default:
                            worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                            worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Y;
                            break;
                    }

                    SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                            worldAxisForDeviceAxisY, adjustedRotationMatrix);

                    // Transform rotation matrix into azimuth/pitch/roll
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation);
                }

                // The x-axis is all we care about here.
                b.accelerometerMagneticFixed.setText(String.valueOf(((float) Math.toDegrees(orientation[0]) + 360) % 360));
            }

            gravityMagneticUpdateNextTimestamp = currentTime + 500;
        }
        b.sensorValueCount.setText(rotationValueCount + " / " + orientationValueCount + " / " + gravityValueCount + " / " + magneticValueCount);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Helper function, that filters newValues, considering previous values
     *
     * @param newValues      array of float, that contains new data
     * @param smoothedValues array of float, that contains previous state
     * @return float filtered array of float
     */
    @NonNull
    private float[] lowPassFilter(@NonNull float[] newValues, @Nullable float[] smoothedValues) {
        if (smoothedValues == null) {
            return newValues;
        }
        for (int i = 0; i < newValues.length; i++) {
            smoothedValues[i] = smoothedValues[i] + ALPHA * (newValues[i] - smoothedValues[i]);
        }
        return smoothedValues;
    }

    @NonNull
    private float[] getRotationVectorFromSensorEvent(@NonNull SensorEvent event) {
        if (event.values.length > 4) {
            // On some Samsung devices SensorManager.getRotationMatrixFromVector
            // appears to throw an exception if rotation vector has length > 4.
            // For the purposes of this class the first 4 values of the
            // rotation vector are sufficient (see crbug.com/335298 for details).
            // Only affects Android 4.3
            System.arraycopy(event.values, 0, truncatedRotationVectorValue, 0, 4);
            return truncatedRotationVectorValue;
        } else {
            return event.values;
        }
    }
}
