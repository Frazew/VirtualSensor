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

    private class RotationVectorEventListener implements SensorEventListener {
        private SensorEventListener realListener = null;
        public Sensor rotationVectorRef = null;

        public RotationVectorEventListener(SensorEventListener realListener) {
            this.realListener = realListener;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (rotationVectorRef != null) {
                event.sensor = rotationVectorRef;
                realListener.onSensorChanged(event);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    private class GravityEventListener implements SensorEventListener {
        private SensorEventListener realListener = null;
        public Sensor gravityRef = null;

        public GravityEventListener(SensorEventListener realListener) {
            this.realListener = realListener;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (gravityRef != null) {
                event.sensor = gravityRef;
                realListener.onSensorChanged(event);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    private class LinearAccEventListener implements SensorEventListener {
        private SensorEventListener realListener = null;
        public Sensor linearAccRef = null;

        public LinearAccEventListener(SensorEventListener realListener) {
            this.realListener = realListener;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (linearAccRef != null) {
                event.sensor = linearAccRef;
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
        /*if(lpparam.packageName.contains("nianticlabs.pokemongo")) {
            Class<?> sensorMgrNiantic = XposedHelpers.findClass("com.nianticlabs.nia.sensors.NianticSensorManager", lpparam.classLoader);
            XposedBridge.hookAllMethods(sensorMgrNiantic, "updateOrientationFromRotation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("VirtualSensor: updateOrientationFromRotation");
                    param.setResult(false);
                }
            });
        }*/
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

                if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                    this.accelerometerValues = ((float[]) (param.args[1])).clone();
                }
                if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    this.magneticValues = ((float[]) (param.args[1])).clone();
                }

                Object listener = XposedHelpers.getObjectField(param.thisObject, "mListener");
                if (listener instanceof GyroscopeEventListener) {
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
                        } else {
                            ((GyroscopeEventListener) listener).gyroscopeRef = null;
                        }
                    } else {
                        ((GyroscopeEventListener) listener).gyroscopeRef = null;
                    }
                } else if (listener instanceof RotationVectorEventListener) {
                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                        float[] values = new float[5];
                        float[] rotationMatrix = new float[9];
                        SensorManager.getRotationMatrix(rotationMatrix, null, this.accelerometerValues, this.magneticValues);
                        float[] quaternion = rotationMatrixToQuaternion(rotationMatrix);

                        float angle = 2 * (float) Math.asin(quaternion[0]);
                        values[0] = quaternion[1];
                        values[1] = quaternion[2];
                        values[2] = quaternion[3];
                        values[3] = quaternion[0];
                        values[4] = -1;

                        System.arraycopy(values, 0, param.args[1], 0, values.length);
                        ((RotationVectorEventListener) listener).rotationVectorRef = sensors.get(Sensor.TYPE_ROTATION_VECTOR);
                    } else {
                        ((RotationVectorEventListener) listener).rotationVectorRef = null;
                    }
                } else if (listener instanceof GravityEventListener) {
                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                        float[] values = new float[3];
                        float[] rotationMatrix = new float[9];
                        SensorManager.getRotationMatrix(rotationMatrix, null, this.accelerometerValues, this.magneticValues);
                        float[] quaternion = rotationMatrixToQuaternion(rotationMatrix);

                        float angle = 2 * (float) Math.asin(quaternion[0]);
                        float sin = (float) Math.sin(angle / 2);
                        values[0] = (quaternion[1]) * sin - this.accelerometerValues[0];
                        values[1] = (quaternion[2]) * sin - this.accelerometerValues[1];
                        values[2] = (quaternion[3]) * sin - this.accelerometerValues[2];

                        System.arraycopy(values, 0, param.args[1], 0, values.length);
                        ((GravityEventListener) listener).gravityRef = sensors.get(Sensor.TYPE_GRAVITY);
                    } else {
                        ((GravityEventListener) listener).gravityRef = null;
                    }
                } else if (listener instanceof LinearAccEventListener) {
                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                        float[] values = new float[3];
                        float[] rotationMatrix = new float[9];
                        SensorManager.getRotationMatrix(rotationMatrix, null, this.accelerometerValues, this.magneticValues);
                        float[] quaternion = rotationMatrixToQuaternion(rotationMatrix);

                        float angle = 2 * (float) Math.asin(quaternion[0]);
                        float sin = (float) Math.sin(angle / 2);
                        values[0] = (quaternion[1] * this.accelerometerValues[0]) * sin;
                        values[1] = (quaternion[2] * this.accelerometerValues[1]) * sin;
                        values[2] = (quaternion[3] * this.accelerometerValues[2]) * sin;

                        System.arraycopy(values, 0, param.args[1], 0, values.length);
                        ((LinearAccEventListener) listener).linearAccRef = sensors.get(Sensor.TYPE_LINEAR_ACCELERATION);
                    } else {
                        ((LinearAccEventListener) listener).linearAccRef = null;
                    }
                }
            }
        });
    }

    private float[] rotationMatrixToQuaternion(float[] rotationMatrix) {
        float m00 = rotationMatrix[0];
        float m01 = rotationMatrix[1];
        float m02 = rotationMatrix[2];
        float m10 = rotationMatrix[3];
        float m11 = rotationMatrix[4];
        float m12 = rotationMatrix[5];
        float m20 = rotationMatrix[6];
        float m21 = rotationMatrix[7];
        float m22 = rotationMatrix[8];

        float tr = m00 + m11 + m22;

        float qw;
        float qx;
        float qy;
        float qz;
        if (tr > 0) {
            float S = (float)Math.sqrt(tr+1.0) * 2; // S=4*qw
            qw = 0.25F * S;
            qx = (m21 - m12) / S;
            qy = (m02 - m20) / S;
            qz = (m10 - m01) / S;
        } else if ((m00 > m11)&(m00 > m22)) {
            float S = (float)Math.sqrt(1.0 + m00 - m11 - m22) * 2; // S=4*qx
            qw = (m21 - m12) / S;
            qx = 0.25F * S;
            qy = (m01 + m10) / S;
            qz = (m02 + m20) / S;
        } else if (m11 > m22) {
            float S = (float)Math.sqrt(1.0 + m11 - m00 - m22) * 2; // S=4*qy
            qw = (m02 - m20) / S;
            qx = (m01 + m10) / S;
            qy = 0.25F * S;
            qz = (m12 + m21) / S;
        } else {
            float S = (float)Math.sqrt(1.0 + m22 - m00 - m11) * 2; // S=4*qz
            qw = (m10 - m01) / S;
            qx = (m02 + m20) / S;
            qy = (m12 + m21) / S;
            qz = 0.25F * S;
        }
        return new float[] {qw, qx, qy, qz};
    }

    private List<Object> getGyroscopeValues(float[] currentAccelerometer, float[] currentMagnetic, float[] prevRotationMatrix, float timeDifference) {
        float[] angularRates = new float[] {0.0F, 0.0F, 0.0F};

        float[] currentRotationMatrix = new float[9];
        SensorManager.getRotationMatrix(currentRotationMatrix, null, currentAccelerometer, currentMagnetic);

        SensorManager.getAngleChange(angularRates, currentRotationMatrix, prevRotationMatrix);
        angularRates[0] = -(angularRates[1]*2) / timeDifference;
        angularRates[1] = (angularRates[2]) / timeDifference;
        angularRates[2] = ((angularRates[0]/2) / timeDifference)*0.0F; //Right now this returns weird values, need to look into it @TODO

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
                boolean hasRotationVector = false;
                boolean hasGravity = false;
                boolean hasLinearAcc = false;
                while (iterator.hasNext()) {
                    Sensor sensor = iterator.next();
                    if (sensor.getType() == Sensor.TYPE_GYROSCOPE) hasGyroscope = true;
                    else if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) hasRotationVector = true;
                    else if (sensor.getType() == Sensor.TYPE_GRAVITY) hasGravity = true;
                    else if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) hasLinearAcc = true;
                    else if (sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                        minDelayAccelerometer = sensor.getMinDelay();
                }

                XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).setAccessible(true);
                XposedHelpers.findMethodBestMatch(android.hardware.Sensor.class, "setType", Integer.class).setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mName").setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mVendor").setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mVersion").setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mHandle").setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mResolution").setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mMinDelay").setAccessible(true);
                XposedHelpers.findField(android.hardware.Sensor.class, "mMaxRange").setAccessible(true);
                XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "mFullSensorsList").setAccessible(true);
                XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "mHandleToSensor").setAccessible(true);

                SparseArray<Sensor> mHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getObjectField(param.thisObject, "mHandleToSensor");

                if (!hasGyroscope) {
                    Sensor s = (Sensor) XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).newInstance();
                    XposedHelpers.callMethod(s, "setType", Sensor.TYPE_GYROSCOPE);
                    XposedHelpers.setObjectField(s, "mName", "VirtualSensor Gyroscope");
                    XposedHelpers.setObjectField(s, "mVendor", "Frazew");
                    XposedHelpers.setObjectField(s, "mVersion", BuildConfig.VERSION_CODE);
                    XposedHelpers.setObjectField(s, "mHandle", Sensor.TYPE_GYROSCOPE);
                    XposedHelpers.setObjectField(s, "mMinDelay", minDelayAccelerometer);
                    XposedHelpers.setObjectField(s, "mResolution", 0.01F);
                    XposedHelpers.setObjectField(s, "mMaxRange", (float) Math.PI);
                    mFullSensorList.add(s);
                    mHandleToSensor.append(Sensor.TYPE_GYROSCOPE, s);
                }

                if (!hasRotationVector) {
                    Sensor s2 = (Sensor) XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).newInstance();
                    XposedHelpers.callMethod(s2, "setType", Sensor.TYPE_ROTATION_VECTOR);
                    XposedHelpers.setObjectField(s2, "mName", "VirtualSensor RotationVector");
                    XposedHelpers.setObjectField(s2, "mVendor", "Frazew");
                    XposedHelpers.setObjectField(s2, "mVersion", BuildConfig.VERSION_CODE);
                    XposedHelpers.setObjectField(s2, "mHandle", Sensor.TYPE_ROTATION_VECTOR);
                    XposedHelpers.setObjectField(s2, "mMinDelay", minDelayAccelerometer);
                    XposedHelpers.setObjectField(s2, "mResolution", 0.01F);
                    mFullSensorList.add(s2);
                    mHandleToSensor.append(Sensor.TYPE_ROTATION_VECTOR, s2);
                }

                if (!hasGravity) {
                    Sensor s3 = (Sensor) XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).newInstance();
                    XposedHelpers.callMethod(s3, "setType", Sensor.TYPE_GRAVITY);
                    XposedHelpers.setObjectField(s3, "mName", "VirtualSensor Gravity");
                    XposedHelpers.setObjectField(s3, "mVendor", "Frazew");
                    XposedHelpers.setObjectField(s3, "mVersion", BuildConfig.VERSION_CODE);
                    XposedHelpers.setObjectField(s3, "mHandle", Sensor.TYPE_GRAVITY);
                    XposedHelpers.setObjectField(s3, "mMinDelay", minDelayAccelerometer);
                    XposedHelpers.setObjectField(s3, "mResolution", 0.01F);
                    mFullSensorList.add(s3);
                    mHandleToSensor.append(Sensor.TYPE_GRAVITY, s3);
                }

                if (!hasLinearAcc) {
                    Sensor s4 = (Sensor) XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).newInstance();
                    XposedHelpers.callMethod(s4, "setType", Sensor.TYPE_LINEAR_ACCELERATION);
                    XposedHelpers.setObjectField(s4, "mName", "VirtualSensor LinearAcceleration");
                    XposedHelpers.setObjectField(s4, "mVendor", "Frazew");
                    XposedHelpers.setObjectField(s4, "mVersion", BuildConfig.VERSION_CODE);
                    XposedHelpers.setObjectField(s4, "mHandle", 4242);
                    XposedHelpers.setObjectField(s4, "mMinDelay", minDelayAccelerometer);
                    XposedHelpers.setObjectField(s4, "mResolution", 0.01F);
                    mFullSensorList.add(s4);
                    mHandleToSensor.append(4242, s4); // Had to use another handle value than Sensor.TYPE_LINEAR_ACCELERATION because it broke the magnetic sensor's hook for some reason. @TODO
                }

                XposedHelpers.setObjectField(param.thisObject, "mHandleToSensor", mHandleToSensor);
                XposedHelpers.setObjectField(param.thisObject, "mFullSensorsList", mFullSensorList);
                param.setResult(mFullSensorList);
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "registerListenerImpl", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, android.os.Handler.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] == null) return;
                SensorEventListener listener = (SensorEventListener) param.args[0];
                SensorEventListener specialListener = null;

                if (((Sensor) param.args[1]).getType() == Sensor.TYPE_GYROSCOPE) specialListener = new GyroscopeEventListener(listener);
                else if (((Sensor) param.args[1]).getType() == Sensor.TYPE_ROTATION_VECTOR) specialListener = new RotationVectorEventListener(listener);
                else if (((Sensor) param.args[1]).getType() == Sensor.TYPE_GRAVITY) specialListener = new GravityEventListener(listener);
                else if (((Sensor) param.args[1]).getType() == Sensor.TYPE_LINEAR_ACCELERATION) specialListener = new LinearAccEventListener(listener);

                if (specialListener != null) {
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
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                for (Map.Entry<Object, Object> entry : ((HashMap<Object, Object>) XposedHelpers.getObjectField(param.thisObject, "mSensorListeners")).entrySet()) {
                    SensorEventListener listener = (SensorEventListener) entry.getKey();
                    if (listener instanceof GyroscopeEventListener) {
                        GyroscopeEventListener specialListener = (GyroscopeEventListener) listener;
                        if (specialListener.realListener == (android.hardware.SensorEventListener) param.args[0]) {
                            XposedHelpers.callMethod(param.thisObject, "unregisterListenerImpl", listener, (Sensor) null);
                        }
                    }

                    if (listener instanceof RotationVectorEventListener) {
                        RotationVectorEventListener specialListener = (RotationVectorEventListener) listener;
                        if (specialListener.realListener == (android.hardware.SensorEventListener) param.args[0]) {
                            XposedHelpers.callMethod(param.thisObject, "unregisterListenerImpl", listener, (Sensor) null);
                        }
                    }

                    if (listener instanceof GravityEventListener) {
                        GravityEventListener specialListener = (GravityEventListener) listener;
                        if (specialListener.realListener == (android.hardware.SensorEventListener) param.args[0]) {
                            XposedHelpers.callMethod(param.thisObject, "unregisterListenerImpl", listener, (Sensor) null);
                        }
                    }

                    if (listener instanceof LinearAccEventListener) {
                        LinearAccEventListener specialListener = (LinearAccEventListener) listener;
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