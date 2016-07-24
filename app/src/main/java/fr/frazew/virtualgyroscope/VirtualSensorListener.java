package fr.frazew.virtualgyroscope;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class VirtualSensorListener implements SensorEventListener {
    private SensorEventListener realListener = null;
    private Sensor registeredSensor = null;
    public Sensor sensorRef = null;

    public VirtualSensorListener(SensorEventListener realListener, Sensor registeredSensor) {
        this.realListener = realListener;
        this.registeredSensor = registeredSensor;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (this.sensorRef != null) {
            event.sensor = this.sensorRef;
            realListener.onSensorChanged(event);
        }
    }

    public Sensor getSensor() {
        return this.registeredSensor;
    }

    public SensorEventListener getRealListener() {
        return this.realListener;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
