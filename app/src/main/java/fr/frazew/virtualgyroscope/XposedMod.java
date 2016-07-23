package fr.frazew.virtualgyroscope;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.ArrayMap;
import android.util.SparseArray;

import de.robv.android.xposed.XposedBridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {

    private static final float NS2S = 1.0f / 1000000000.0f;

    private class GyroscopeEventListener implements SensorEventListener {
        private SensorEventListener realListener = null;
        public Sensor gyroscopeRef = null;

        public GyroscopeEventListener(SensorEventListener realListener) {
            this.realListener = realListener;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (gyroscopeRef != null) {
                event.sensor = gyroscopeRef;
                realListener.onSensorChanged(event);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals("android")) {
            hookPackageFeatures(lpparam);
        }
        hookSensorValues(lpparam);
        addSensors(lpparam);
    }

    @SuppressWarnings("unchecked")
    private void hookPackageFeatures(final LoadPackageParam lpparam) {
        Class<?> pkgMgrSrv = XposedHelpers.findClass("com.android.server.SystemConfig", lpparam.classLoader);
        XposedBridge.hookAllMethods(pkgMgrSrv, "getAvailableFeatures", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ArrayMap<String, FeatureInfo> mAvailableFeatures = (ArrayMap<String, FeatureInfo>) param.getResult();
                if (!mAvailableFeatures.containsKey(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                    FeatureInfo gyro = new FeatureInfo();
                    gyro.name = PackageManager.FEATURE_SENSOR_GYROSCOPE;
                    gyro.reqGlEsVersion = (int) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "getInt", "ro.opengles.version", FeatureInfo.GL_ES_VERSION_UNDEFINED);
                    mAvailableFeatures.put(PackageManager.FEATURE_SENSOR_GYROSCOPE, gyro);
                    XposedHelpers.setObjectField(param.thisObject, "mAvailableFeatures", mAvailableFeatures);
                    param.setResult(mAvailableFeatures);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void hookSensorValues(final LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue", lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class, new XC_MethodHook() {
            // Noise reduction
            float lastFilterValues[][] = new float[3][10];
            float prevValues[] = new float[3];

            //Sensor values
            float[] magneticValues = new float[3];
            float[] accelerometerValues = new float[3];

            //Keeping track of the previous rotation matrix and timestamp
            float[] prevRotationMatrix = new float[9];
            long prevTimestamp = 0;


            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                int handle = (int) param.args[0];
                Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
                SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
                Sensor s = sensors.get(handle);

                Object listener = XposedHelpers.getObjectField(param.thisObject, "mListener");
                if (listener instanceof GyroscopeEventListener) {
                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                        this.accelerometerValues = ((float[]) (param.args[1])).clone();
                    }
                    if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        this.magneticValues = ((float[]) (param.args[1])).clone();
                    }

                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                        float timeDifference = Math.abs((float) ((long) param.args[3] - this.prevTimestamp) * NS2S);
                        List<Object> valuesList = getGyroscopeValues(this.accelerometerValues, this.magneticValues, this.prevRotationMatrix, timeDifference);
                        if (timeDifference != 0.0F) {
                            this.prevTimestamp = (long) param.args[3];
                            this.prevRotationMatrix = (float[]) valuesList.get(1);
                            float[] values = (float[]) valuesList.get(0);

                            if (Float.isNaN(values[0]) || Float.isInfinite(values[0]))
                                XposedBridge.log("VirtualSensor: Value #" + 0 + " is NaN or Infinity, this should not happen");

                            if (Float.isNaN(values[1]) || Float.isInfinite(values[1]))
                                XposedBridge.log("VirtualSensor: Value #" + 1 + " is NaN or Infinity, this should not happen");

                            if (Float.isNaN(values[2]) || Float.isInfinite(values[2]))
                                XposedBridge.log("VirtualSensor: Value #" + 2 + " is NaN or Infinity, this should not happen");


                            List<Object> filter = filterValues(values, lastFilterValues, prevValues);
                            values = (float[]) filter.get(0);
                            this.prevValues = (float[]) filter.get(1);
                            this.lastFilterValues = (float[][]) filter.get(2);

                            System.arraycopy(values, 0, param.args[1], 0, values.length);
                            ((GyroscopeEventListener) listener).gyroscopeRef = sensors.get(Sensor.TYPE_GYROSCOPE);
                            //XposedBridge.log("VirtualSensor: currentGyro=" + ((float[])param.args[1])[0] + ":" + ((float[])param.args[1])[1] + ":" + ((float[])param.args[1])[2]);
                        } else {
                            ((GyroscopeEventListener) listener).gyroscopeRef = null;
                        }
                    } else {
                        ((GyroscopeEventListener) listener).gyroscopeRef = null;
                    }
                }
            }
        });
    }

    private List<Object> getGyroscopeValues(float[] currentAccelerometer, float[] currentMagnetic, float[] prevRotationMatrix, float timeDifference) {
        float[] angularRates = new float[] {0.0F, 0.0F, 0.0F};

        float[] currentRotationMatrix = new float[9];
        SensorManager.getRotationMatrix(currentRotationMatrix, null, currentAccelerometer, currentMagnetic);

        SensorManager.getAngleChange(angularRates, currentRotationMatrix, prevRotationMatrix);
        angularRates[0] = -(angularRates[1]*2) / timeDifference;
        angularRates[1] = (angularRates[2]) / timeDifference;
        angularRates[2] = ((angularRates[0]/2) / timeDifference) * 0.0F; //Right now this returns weird values, need to look into it @TODO

        List<Object> returnList = new ArrayList<>();
        returnList.add(angularRates);
        returnList.add(currentRotationMatrix);
        return returnList;
    }

    @SuppressWarnings("unchecked")
    private void addSensors(final LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "getFullSensorList", new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ArrayList<Sensor> mFullSensorList = (ArrayList<Sensor>) param.getResult();
                Iterator<Sensor> iterator = mFullSensorList.iterator();

                int minDelayAccelerometer = 0;

                boolean hasGyroscope = false;
                while (iterator.hasNext()) {
                    Sensor sensor = iterator.next();
                    if (sensor.getType() == Sensor.TYPE_GYROSCOPE) hasGyroscope = true;
                    else if (sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                        minDelayAccelerometer = sensor.getMinDelay();
                }

                if (!hasGyroscope) {
                    XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).setAccessible(true);
                    Sensor s = (Sensor) XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).newInstance();

                    XposedHelpers.findMethodBestMatch(s.getClass(), "setType", Integer.class).setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mName").setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mVendor").setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mVersion").setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mHandle").setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mResolution").setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mMinDelay").setAccessible(true);
                    XposedHelpers.findField(s.getClass(), "mMaxRange").setAccessible(true);
                    XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "mFullSensorsList").setAccessible(true);
                    XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "mHandleToSensor").setAccessible(true);


                    XposedHelpers.callMethod(s, "setType", Sensor.TYPE_GYROSCOPE);
                    XposedHelpers.setObjectField(s, "mName", "VirtualSensor Gyroscope");
                    XposedHelpers.setObjectField(s, "mVendor", "Frazew");
                    XposedHelpers.setObjectField(s, "mVersion", BuildConfig.VERSION_CODE);
                    XposedHelpers.setObjectField(s, "mHandle", Sensor.TYPE_GYROSCOPE);
                    XposedHelpers.setObjectField(s, "mMinDelay", minDelayAccelerometer);
                    XposedHelpers.setObjectField(s, "mResolution", 0.01F);
                    XposedHelpers.setObjectField(s, "mMaxRange", (float) Math.PI);

                    mFullSensorList.add(s);
                    XposedHelpers.setObjectField(param.thisObject, "mFullSensorsList", mFullSensorList);
                    param.setResult(mFullSensorList);

                    SparseArray<Sensor> mHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getObjectField(param.thisObject, "mHandleToSensor");
                    mHandleToSensor.append(Sensor.TYPE_GYROSCOPE, s);

                    XposedHelpers.setObjectField(param.thisObject, "mHandleToSensor", mHandleToSensor);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "registerListenerImpl", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, android.os.Handler.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] == null) return;
                if (((Sensor) param.args[1]).getType() == Sensor.TYPE_GYROSCOPE) {
                    final SensorEventListener listener = (SensorEventListener) param.args[0];

                    SensorEventListener specialListener = new GyroscopeEventListener(listener);
                    XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                            specialListener,
                            XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_ACCELEROMETER),
                            XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                            (android.os.Handler) param.args[3],
                            0,
                            0
                    );
                    XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                            specialListener,
                            XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_MAGNETIC_FIELD),
                            XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                            (android.os.Handler) param.args[3],
                            0,
                            0
                    );
                    param.args[0] = specialListener;
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "unregisterListenerImpl", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (Map.Entry<Object, Object> entry : ((HashMap<Object, Object>) XposedHelpers.getObjectField(param.thisObject, "mSensorListeners")).entrySet()) {
                    SensorEventListener listener = (SensorEventListener) entry.getKey();
                    if (listener instanceof GyroscopeEventListener) {
                        GyroscopeEventListener specialListener = (GyroscopeEventListener) listener;
                        if (specialListener.realListener == (android.hardware.SensorEventListener) param.args[0]) {
                            XposedHelpers.callMethod(param.thisObject, "unregisterListenerImpl", listener, (Sensor) null);
                        }
                    }
                }
            }
        });
    }

    private static List<Object> filterValues(float[] values, float[][] lastFilterValues, float[] prevValues) {
        if (Float.isInfinite(values[0]) || Float.isNaN(values[0])) values[0] = prevValues[0];
        if (Float.isInfinite(values[1]) || Float.isNaN(values[1])) values[1] = prevValues[1];
        if (Float.isInfinite(values[2]) || Float.isNaN(values[2])) values[2] = prevValues[2];

        float[][] newLastFilterValues = new float[3][10];
        for (int i = 0; i < 3; i++) {
            // Apply lowpass on the value
            float alpha = 0.1F;
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