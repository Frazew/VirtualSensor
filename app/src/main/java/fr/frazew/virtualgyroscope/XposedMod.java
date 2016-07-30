package fr.frazew.virtualgyroscope;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Build;
import android.util.SparseArray;
import android.util.SparseIntArray;

import de.robv.android.xposed.XposedBridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import fr.frazew.virtualgyroscope.hooks.SensorChangeHook;
import fr.frazew.virtualgyroscope.hooks.SystemSensorManagerHook;

public class XposedMod implements IXposedHookLoadPackage {
    public static boolean FIRST_LAUNCH_SINCE_BOOT = true;

    public static float[] ROTATION_MATRIX = new float[16]; // @TODO Change this to a better way
    public static float MAGNETIC_ACCURACY; // @TODO Change this too
    public static float ACCELEROMETER_ACCURACY; // @TODO And this

    public static final SparseIntArray sensorTypetoHandle = new SparseIntArray() {{
        append(Sensor.TYPE_ROTATION_VECTOR, 4242);
        append(Sensor.TYPE_GYROSCOPE, 4243);
        if (Build.VERSION.SDK_INT >= 19) put(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, 4244);
        append(Sensor.TYPE_GRAVITY, 4245);
        append(Sensor.TYPE_LINEAR_ACCELERATION, 4246);
    }};

    public static final SparseArray<SensorModel> sensorsToEmulate = new SparseArray<SensorModel>() {{
        append(Sensor.TYPE_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_ROTATION_VECTOR, "VirtualSensor RotationVector", sensorTypetoHandle.get(Sensor.TYPE_ROTATION_VECTOR), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_ROTATION_VECTOR : "", "none"));
        append(Sensor.TYPE_GYROSCOPE, new SensorModel(Sensor.TYPE_GYROSCOPE, "VirtualSensor Gyroscope", sensorTypetoHandle.get(Sensor.TYPE_GYROSCOPE), 0.01F, -1, 5460, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GYROSCOPE : "", "android.hardware.sensor.gyroscope"));
        if (Build.VERSION.SDK_INT >= 19) append(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "VirtualSensor GeomagneticRotationVector", sensorTypetoHandle.get(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR : "", "none"));
        append(Sensor.TYPE_GRAVITY, new SensorModel(Sensor.TYPE_GRAVITY, "VirtualSensor Gravity", sensorTypetoHandle.get(Sensor.TYPE_GRAVITY), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GRAVITY : "", "none"));
        append(Sensor.TYPE_LINEAR_ACCELERATION, new SensorModel(Sensor.TYPE_LINEAR_ACCELERATION, "VirtualSensor LinearAcceleration", sensorTypetoHandle.get(Sensor.TYPE_LINEAR_ACCELERATION), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_LINEAR_ACCELERATION : "", "none")); // Had to use another handle as it broke the magnetic sensor's readings (?!)
    }};

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals("android")) {
            if (FIRST_LAUNCH_SINCE_BOOT) {
                FIRST_LAUNCH_SINCE_BOOT = false;
                XposedBridge.log("VirtualSensor: Using version " + BuildConfig.VERSION_NAME);
            }

            hookPackageFeatures(lpparam);
        }
        hookSensorValues(lpparam);
        addSensors(lpparam);
        //hookCarboard(lpparam);

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
    private void hookCarboard(final LoadPackageParam lpparam) {
        try {
            Class<?> headTransform = XposedHelpers.findClass("com.google.vrtoolkit.cardboard.HeadTransform", lpparam.classLoader);
            XposedHelpers.findAndHookConstructor(headTransform, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                            new SensorEventListener() {
                                @Override
                                public void onSensorChanged(SensorEvent event) {

                                }

                                @Override
                                public void onAccuracyChanged(Sensor sensor, int accuracy) {

                                }
                            },
                            XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_GYROSCOPE),
                            0,
                            null
                    );
                    super.afterHookedMethod(param);
                }
            });
            XposedHelpers.findAndHookMethod("com.google.vrtoolkit.cardboard.HeadTransform", lpparam.classLoader, "getHeadView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(XposedMod.ROTATION_MATRIX);
                    XposedBridge.log("VirtualSensor: Setting ROTATION_MATRIX as HeadTransform");
                    super.afterHookedMethod(param);
                }
            });
        } catch (Exception e) {}

        /*try {
            Class<?> eye = XposedHelpers.findClass("com.google.vrtoolkit.cardboard.Eye", lpparam.classLoader);
            XposedHelpers.findAndHookConstructor(eye, lpparam.classLoader, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                            new SensorEventListener() {
                                @Override
                                public void onSensorChanged(SensorEvent event) {

                                }

                                @Override
                                public void onAccuracyChanged(Sensor sensor, int accuracy) {

                                }
                            },
                            XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_GYROSCOPE),
                            0,
                            null
                    );
                    super.afterHookedMethod(param);
                }
            });
            XposedHelpers.findAndHookMethod("com.google.vrtoolkit.cardboard.Eye", lpparam.classLoader, "getEyeView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(XposedMod.ROTATION_MATRIX);
                    super.afterHookedMethod(param);
                }
            });
        } catch (Exception e) {}*/
    }

    @SuppressWarnings("unchecked")
    private void hookPackageFeatures(final LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT >= 21) {
            Class<?> pkgMgrSrv = XposedHelpers.findClass("com.android.server.SystemConfig", lpparam.classLoader);
            XposedBridge.hookAllMethods(pkgMgrSrv, "getAvailableFeatures", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Map<String, FeatureInfo> mAvailableFeatures = (Map<String, FeatureInfo>) param.getResult();
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
        } else {
            Class<?> pkgMgrSrv = XposedHelpers.findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader);
            XposedBridge.hookAllMethods(pkgMgrSrv, "getSystemAvailableFeatures", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() != null) {
                        Object mPackages = XposedHelpers.getObjectField(param.thisObject, "mPackages");
                        synchronized (mPackages) {
                            Map<String, FeatureInfo> mAvailableFeatures = (Map<String, FeatureInfo>) XposedHelpers.getObjectField(param.thisObject, "mAvailableFeatures");
                            int openGLEsVersion = (int) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "getInt", "ro.opengles.version", FeatureInfo.GL_ES_VERSION_UNDEFINED);
                            if (!mAvailableFeatures.containsKey(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                                FeatureInfo gyro = new FeatureInfo();
                                gyro.name = PackageManager.FEATURE_SENSOR_GYROSCOPE;
                                gyro.reqGlEsVersion = openGLEsVersion;
                                mAvailableFeatures.put(PackageManager.FEATURE_SENSOR_GYROSCOPE, gyro);
                            }
                            XposedHelpers.setObjectField(param.thisObject, "mAvailableFeatures", mAvailableFeatures);
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(pkgMgrSrv, "hasSystemFeature", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(boolean) param.getResult() && (String) param.args[0] == PackageManager.FEATURE_SENSOR_GYROSCOPE) {
                        Object mPackages = XposedHelpers.getObjectField(param.thisObject, "mPackages");
                        synchronized (mPackages) {
                            Map<String, FeatureInfo> mAvailableFeatures = (Map<String, FeatureInfo>) param.getResult();
                            int openGLEsVersion = (int) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "getInt", "ro.opengles.version", FeatureInfo.GL_ES_VERSION_UNDEFINED);
                            FeatureInfo gyro = new FeatureInfo();
                            gyro.name = PackageManager.FEATURE_SENSOR_GYROSCOPE;
                            gyro.reqGlEsVersion = openGLEsVersion;
                            mAvailableFeatures.put(PackageManager.FEATURE_SENSOR_GYROSCOPE, gyro);
                            XposedHelpers.setObjectField(param.thisObject, "mAvailableFeatures", mAvailableFeatures);
                            param.setResult(true);
                        }
                    }
                }
            });
        }
    }

    private void hookSensorValues(final LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT >= 18) {
            if (Build.VERSION.SDK_INT >= 23) XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue", lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class, new SensorChangeHook.API23Plus(lpparam));
            else XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue", lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class, new SensorChangeHook.API18Plus(lpparam));
        } else {
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$ListenerDelegate", lpparam.classLoader, "onSensorChangedLocked", Sensor.class, float[].class, long[].class, int.class, new SensorChangeHook.API1617(lpparam));
        }
    }

    @SuppressWarnings("unchecked")
    private void addSensors(final LoadPackageParam lpparam) {

        // SystemSensorManager constructor hook, starting from SDK16
        if (Build.VERSION.SDK_INT == 16 || Build.VERSION.SDK_INT == 17) XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager", lpparam.classLoader, android.os.Looper.class, new SystemSensorManagerHook.API1617(lpparam));
        else if (Build.VERSION.SDK_INT > 17 && Build.VERSION.SDK_INT < 23) XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager", lpparam.classLoader, android.content.Context.class, android.os.Looper.class, new SystemSensorManagerHook.API18Plus(lpparam));
        else XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager", lpparam.classLoader, android.content.Context.class, android.os.Looper.class, new SystemSensorManagerHook.API23Plus(lpparam));

        // registerListenerImpl hook
        if (Build.VERSION.SDK_INT <= 18) {
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "registerListenerImpl", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, android.os.Handler.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[1] == null) return;
                    SensorEventListener listener = (SensorEventListener) param.args[0];

                    // We check that the listener isn't of type VirtualSensorListener. Although that should not happen, it would probably be nasty.
                    if (sensorsToEmulate.indexOfKey(((Sensor) param.args[1]).getType()) >= 0 && !(listener instanceof VirtualSensorListener) && !sensorsToEmulate.get(((Sensor) param.args[1]).getType()).isAlreadyNative) {
                        SensorEventListener specialListener = new VirtualSensorListener(listener, ((Sensor) param.args[1]));
                        XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_ACCELEROMETER),
                                XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                                (android.os.Handler) param.args[3]
                        );
                        XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_MAGNETIC_FIELD),
                                XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                                (android.os.Handler) param.args[3]
                        );

                        param.args[0] = specialListener;
                    }
                }
            });
        } else {
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "registerListenerImpl", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, android.os.Handler.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[1] == null) return;
                    SensorEventListener listener = (SensorEventListener) param.args[0];

                    // We check that the listener isn't of type VirtualSensorListener. Although that should not happen, it would probably be nasty.
                    if (sensorsToEmulate.indexOfKey(((Sensor) param.args[1]).getType()) >= 0 && !(listener instanceof VirtualSensorListener) && !sensorsToEmulate.get(((Sensor) param.args[1]).getType()).isAlreadyNative) {
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
        }

        // This hook does not need to change depending on the SDK version
        XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "unregisterListenerImpl", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                List<SensorEventListener> listenersToRemove = new ArrayList<>();
                for (Map.Entry<Object, Object> entry : ((HashMap<Object, Object>) XposedHelpers.getObjectField(param.thisObject, "mSensorListeners")).entrySet()) {
                    SensorEventListener listener = (SensorEventListener) entry.getKey();

                    if (listener instanceof VirtualSensorListener) {
                        VirtualSensorListener specialListener = (VirtualSensorListener) listener;
                        if (specialListener.getRealListener() == (android.hardware.SensorEventListener) param.args[0]) {
                            listenersToRemove.add(listener);
                        }
                    }
                }

                for (SensorEventListener listener : listenersToRemove) {
                    XposedHelpers.callMethod(param.thisObject, "unregisterListenerImpl", listener, (Sensor) null);
                }
            }
        });
    }
}
