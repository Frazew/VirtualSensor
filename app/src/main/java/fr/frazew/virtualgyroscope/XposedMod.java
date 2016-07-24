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
    private static final SparseArray<SensorModel> sensorsToEmulate = new SparseArray<SensorModel>() {{
        put(Sensor.TYPE_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_ROTATION_VECTOR, "VirtualSensor RotationVector", -1, 0.01F, -1, -1));
        put(Sensor.TYPE_GYROSCOPE, new SensorModel(Sensor.TYPE_GYROSCOPE, "VirtualSensor Gyroscope", -1, 0.01F, -1, (float)Math.PI));
        put(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "VirtualSensor GeomagneticRotationVector", -1, 0.01F, -1, -1));
        put(Sensor.TYPE_GRAVITY, new SensorModel(Sensor.TYPE_GRAVITY, "VirtualSensor Gravity", -1, 0.01F, -1, -1));
        put(Sensor.TYPE_LINEAR_ACCELERATION, new SensorModel(Sensor.TYPE_LINEAR_ACCELERATION, "VirtualSensor LinearAcceleration", 4242, 0.01F, -1, -1));
    }};

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals("android")) {
            hookPackageFeatures(lpparam);
        }
        hookSensorValues(lpparam);
        addSensors(lpparam);

        // Simple Pokémon GO hook, trying to understand why it doesn't understand the values from the virtual sensors.
        if(lpparam.packageName.contains("nianticlabs.pokemongo")) {
            Class<?> sensorMgrNiantic = XposedHelpers.findClass("com.nianticlabs.nia.sensors.NianticSensorManager", lpparam.classLoader);
            XposedBridge.hookAllMethods(sensorMgrNiantic, "onSensorChanged", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    //XposedBridge.log("VirtualSensor: Pokémon GO onSensorChanged with sensor type " + (android.os.Build.VERSION.SDK_INT >= 20 ? ((SensorEvent) (param.args[0])).sensor.getStringType() : ((SensorEvent) (param.args[0])).sensor.getType()));
                    if (((SensorEvent) (param.args[0])).sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        float[] values = ((SensorEvent) (param.args[0])).values;
                        //XposedBridge.log("VirtualSensor: Pokémon GO gyroscope values are x=" + values[0] + ",y=" + values[1] + ",z=" + values[2]);
                    }
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void hookPackageFeatures(final LoadPackageParam lpparam) {
        Class<?> pkgMgrSrv = XposedHelpers.findClass("com.android.server.SystemConfig", lpparam.classLoader);
        XposedBridge.hookAllMethods(pkgMgrSrv, "getAvailableFeatures", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ArrayMap<String, FeatureInfo> mAvailableFeatures = (ArrayMap<String, FeatureInfo>) param.getResult();
                int openGLEsVersion = (int) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "getInt", "ro.opengles.version", FeatureInfo.GL_ES_VERSION_UNDEFINED);
                if (!mAvailableFeatures.containsKey(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                    FeatureInfo gyro = new FeatureInfo();
                    gyro.name = PackageManager.FEATURE_SENSOR_GYROSCOPE;
                    gyro.reqGlEsVersion = openGLEsVersion;
                    mAvailableFeatures.put(PackageManager.FEATURE_SENSOR_GYROSCOPE, gyro);
                }
                XposedHelpers.setObjectField(param.thisObject, "mAvailableFeatures", mAvailableFeatures);
                param.setResult(mAvailableFeatures);
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

                Object listener = XposedHelpers.getObjectField(param.thisObject, "mListener");
                if (listener instanceof VirtualSensorListener) { // This is our custom listener type, we can start working on the values
                    int handle = (int) param.args[0];
                    Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
                    SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
                    Sensor s = sensors.get(handle);

                    //All calculations need data from these two sensors, we can safely get their value every time
                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {
                        this.accelerometerValues = ((float[]) (param.args[1])).clone();
                    }
                    if (s.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        this.magneticValues = ((float[]) (param.args[1])).clone();
                    }

                    VirtualSensorListener virtualListener = (VirtualSensorListener) listener;

                    // Per default, we set the sensor to null so that it doesn't accidentally send the accelerometer's values
                    virtualListener.sensorRef = null;

                    // We only work when it's an accelerometer's reading. If we were to work on both events, the timeDifference for the gyro would often be 0 resulting in NaN or Infinity
                    if (s.getType() == Sensor.TYPE_ACCELEROMETER) {

                        if (virtualListener.getSensor().getType() == Sensor.TYPE_GYROSCOPE) {
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

                                System.arraycopy(values, 0, (float[]) param.args[1], 0, values.length);
                                virtualListener.sensorRef = sensors.get(Sensor.TYPE_GYROSCOPE);
                            }
                        } else if (virtualListener.getSensor().getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR || virtualListener.getSensor().getType() == Sensor.TYPE_ROTATION_VECTOR) {
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

                            System.arraycopy(values, 0, (float[]) param.args[1], 0, values.length);
                            if (virtualListener.getSensor().getType() == Sensor.TYPE_ROTATION_VECTOR)
                                virtualListener.sensorRef = sensors.get(Sensor.TYPE_ROTATION_VECTOR);
                            else
                                virtualListener.sensorRef = sensors.get(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
                        } else if (virtualListener.getSensor().getType() == Sensor.TYPE_GRAVITY) {
                            float[] values = new float[3];
                            float[] rotationMatrix = new float[9];
                            float[] gravity = new float[] {0F, 0F, 9.81F};

                            SensorManager.getRotationMatrix(rotationMatrix, null, this.accelerometerValues, this.magneticValues);
                            float x = rotationMatrix[0] * this.accelerometerValues[0] + rotationMatrix[1] * this.accelerometerValues[1] + rotationMatrix[2] * this.accelerometerValues[2];
                            float y = rotationMatrix[3] * this.accelerometerValues[0] + rotationMatrix[4] * this.accelerometerValues[1] + rotationMatrix[5] * this.accelerometerValues[2];
                            float z = rotationMatrix[6] * this.accelerometerValues[0] + rotationMatrix[7] * this.accelerometerValues[1] + rotationMatrix[8] * this.accelerometerValues[2];

                            float[] gravityRot = new float[3];
                            gravityRot[0] = gravity[0] * rotationMatrix[0] + gravity[1] * rotationMatrix[3] + gravity[2] * rotationMatrix[6];
                            gravityRot[1] = gravity[0] * rotationMatrix[1] + gravity[1] * rotationMatrix[4] + gravity[2] * rotationMatrix[7];
                            gravityRot[2] = gravity[0] * rotationMatrix[2] + gravity[1] * rotationMatrix[5] + gravity[2] * rotationMatrix[8];

                            values[0] = gravityRot[0];
                            values[1] = gravityRot[1];
                            values[2] = gravityRot[2];

                            System.arraycopy(values, 0, (float[]) param.args[1], 0, values.length);
                            virtualListener.sensorRef = sensors.get(Sensor.TYPE_GRAVITY);
                        } else if (virtualListener.getSensor().getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                            float[] values = new float[3];
                            float[] rotationMatrix = new float[9];
                            float[] gravity = new float[] {0F, 0F, 9.81F};

                            SensorManager.getRotationMatrix(rotationMatrix, null, this.accelerometerValues, this.magneticValues);
                            float x = rotationMatrix[0] * this.accelerometerValues[0] + rotationMatrix[1] * this.accelerometerValues[1] + rotationMatrix[2] * this.accelerometerValues[2];
                            float y = rotationMatrix[3] * this.accelerometerValues[0] + rotationMatrix[4] * this.accelerometerValues[1] + rotationMatrix[5] * this.accelerometerValues[2];
                            float z = rotationMatrix[6] * this.accelerometerValues[0] + rotationMatrix[7] * this.accelerometerValues[1] + rotationMatrix[8] * this.accelerometerValues[2];

                            float[] gravityRot = new float[3];
                            gravityRot[0] = gravity[0] * rotationMatrix[0] + gravity[1] * rotationMatrix[3] + gravity[2] * rotationMatrix[6];
                            gravityRot[1] = gravity[0] * rotationMatrix[1] + gravity[1] * rotationMatrix[4] + gravity[2] * rotationMatrix[7];
                            gravityRot[2] = gravity[0] * rotationMatrix[2] + gravity[1] * rotationMatrix[5] + gravity[2] * rotationMatrix[8];

                            values[0] = this.accelerometerValues[0] - gravityRot[0];
                            values[1] = this.accelerometerValues[1] - gravityRot[1];
                            values[2] = this.accelerometerValues[2] - gravityRot[2];

                            System.arraycopy(values, 0, (float[]) param.args[1], 0, values.length);
                            virtualListener.sensorRef = sensors.get(Sensor.TYPE_LINEAR_ACCELERATION);
                        }
                    }
                }
            }
        });
    }

    /*
        This uses the Hamilton product to multiply the vector converted to a quaternion with the rotation quaternion.
        Returns a new quaternion which is the rotated vector.
        Source:  https://en.wikipedia.org/wiki/Quaternion#Hamilton_product
     */
    public float[] rotateVectorByQuaternion(float[] vector, float[] quaternion) {
        float a = vector[0];
        float b = vector[1];
        float c = vector[2];
        float d = vector[3];

        float A = quaternion[0];
        float B = quaternion[1];
        float C = quaternion[2];
        float D = quaternion[3];

        float newQuaternionReal = a*A - b*B - c*C - d*D;
        float newQuaternioni = a*B + b*A + c*D - d*C;
        float newQuaternionj = a*C - b*D + c*A + d*B;
        float newQuaternionk = a*D + b*C - c*B + d*A;

        return new float[] {newQuaternionReal, newQuaternioni, newQuaternionj, newQuaternionk};
    }


    /*
        Credit for this code goes to http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToQuaternion/
        Additional credit goes to https://en.wikipedia.org/wiki/Quaternion for helping me understand how quaternions work
     */
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
            float S = (float)Math.sqrt(tr+1.0) * 2;
            qw = 0.25F * S;
            qx = (m21 - m12) / S;
            qy = (m02 - m20) / S;
            qz = (m10 - m01) / S;
        } else if ((m00 > m11)&(m00 > m22)) {
            float S = (float)Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            qw = (m21 - m12) / S;
            qx = 0.25F * S;
            qy = (m01 + m10) / S;
            qz = (m02 + m20) / S;
        } else if (m11 > m22) {
            float S = (float)Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            qw = (m02 - m20) / S;
            qx = (m01 + m10) / S;
            qy = 0.25F * S;
            qz = (m12 + m21) / S;
        } else {
            float S = (float)Math.sqrt(1.0 + m22 - m00 - m11) * 2;
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
                SparseArray<Sensor> mHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getObjectField(param.thisObject, "mHandleToSensor");

                int minDelayAccelerometer = mHandleToSensor.get(Sensor.TYPE_ACCELEROMETER).getMinDelay();

                while (iterator.hasNext()) {
                    Sensor sensor = iterator.next();
                    if (sensorsToEmulate.indexOfKey(sensor.getType()) >= 0) {
                        sensorsToEmulate.get(sensor.getType()).alreadyThere = true;
                    }
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

                for (int i = 0; i < sensorsToEmulate.size(); i++) {
                    SensorModel model = sensorsToEmulate.valueAt(i);
                    if (!model.alreadyThere) {
                        Sensor s = (Sensor) XposedHelpers.findConstructorBestMatch(android.hardware.Sensor.class).newInstance();
                        XposedHelpers.callMethod(s, "setType", sensorsToEmulate.keyAt(i));
                        XposedHelpers.setObjectField(s, "mName", model.name);
                        XposedHelpers.setObjectField(s, "mVendor", "Frazew");
                        XposedHelpers.setObjectField(s, "mVersion", BuildConfig.VERSION_CODE);
                        XposedHelpers.setObjectField(s, "mHandle", model.handle);
                        XposedHelpers.setObjectField(s, "mMinDelay", model.minDelay == -1 ? minDelayAccelerometer : model.minDelay);
                        XposedHelpers.setObjectField(s, "mResolution", model.resolution == -1 ? 0.01F : model.resolution); // This 0.01F is a placeholder, it doesn't seem to change anything but I keep it
                        if (model.maxRange != -1) XposedHelpers.setObjectField(s, "mMaxRange", model.maxRange);
                        mFullSensorList.add(s);
                        mHandleToSensor.append(model.handle, s);
                    }
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

                // We check that the listener isn't of type VirtualSensorListener. Although that should not happen, it would probably be nasty.
                if (sensorsToEmulate.indexOfKey(((Sensor) param.args[1]).getType()) >= 0 && !(listener instanceof  VirtualSensorListener)) {
                    SensorEventListener specialListener = new VirtualSensorListener(listener, ((Sensor) param.args[1]));
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

                    if (listener instanceof VirtualSensorListener) {
                        VirtualSensorListener specialListener = (VirtualSensorListener) listener;
                        if (specialListener.getRealListener() == (android.hardware.SensorEventListener) param.args[0]) {
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