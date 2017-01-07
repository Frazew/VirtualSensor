package fr.frazew.virtualgyroscope.hooks;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.SparseArray;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.Util;
import fr.frazew.virtualgyroscope.VirtualSensorListener;
import fr.frazew.virtualgyroscope.XposedMod;

public class SensorChangeHook {
    private static final float NS2S = 1.0f / 1000000000.0f;

    public static List<Object> changeSensorValues(Sensor s, float[] accelerometerValues, float[] magneticValues, Object listener, float[] prevRotationMatrix, long timestamp, long prevTimestamp,
                                                  float[] prevValues, float[][] lastFilterValues, SparseArray<Sensor> sensors) {
        float[] returnValues = new float[3];
        VirtualSensorListener virtualListener = (VirtualSensorListener) listener;

        // Per default, we set the sensor to null so that it doesn't accidentally send the accelerometer's values
        virtualListener.sensorRef = null;

        // We only work when it's an accelerometer's reading. If we were to work on both events, the timeDifference for the gyro would often be 0 resulting in NaN or Infinity
        if (s.getType() == Sensor.TYPE_ACCELEROMETER && (virtualListener.getSensor() != null || virtualListener.isDummyGyroListener)) {

            if (virtualListener.isDummyGyroListener || virtualListener.getSensor().getType() == Sensor.TYPE_GYROSCOPE) {
                float timeDifference = Math.abs((float) (timestamp - prevTimestamp) * NS2S);
                List<Object> valuesList = getGyroscopeValues(accelerometerValues, magneticValues, prevRotationMatrix, timeDifference);
                if (timeDifference != 0.0F) {
                    prevTimestamp = timestamp;
                    prevRotationMatrix = (float[]) valuesList.get(1);
                    float[] values = (float[]) valuesList.get(0);

                    if (Float.isNaN(values[0]) || Float.isInfinite(values[0]))
                        XposedBridge.log("VirtualSensor: Value #" + 0 + " is NaN or Infinity, this should not happen");

                    if (Float.isNaN(values[1]) || Float.isInfinite(values[1]))
                        XposedBridge.log("VirtualSensor: Value #" + 1 + " is NaN or Infinity, this should not happen");

                    if (Float.isNaN(values[2]) || Float.isInfinite(values[2]))
                        XposedBridge.log("VirtualSensor: Value #" + 2 + " is NaN or Infinity, this should not happen");


                    List<Object> filter = filterValues(values, lastFilterValues, prevValues);
                    values = (float[]) filter.get(0);
                    prevValues = (float[]) filter.get(1);
                    lastFilterValues = (float[][]) filter.get(2);

                    System.arraycopy(values, 0, returnValues, 0, values.length);
                    if (sensors != null) virtualListener.sensorRef = sensors.valueAt(sensors.indexOfKey(XposedMod.sensorTypetoHandle.get(Sensor.TYPE_GYROSCOPE)));
                }
            } else if ((Build.VERSION.SDK_INT >= 19 && virtualListener.getSensor().getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) || virtualListener.getSensor().getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] values = new float[3];
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magneticValues);
                float[] quaternion = Util.rotationMatrixToQuaternion(rotationMatrix);

                values[0] = quaternion[1];
                values[1] = quaternion[2];
                values[2] = quaternion[3];

                System.arraycopy(values, 0, returnValues, 0, values.length);
                if (virtualListener.getSensor().getType() == Sensor.TYPE_ROTATION_VECTOR)
                    if (sensors != null) virtualListener.sensorRef = sensors.valueAt(sensors.indexOfKey(XposedMod.sensorTypetoHandle.get(Sensor.TYPE_ROTATION_VECTOR)));
                else if (Build.VERSION.SDK_INT >= 19)
                        if (sensors != null) virtualListener.sensorRef = sensors.valueAt(sensors.indexOfKey(XposedMod.sensorTypetoHandle.get(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)));
            } else if (virtualListener.getSensor().getType() == Sensor.TYPE_GRAVITY) {
                float[] values = new float[3];
                float[] rotationMatrix = new float[9];
                float[] gravity = new float[]{0F, 0F, 9.81F};

                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magneticValues);
                float[] gravityRot = new float[3];
                gravityRot[0] = gravity[0] * rotationMatrix[0] + gravity[1] * rotationMatrix[3] + gravity[2] * rotationMatrix[6];
                gravityRot[1] = gravity[0] * rotationMatrix[1] + gravity[1] * rotationMatrix[4] + gravity[2] * rotationMatrix[7];
                gravityRot[2] = gravity[0] * rotationMatrix[2] + gravity[1] * rotationMatrix[5] + gravity[2] * rotationMatrix[8];

                values[0] = gravityRot[0];
                values[1] = gravityRot[1];
                values[2] = gravityRot[2];

                System.arraycopy(values, 0, returnValues, 0, values.length);
                if (sensors != null) virtualListener.sensorRef = sensors.valueAt(sensors.indexOfKey(XposedMod.sensorTypetoHandle.get(Sensor.TYPE_GRAVITY)));
            } else if (virtualListener.getSensor().getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                float[] values = new float[3];
                float[] rotationMatrix = new float[9];
                float[] gravity = new float[]{0F, 0F, 9.81F};

                SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magneticValues);

                float[] gravityRot = new float[3];
                gravityRot[0] = gravity[0] * rotationMatrix[0] + gravity[1] * rotationMatrix[3] + gravity[2] * rotationMatrix[6];
                gravityRot[1] = gravity[0] * rotationMatrix[1] + gravity[1] * rotationMatrix[4] + gravity[2] * rotationMatrix[7];
                gravityRot[2] = gravity[0] * rotationMatrix[2] + gravity[1] * rotationMatrix[5] + gravity[2] * rotationMatrix[8];

                values[0] = accelerometerValues[0] - gravityRot[0];
                values[1] = accelerometerValues[1] - gravityRot[1];
                values[2] = accelerometerValues[2] - gravityRot[2];

                System.arraycopy(values, 0, returnValues, 0, values.length);
                if (sensors != null) virtualListener.sensorRef = sensors.valueAt(sensors.indexOfKey(XposedMod.sensorTypetoHandle.get(Sensor.TYPE_LINEAR_ACCELERATION)));
            }
        }

        List<Object> list = new ArrayList<>();
        list.add(returnValues);
        list.add(prevTimestamp);
        list.add(prevRotationMatrix);
        list.add(prevValues);
        list.add(lastFilterValues);
        return list;
    }

    @SuppressWarnings("unchecked")
    public static class API1617 extends XC_MethodHook {
        // Noise reduction
        float lastFilterValues[][] = new float[3][10];
        float prevValues[] = new float[3];

        //Sensor values
        float[] magneticValues = new float[3];
        float[] accelerometerValues = new float[3];

        //Keeping track of the previous rotation matrix and timestamp
        float[] prevRotationMatrix = new float[9];
        long prevTimestamp = 0;

        // Stores the magnetic values read each second for the last 10 seconds (approximately, it depends on the delay of the sensors currently used)
        float[][] oneSecondIntervalMagneticValues = new float[10][3];
        long lastMagneticValuesIntervalRead = 0;
        long lastMessage = 0;
        int magneticValuesIntervalCount = 0;

        private XC_LoadPackage.LoadPackageParam lpparam;

        public API1617(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        @Override
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);
            SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "sHandleToSensor");

            Object listener = XposedHelpers.getObjectField(param.thisObject, "mSensorEventListener");
            if (listener instanceof VirtualSensorListener) { // This is our custom listener type, we can start working on the values
                Sensor s = (Sensor)param.args[0];

                //All calculations need data from these two sensors, we can safely get their value every time
                if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                    this.accelerometerValues = ((float[]) (param.args[1])).clone();
                }
                if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    this.magneticValues = ((float[]) (param.args[1])).clone();
                    if (Math.abs(lastMagneticValuesIntervalRead - ((long[]) param.args[2])[0]) * NS2S >= 1) {
                        this.oneSecondIntervalMagneticValues[this.magneticValuesIntervalCount] = this.magneticValues;
                        this.lastMagneticValuesIntervalRead = ((long[]) param.args[2])[0];
                        this.magneticValuesIntervalCount++;
                        if (this.magneticValuesIntervalCount > 9) this.magneticValuesIntervalCount = 0;
                    }
                }

                /*
                    Check that the readings from the magnetic field are not too wrong. If they have been 0 or close to 0 for the last 10 seconds, we can safely assume there's a problem
                 */
                float lastMagneticValuesXSum = this.oneSecondIntervalMagneticValues[0][0]+this.oneSecondIntervalMagneticValues[1][0]+this.oneSecondIntervalMagneticValues[2][0]+this.oneSecondIntervalMagneticValues[3][0]+this.oneSecondIntervalMagneticValues[4][0]+this.oneSecondIntervalMagneticValues[5][0]+this.oneSecondIntervalMagneticValues[6][0]+this.oneSecondIntervalMagneticValues[7][0]+this.oneSecondIntervalMagneticValues[8][0]+this.oneSecondIntervalMagneticValues[9][0];
                float lastMagneticValuesYSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][1]+this.oneSecondIntervalMagneticValues[2][1]+this.oneSecondIntervalMagneticValues[3][1]+this.oneSecondIntervalMagneticValues[4][1]+this.oneSecondIntervalMagneticValues[5][1]+this.oneSecondIntervalMagneticValues[6][1]+this.oneSecondIntervalMagneticValues[7][1]+this.oneSecondIntervalMagneticValues[8][1]+this.oneSecondIntervalMagneticValues[9][1];
                float lastMagneticValuesZSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][2]+this.oneSecondIntervalMagneticValues[2][2]+this.oneSecondIntervalMagneticValues[3][2]+this.oneSecondIntervalMagneticValues[4][2]+this.oneSecondIntervalMagneticValues[5][2]+this.oneSecondIntervalMagneticValues[6][2]+this.oneSecondIntervalMagneticValues[7][2]+this.oneSecondIntervalMagneticValues[8][2]+this.oneSecondIntervalMagneticValues[9][2];

                if ((Math.abs(lastMagneticValuesXSum/10) == 0.0F || Math.abs(lastMagneticValuesYSum/10) == 0.0F || Math.abs(lastMagneticValuesZSum/10) == 0.0F) && Math.abs(lastMessage - ((long[]) param.args[2])[0]) * NS2S >= 10 && lastMessage != 0) {
                    XposedBridge.log("VirtualSensor: Magnetic values are likely to be wrong, if this message seems to appear often, it is likely that there is a problem");
                    this.lastMessage = ((long[]) param.args[2])[0];
                }

                List<Object> list = changeSensorValues(s, this.accelerometerValues, this.magneticValues, listener, this.prevRotationMatrix, ((long[]) param.args[2])[0], this.prevTimestamp, this.prevValues, this.lastFilterValues, sensors);

                // Just making sure nothing goes wrong with the values returned by the rotation vector for example
                if (((float[])param.args[1]).length == 3) {
                    System.arraycopy((float[])list.get(0), 0, (float[])param.args[1], 0, ((float[])param.args[1]).length);
                }
                else {
                    System.arraycopy((float[])list.get(0), 0, (float[])param.args[1], 0, ((float[])list.get(0)).length);
                }

                this.prevTimestamp = (long)list.get(1);
                this.prevRotationMatrix = (float[])list.get(2);
                this.prevValues = (float[])list.get(3);
                this.lastFilterValues = (float[][])list.get(4);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static class API18Plus extends XC_MethodHook {
        // Noise reduction
        float lastFilterValues[][] = new float[3][10];
        float prevValues[] = new float[3];

        //Sensor values
        float[] magneticValues = new float[3];
        float[] accelerometerValues = new float[3];

        //Keeping track of the previous rotation matrix and timestamp
        float[] prevRotationMatrix = new float[9];
        long prevTimestamp = 0;

        // Stores the magnetic values read each second for the last 10 seconds (approximately, it depends on the delay of the sensors currently used)
        float[][] oneSecondIntervalMagneticValues = new float[10][3];
        long lastMagneticValuesIntervalRead = 0;
        long lastMessage = 0;
        int magneticValuesIntervalCount = 0;

        private XC_LoadPackage.LoadPackageParam lpparam;

        public API18Plus(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        @Override
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);

            Object listener = XposedHelpers.getObjectField(param.thisObject, "mListener");
            if (listener instanceof VirtualSensorListener) { // This is our custom listener type, we can start working on the values
                int handle = (int) param.args[0];
                Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
                SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(mgr.getClass(), "sHandleToSensor");
                Sensor s = sensors.get(handle);

                //All calculations need data from these two sensors, we can safely get their value every time
                if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                    this.accelerometerValues = ((float[]) (param.args[1])).clone();
                }
                if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    this.magneticValues = ((float[]) (param.args[1])).clone();
                    if (Math.abs(lastMagneticValuesIntervalRead - (long) param.args[3]) * NS2S >= 1) {
                        this.oneSecondIntervalMagneticValues[this.magneticValuesIntervalCount] = this.magneticValues;
                        this.lastMagneticValuesIntervalRead = (long) param.args[3];
                        this.magneticValuesIntervalCount++;
                        if (this.magneticValuesIntervalCount > 9) this.magneticValuesIntervalCount = 0;
                    }
                }

                /*
                    Check that the readings from the magnetic field are not too wrong. If they have been 0 or close to 0 for the last 10 seconds, we can safely assume there's a problem
                 */
                float lastMagneticValuesXSum = this.oneSecondIntervalMagneticValues[0][0]+this.oneSecondIntervalMagneticValues[1][0]+this.oneSecondIntervalMagneticValues[2][0]+this.oneSecondIntervalMagneticValues[3][0]+this.oneSecondIntervalMagneticValues[4][0]+this.oneSecondIntervalMagneticValues[5][0]+this.oneSecondIntervalMagneticValues[6][0]+this.oneSecondIntervalMagneticValues[7][0]+this.oneSecondIntervalMagneticValues[8][0]+this.oneSecondIntervalMagneticValues[9][0];
                float lastMagneticValuesYSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][1]+this.oneSecondIntervalMagneticValues[2][1]+this.oneSecondIntervalMagneticValues[3][1]+this.oneSecondIntervalMagneticValues[4][1]+this.oneSecondIntervalMagneticValues[5][1]+this.oneSecondIntervalMagneticValues[6][1]+this.oneSecondIntervalMagneticValues[7][1]+this.oneSecondIntervalMagneticValues[8][1]+this.oneSecondIntervalMagneticValues[9][1];
                float lastMagneticValuesZSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][2]+this.oneSecondIntervalMagneticValues[2][2]+this.oneSecondIntervalMagneticValues[3][2]+this.oneSecondIntervalMagneticValues[4][2]+this.oneSecondIntervalMagneticValues[5][2]+this.oneSecondIntervalMagneticValues[6][2]+this.oneSecondIntervalMagneticValues[7][2]+this.oneSecondIntervalMagneticValues[8][2]+this.oneSecondIntervalMagneticValues[9][2];

                if ((Math.abs(lastMagneticValuesXSum/10) == 0.0F || Math.abs(lastMagneticValuesYSum/10) == 0.01F || Math.abs(lastMagneticValuesZSum/10) == 0.01F) && Math.abs(lastMessage - (long) param.args[3]) * NS2S >= 10 && lastMessage != 0) {
                    XposedBridge.log("VirtualSensor: Magnetic values are likely to be wrong, if this message seems to appear often, it is likely that there is a problem");
                    this.lastMessage = (long) param.args[3];
                }

                List<Object> list = changeSensorValues(s, this.accelerometerValues, this.magneticValues, listener, this.prevRotationMatrix, (long) param.args[3], this.prevTimestamp, this.prevValues, this.lastFilterValues, sensors);

                // Just making sure nothing goes wrong with the values returned by the rotation vector for example
                if (((float[])param.args[1]).length == 3) {
                    System.arraycopy((float[])list.get(0), 0, (float[])param.args[1], 0, ((float[])param.args[1]).length);
                }
                else {
                    System.arraycopy((float[])list.get(0), 0, (float[])param.args[1], 0, ((float[])list.get(0)).length);
                }

                this.prevTimestamp = (long)list.get(1);
                this.prevRotationMatrix = (float[])list.get(2);
                this.prevValues = (float[])list.get(3);
                this.lastFilterValues = (float[][])list.get(4);
            }
        }
    }


    public static class API23PlusNew extends XC_MethodReplacement {
        // Noise reduction
        float lastFilterValues[][] = new float[3][10];
        float prevValues[] = new float[3];

        //Sensor values
        float[] magneticValues = new float[3];
        float[] accelerometerValues = new float[3];

        //Keeping track of the previous rotation matrix and timestamp
        float[] prevRotationMatrix = new float[9];
        long prevTimestamp = 0;

        // Stores the magnetic values read each second for the last 10 seconds (approximately, it depends on the delay of the sensors currently used)
        float[][] oneSecondIntervalMagneticValues = new float[10][3];
        long lastMagneticValuesIntervalRead = 0;
        long lastMessage = 0;
        int magneticValuesIntervalCount = 0;

        @Override
        protected Object replaceHookedMethod(XC_MethodReplacement.MethodHookParam param) throws Throwable {
            SensorEventListener listener = (SensorEventListener)XposedHelpers.getObjectField(param.thisObject, "mListener");
            int handle = (int) param.args[0];
            Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
            SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
            Sensor s = sensors.get(handle);
            if (listener instanceof VirtualSensorListener) { // This is our custom listener type, we can start working on the values

                //All calculations need data from these two sensors, we can safely get their value every time
                if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                    if (Util.checkSensorResolution(this.accelerometerValues, (float[]) param.args[1], XposedMod.ACCELEROMETER_ACCURACY)) {
                        this.accelerometerValues = ((float[]) (param.args[1])).clone();
                    }
                }
                if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    if (Util.checkSensorResolution(this.magneticValues, (float[]) param.args[1], XposedMod.MAGNETIC_ACCURACY)) {
                        this.magneticValues = ((float[]) (param.args[1])).clone();
                        if (Math.abs(lastMagneticValuesIntervalRead - (long) param.args[3]) * NS2S >= 1) {
                            this.oneSecondIntervalMagneticValues[this.magneticValuesIntervalCount] = this.magneticValues;
                            this.lastMagneticValuesIntervalRead = (long) param.args[3];
                            this.magneticValuesIntervalCount++;
                            if (this.magneticValuesIntervalCount > 9)
                                this.magneticValuesIntervalCount = 0;
                        }
                    }
                }

                /*
                    Check that the readings from the magnetic field are not too wrong. If they have been 0 or close to 0 for the last 10 seconds, we can safely assume there's a problem
                 */
                float lastMagneticValuesXSum = this.oneSecondIntervalMagneticValues[0][0]+this.oneSecondIntervalMagneticValues[1][0]+this.oneSecondIntervalMagneticValues[2][0]+this.oneSecondIntervalMagneticValues[3][0]+this.oneSecondIntervalMagneticValues[4][0]+this.oneSecondIntervalMagneticValues[5][0]+this.oneSecondIntervalMagneticValues[6][0]+this.oneSecondIntervalMagneticValues[7][0]+this.oneSecondIntervalMagneticValues[8][0]+this.oneSecondIntervalMagneticValues[9][0];
                float lastMagneticValuesYSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][1]+this.oneSecondIntervalMagneticValues[2][1]+this.oneSecondIntervalMagneticValues[3][1]+this.oneSecondIntervalMagneticValues[4][1]+this.oneSecondIntervalMagneticValues[5][1]+this.oneSecondIntervalMagneticValues[6][1]+this.oneSecondIntervalMagneticValues[7][1]+this.oneSecondIntervalMagneticValues[8][1]+this.oneSecondIntervalMagneticValues[9][1];
                float lastMagneticValuesZSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][2]+this.oneSecondIntervalMagneticValues[2][2]+this.oneSecondIntervalMagneticValues[3][2]+this.oneSecondIntervalMagneticValues[4][2]+this.oneSecondIntervalMagneticValues[5][2]+this.oneSecondIntervalMagneticValues[6][2]+this.oneSecondIntervalMagneticValues[7][2]+this.oneSecondIntervalMagneticValues[8][2]+this.oneSecondIntervalMagneticValues[9][2];

                if ((Math.abs(lastMagneticValuesXSum/10) == 0.0F || Math.abs(lastMagneticValuesYSum/10) == 0.01F || Math.abs(lastMagneticValuesZSum/10) == 0.01F) && Math.abs(lastMessage - (long) param.args[3]) * NS2S >= 10 && lastMessage != 0) {
                    XposedBridge.log("VirtualSensor: Magnetic values are likely to be wrong, if this message seems to appear often, it is likely that there is a problem");
                    this.lastMessage = (long) param.args[3];
                }

                List<Object> list = changeSensorValues(s, this.accelerometerValues, this.magneticValues, listener, this.prevRotationMatrix, (long) param.args[3], this.prevTimestamp, this.prevValues, this.lastFilterValues, sensors);
                SensorEvent t = null;
                SparseArray<SensorEvent> mSensorsEvents = (SparseArray<SensorEvent>)XposedHelpers.getObjectField(param.thisObject, "mSensorsEvents");
                synchronized (mSensorsEvents) {
                    if ((System.currentTimeMillis() % 10000) < 5000) {
                        for (int i = 0; i < mSensorsEvents.size(); i++) {
                            XposedBridge.log("VirtualSensor: " + sensors.get(mSensorsEvents.keyAt(i)).getStringType());
                        }
                    }
                    t = mSensorsEvents.get(XposedMod.sensorTypetoHandle.get(((VirtualSensorListener) listener).getSensor().getType()));
                }
                if (t == null) {
                    return null;
                }

                System.arraycopy((float[])list.get(0), 0, t.values, 0, Math.min(t.values.length,((float[])list.get(0)).length));
                t.timestamp = (long)param.args[3];
                t.accuracy = (int)param.args[2];
                t.sensor = ((VirtualSensorListener) listener).getSensor();

                listener.onSensorChanged(t);

                /*System.out.print("VirtualSensor (" + (long)param.args[3] + " in SensorChangeHook " + s.getStringType() + "): ");
                System.out.print(((VirtualSensorListener) listener).getSensor().getStringType());
                for (int i = 0; i < ((float[])param.args[1]).length; i++) {
                    System.out.print(((float[])param.args[1])[i] + ":");
                }
                System.out.println();*/

                this.prevTimestamp = (long)list.get(1);
                this.prevRotationMatrix = (float[])list.get(2);
                this.prevValues = (float[])list.get(3);
                this.lastFilterValues = (float[][])list.get(4);
            } else { //TODO Better way to call the original function than copy it here ?
                SensorEvent t = null;
                SparseArray<SensorEvent> mSensorsEvents = (SparseArray<SensorEvent>)XposedHelpers.getObjectField(param.thisObject, "mSensorsEvents");
                synchronized (mSensorsEvents) {
                    t = mSensorsEvents.get(handle);
                }

                if (t == null) {
                    // This may happen if the client has unregistered and there are pending events in
                    // the queue waiting to be delivered. Ignore.
                    return null;
                }
                // Copy from the values array.
                System.arraycopy(param.args[1], 0, t.values, 0, t.values.length);
                t.timestamp = (long)param.args[3];
                t.accuracy = (int)param.args[2];
                t.sensor = s;

                SparseIntArray mSensorAccuracies = (SparseIntArray)XposedHelpers.getObjectField(param.thisObject, "mSensorAccuracies");
                // call onAccuracyChanged() only if the value changes
                final int accuracy = mSensorAccuracies.get(handle);
                if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                    mSensorAccuracies.put(handle, t.accuracy);
                    listener.onAccuracyChanged(t.sensor, t.accuracy);
                }
                listener.onSensorChanged(t);
            }

            return null;
        }
    }
    @SuppressWarnings("unchecked")
    public static class API23Plus extends XC_MethodHook {
        // Noise reduction
        float lastFilterValues[][] = new float[3][10];
        float prevValues[] = new float[3];

        //Sensor values
        float[] magneticValues = new float[3];
        float[] accelerometerValues = new float[3];

        //Keeping track of the previous rotation matrix and timestamp
        float[] prevRotationMatrix = new float[9];
        long prevTimestamp = 0;

        // Stores the magnetic values read each second for the last 10 seconds (approximately, it depends on the delay of the sensors currently used)
        float[][] oneSecondIntervalMagneticValues = new float[10][3];
        long lastMagneticValuesIntervalRead = 0;
        long lastMessage = 0;
        int magneticValuesIntervalCount = 0;

        private XC_LoadPackage.LoadPackageParam lpparam;

        public API23Plus(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        @Override
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            super.beforeHookedMethod(param);

            Object listener = XposedHelpers.getObjectField(param.thisObject, "mListener");
            if (listener instanceof VirtualSensorListener) { // This is our custom listener type, we can start working on the values
                int handle = (int) param.args[0];
                Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
                SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
                Sensor s = sensors.get(handle);

                //All calculations need data from these two sensors, we can safely get their value every time
                if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                    if (Util.checkSensorResolution(this.accelerometerValues, (float[]) param.args[1], XposedMod.ACCELEROMETER_ACCURACY)) {
                        this.accelerometerValues = ((float[]) (param.args[1])).clone();
                    }
                }
                if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    if (Util.checkSensorResolution(this.magneticValues, (float[]) param.args[1], XposedMod.MAGNETIC_ACCURACY)) {
                        this.magneticValues = ((float[]) (param.args[1])).clone();
                        if (Math.abs(lastMagneticValuesIntervalRead - (long) param.args[3]) * NS2S >= 1) {
                            this.oneSecondIntervalMagneticValues[this.magneticValuesIntervalCount] = this.magneticValues;
                            this.lastMagneticValuesIntervalRead = (long) param.args[3];
                            this.magneticValuesIntervalCount++;
                            if (this.magneticValuesIntervalCount > 9)
                                this.magneticValuesIntervalCount = 0;
                        }
                    }
                }

                /*
                    Check that the readings from the magnetic field are not too wrong. If they have been 0 or close to 0 for the last 10 seconds, we can safely assume there's a problem
                 */
                float lastMagneticValuesXSum = this.oneSecondIntervalMagneticValues[0][0]+this.oneSecondIntervalMagneticValues[1][0]+this.oneSecondIntervalMagneticValues[2][0]+this.oneSecondIntervalMagneticValues[3][0]+this.oneSecondIntervalMagneticValues[4][0]+this.oneSecondIntervalMagneticValues[5][0]+this.oneSecondIntervalMagneticValues[6][0]+this.oneSecondIntervalMagneticValues[7][0]+this.oneSecondIntervalMagneticValues[8][0]+this.oneSecondIntervalMagneticValues[9][0];
                float lastMagneticValuesYSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][1]+this.oneSecondIntervalMagneticValues[2][1]+this.oneSecondIntervalMagneticValues[3][1]+this.oneSecondIntervalMagneticValues[4][1]+this.oneSecondIntervalMagneticValues[5][1]+this.oneSecondIntervalMagneticValues[6][1]+this.oneSecondIntervalMagneticValues[7][1]+this.oneSecondIntervalMagneticValues[8][1]+this.oneSecondIntervalMagneticValues[9][1];
                float lastMagneticValuesZSum = this.oneSecondIntervalMagneticValues[0][1]+this.oneSecondIntervalMagneticValues[1][2]+this.oneSecondIntervalMagneticValues[2][2]+this.oneSecondIntervalMagneticValues[3][2]+this.oneSecondIntervalMagneticValues[4][2]+this.oneSecondIntervalMagneticValues[5][2]+this.oneSecondIntervalMagneticValues[6][2]+this.oneSecondIntervalMagneticValues[7][2]+this.oneSecondIntervalMagneticValues[8][2]+this.oneSecondIntervalMagneticValues[9][2];

                if ((Math.abs(lastMagneticValuesXSum/10) == 0.0F || Math.abs(lastMagneticValuesYSum/10) == 0.01F || Math.abs(lastMagneticValuesZSum/10) == 0.01F) && Math.abs(lastMessage - (long) param.args[3]) * NS2S >= 10 && lastMessage != 0) {
                    XposedBridge.log("VirtualSensor: Magnetic values are likely to be wrong, if this message seems to appear often, it is likely that there is a problem");
                    this.lastMessage = (long) param.args[3];
                }

                List<Object> list = changeSensorValues(s, this.accelerometerValues, this.magneticValues, listener, this.prevRotationMatrix, (long) param.args[3], this.prevTimestamp, this.prevValues, this.lastFilterValues, sensors);

                param.args[1] = new float[((float[])list.get(0)).length];
                // Just making sure nothing goes wrong with the values returned by the rotation vector for example
                System.arraycopy((float[])list.get(0), 0, (float[])param.args[1], 0, ((float[])param.args[1]).length);

                /*System.out.print("VirtualSensor (" + (long)param.args[3] + " in SensorChangeHook " + s.getStringType() + "): ");
                System.out.print(((VirtualSensorListener) listener).getSensor().getStringType());
                for (int i = 0; i < ((float[])param.args[1]).length; i++) {
                    System.out.print(((float[])param.args[1])[i] + ":");
                }
                System.out.println();*/

                this.prevTimestamp = (long)list.get(1);
                this.prevRotationMatrix = (float[])list.get(2);
                this.prevValues = (float[])list.get(3);
                this.lastFilterValues = (float[][])list.get(4);
            }
        }
    }

    /*
        Helper functions
     */

    private static List<Object> getGyroscopeValues(float[] currentAccelerometer, float[] currentMagnetic, float[] prevRotationMatrix, float timeDifference) {
        float[] angularRates = new float[] {0.0F, 0.0F, 0.0F};

        float[] rotationMatrix = new float[9];
        float[] gravity = new float[]{0F, 0F, 9.81F};
        SensorManager.getRotationMatrix(rotationMatrix, null, currentAccelerometer, currentMagnetic);

        float[] gravityRot = new float[3];
        gravityRot[0] = gravity[0] * rotationMatrix[0] + gravity[1] * rotationMatrix[3] + gravity[2] * rotationMatrix[6];
        gravityRot[1] = gravity[0] * rotationMatrix[1] + gravity[1] * rotationMatrix[4] + gravity[2] * rotationMatrix[7];
        gravityRot[2] = gravity[0] * rotationMatrix[2] + gravity[1] * rotationMatrix[5] + gravity[2] * rotationMatrix[8];

        SensorManager.getRotationMatrix(rotationMatrix, null, gravityRot, currentMagnetic);

        float[] angleChange = new float[3];
        SensorManager.getAngleChange(angleChange, rotationMatrix, prevRotationMatrix);
        angularRates[0] = -(angleChange[1]) / timeDifference;
        angularRates[1] = (angleChange[2]) / timeDifference;
        angularRates[2] = (angleChange[0]) / timeDifference;

        List<Object> returnList = new ArrayList<>();
        returnList.add(angularRates);
        returnList.add(rotationMatrix);
        return returnList;
    }

    private static List<Object> filterValues(float[] values, float[][] lastFilterValues, float[] prevValues) {
        if (Float.isInfinite(values[0]) || Float.isNaN(values[0])) values[0] = prevValues[0];
        if (Float.isInfinite(values[1]) || Float.isNaN(values[1])) values[1] = prevValues[1];
        if (Float.isInfinite(values[2]) || Float.isNaN(values[2])) values[2] = prevValues[2];

        float[][] newLastFilterValues = new float[3][10];
        for (int i = 0; i < 3; i++) {
            // Apply lowpass on the value
            float alpha = 0.5F;
            float newValue = lowPass(alpha, values[i], prevValues[i]);
            //float newValue = values[i];

            for (int j = 0; j < 10; j++) {
                if (j == 0) continue;
                newLastFilterValues[i][j-1] = lastFilterValues[i][j];
            }
            newLastFilterValues[i][9] = newValue;

            float sum = 0F;
            for (int j = 0; j < 10; j++) {
                sum += lastFilterValues[i][j];
            }
            newValue = sum/10;

            //The gyroscope is moving even after lowpass
            if (newValue != 0.0F) {
                //We are under the declared resolution of the gyroscope, so the value should be 0
                if (Math.abs(newValue) < 0.01F) newValue = 0.0F;
            }

            prevValues[i] = values[i];
            values[i] = newValue;
        }

        List<Object> returnValue = new ArrayList<>();
        returnValue.add(values);
        returnValue.add(prevValues);
        returnValue.add(newLastFilterValues);
        return returnValue;
    }

    private static float lowPass(float alpha, float value, float prev) {
        return prev + alpha * (value - prev);
    }
}

