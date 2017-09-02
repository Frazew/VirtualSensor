package fr.frazew.virtualgyroscope;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.SparseArray;
import android.util.SparseIntArray;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {
    public static boolean FIRST_LAUNCH_SINCE_BOOT = true;

    public static float MAGNETIC_RESOLUTION; // @TODO Change this too
    public static float ACCELEROMETER_RESOLUTION; // @TODO And this

    public static final SparseIntArray sensorTypetoHandle = new SparseIntArray() {{
        append(Sensor.TYPE_ROTATION_VECTOR, 4242);
        append(Sensor.TYPE_GYROSCOPE, 4243);
        if (Build.VERSION.SDK_INT >= 19) put(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, 4244);
        append(Sensor.TYPE_GRAVITY, 4245);
        append(Sensor.TYPE_LINEAR_ACCELERATION, 4246);
        if (Build.VERSION.SDK_INT >= 18) append(Sensor.TYPE_GAME_ROTATION_VECTOR, 4247);
    }};

    public static final SparseArray<SensorModel> sensorsToEmulate = new SparseArray<SensorModel>() {{
        append(Sensor.TYPE_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_ROTATION_VECTOR, "VirtualSensor RotationVector", sensorTypetoHandle.get(Sensor.TYPE_ROTATION_VECTOR), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_ROTATION_VECTOR : "", "none"));
        append(Sensor.TYPE_GYROSCOPE, new SensorModel(Sensor.TYPE_GYROSCOPE, "VirtualSensor Gyroscope", sensorTypetoHandle.get(Sensor.TYPE_GYROSCOPE), 0.01F, -1, 5460, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GYROSCOPE : "", "android.hardware.sensor.gyroscope"));
        if (Build.VERSION.SDK_INT >= 19) append(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "VirtualSensor GeomagneticRotationVector", sensorTypetoHandle.get(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR : "", "none"));
        append(Sensor.TYPE_GRAVITY, new SensorModel(Sensor.TYPE_GRAVITY, "VirtualSensor Gravity", sensorTypetoHandle.get(Sensor.TYPE_GRAVITY), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GRAVITY : "", "none"));
        append(Sensor.TYPE_LINEAR_ACCELERATION, new SensorModel(Sensor.TYPE_LINEAR_ACCELERATION, "VirtualSensor LinearAcceleration", sensorTypetoHandle.get(Sensor.TYPE_LINEAR_ACCELERATION), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_LINEAR_ACCELERATION : "", "none")); // Had to use another handle as it broke the magnetic sensor's readings (?!)
        if (Build.VERSION.SDK_INT >= 18) append(Sensor.TYPE_GAME_ROTATION_VECTOR, new SensorModel(Sensor.TYPE_GAME_ROTATION_VECTOR, "VirtualSensor GameRotationVector", sensorTypetoHandle.get(Sensor.TYPE_GAME_ROTATION_VECTOR), 0.01F, -1, -1, (Build.VERSION.SDK_INT > 19) ? Sensor.STRING_TYPE_GAME_ROTATION_VECTOR : "", "none"));
    }};

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals("android")) {
            if (FIRST_LAUNCH_SINCE_BOOT) {
                FIRST_LAUNCH_SINCE_BOOT = false;
                XposedBridge.log("VirtualSensor: Using version " + BuildConfig.VERSION_NAME);
            }
            hookPackageFeatures(lpparam); // @TODO Revisit this hook to make it more flexible
        }

        hookSensorValues(lpparam);
        addSensors(lpparam);
        enableSensors(lpparam);
        registerListenerHook(lpparam);

        hookCardboard(lpparam);
    }

    private void hookSensorValues(final LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT >= 24)
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue",
                    lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class,
                    new fr.frazew.virtualgyroscope.hooks.sensorchange.API24());
        else if (Build.VERSION.SDK_INT == 23)
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue",
                    lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class,
                    new fr.frazew.virtualgyroscope.hooks.sensorchange.API23());
        else if (Build.VERSION.SDK_INT >= 18)
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue",
                    lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class,
                    new fr.frazew.virtualgyroscope.hooks.sensorchange.API18());
        else if (Build.VERSION.SDK_INT >= 16)
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$ListenerDelegate",
                    lpparam.classLoader, "onSensorChangedLocked", Sensor.class, float[].class, long[].class, int.class,
                    new fr.frazew.virtualgyroscope.hooks.sensorchange.API16());
        else XposedBridge.log("VirtualSensor: Using SDK version " + Build.VERSION.SDK_INT + ", this is not supported");
    }

    @SuppressWarnings("unchecked")
    private void addSensors(final LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT >= 24)
            XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager",
                    lpparam.classLoader, android.content.Context.class, android.os.Looper.class,
                    new fr.frazew.virtualgyroscope.hooks.constructor.API24(lpparam));
        else if (Build.VERSION.SDK_INT == 23)
            XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager",
                    lpparam.classLoader, android.content.Context.class, android.os.Looper.class,
                    new fr.frazew.virtualgyroscope.hooks.constructor.API23(lpparam));
        else if (Build.VERSION.SDK_INT >= 18)
            XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager",
                    lpparam.classLoader, android.content.Context.class, android.os.Looper.class,
                    new fr.frazew.virtualgyroscope.hooks.constructor.API18(lpparam));
        else if (Build.VERSION.SDK_INT >= 16)
            XposedHelpers.findAndHookConstructor("android.hardware.SystemSensorManager",
                    lpparam.classLoader, android.os.Looper.class,
                    new fr.frazew.virtualgyroscope.hooks.constructor.API16(lpparam));
        else XposedBridge.log("VirtualSensor: Using SDK version " + Build.VERSION.SDK_INT + ", this is not supported");
    }

    private void enableSensors(final LoadPackageParam lpparam) throws XposedHelpers.ClassNotFoundError {
        if (Build.VERSION.SDK_INT >= 18) {
            try {
                XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$BaseEventQueue", lpparam.classLoader, "enableSensor", android.hardware.Sensor.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (sensorsToEmulate.indexOfKey(((Sensor) param.args[0]).getType()) >= 0 && !sensorsToEmulate.get(((Sensor) param.args[0]).getType()).isAlreadyNative) {
                            param.setResult(0);
                        }
                        super.afterHookedMethod(param);
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log("VirtualSensor: Could not hook the AOSP enableSensor method, trying an alternative hook.");
                try {
                    XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager$BaseEventQueue", lpparam.classLoader, "enableSensor", android.hardware.Sensor.class, int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (sensorsToEmulate.indexOfKey(((Sensor) param.args[0]).getType()) >= 0 && !sensorsToEmulate.get(((Sensor) param.args[0]).getType()).isAlreadyNative) {
                                param.setResult(0);
                            }
                            super.afterHookedMethod(param);
                        }
                    });
                } catch (NoSuchMethodError e1) {
                    XposedBridge.log("VirtualSensor: The alternative enableSensor hook failed, but the module might still work.");
                }
            }
        } else if (Build.VERSION.SDK_INT >= 16) {
            XposedHelpers.findAndHookMethod("android.hardware.SystemSensorManager", lpparam.classLoader, "enableSensorLocked", android.hardware.Sensor.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sensorsToEmulate.indexOfKey(((Sensor) param.args[0]).getType()) >= 0 && !sensorsToEmulate.get(((Sensor) param.args[0]).getType()).isAlreadyNative) {
                        param.setResult(true);
                    }
                    super.afterHookedMethod(param);
                }
            });
        } else XposedBridge.log("VirtualSensor: Using SDK version " + Build.VERSION.SDK_INT + ", this is not supported");
    }

    private void registerListenerHook(final LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT <= 18) {
            XposedHelpers.findAndHookMethod("android.hardware.SensorManager", lpparam.classLoader, "registerListener", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, android.os.Handler.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[1] == null) return;
                    SensorEventListener listener = (SensorEventListener) param.args[0];

                    // We check that the listener isn't of type VirtualSensorListener. Although that should not happen, it would probably be nasty.
                    if (sensorsToEmulate.indexOfKey(((Sensor) param.args[1]).getType()) >= 0 && !(listener instanceof VirtualSensorListener) && !sensorsToEmulate.get(((Sensor) param.args[1]).getType()).isAlreadyNative) {
                        SensorEventListener specialListener = new VirtualSensorListener(listener, ((Sensor) param.args[1]));
                        XposedHelpers.callMethod(param.thisObject, "registerListener",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_ACCELEROMETER),
                                param.args[2],
                                param.args[3]
                        );
                        XposedHelpers.callMethod(param.thisObject, "registerListener",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_MAGNETIC_FIELD),
                                param.args[2],
                                param.args[3]
                        );

                        param.args[0] = specialListener;
                    }
                }
            });
        } else {
            XposedHelpers.findAndHookMethod("android.hardware.SensorManager", lpparam.classLoader, "registerListener", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, int.class, new XC_MethodHook() {
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
                                null,
                                param.args[3],
                                0
                        );
                        XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_MAGNETIC_FIELD),
                                XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                                null,
                                param.args[3],
                                0
                        );

                        param.args[0] = specialListener;
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.hardware.SensorManager", lpparam.classLoader, "registerListener", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, android.os.Handler.class, new XC_MethodHook() {
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
                                param.args[3],
                                0,
                                0
                        );
                        XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_MAGNETIC_FIELD),
                                XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                                param.args[3],
                                0,
                                0
                        );

                        param.args[0] = specialListener;
                    }
                }
            });

            XposedHelpers.findAndHookMethod("android.hardware.SensorManager", lpparam.classLoader, "registerListener", android.hardware.SensorEventListener.class, android.hardware.Sensor.class, int.class, int.class, android.os.Handler.class, new XC_MethodHook() {
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
                                param.args[4],
                                param.args[3],
                                0
                        );
                        XposedHelpers.callMethod(param.thisObject, "registerListenerImpl",
                                specialListener,
                                XposedHelpers.callMethod(param.thisObject, "getDefaultSensor", Sensor.TYPE_MAGNETIC_FIELD),
                                XposedHelpers.callStaticMethod(android.hardware.SensorManager.class, "getDelay", param.args[2]),
                                param.args[4],
                                param.args[3],
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
                        if (specialListener.getRealListener() == param.args[0]) {
                            listenersToRemove.add(listener);
                        }
                    }
                }

                for (SensorEventListener listener : listenersToRemove) {
                    XposedHelpers.callMethod(param.thisObject, "unregisterListenerImpl", listener, null);
                }
            }
        });
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
                    if (!(boolean) param.getResult() && param.args[0] == PackageManager.FEATURE_SENSOR_GYROSCOPE) {
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

    @SuppressWarnings("unchecked")
    private void hookCardboard(final LoadPackageParam lpparam) {
        try {
            Class headTransformTMP = XposedHelpers.findClassIfExists("com.google.vrtoolkit.cardboard.HeadTransform", lpparam.classLoader);

            if (headTransformTMP == null) {
                headTransformTMP = XposedHelpers.findClassIfExists("com.google.vr.sdk.base.HeadTransform", lpparam.classLoader);
                if (headTransformTMP != null)
                    XposedBridge.log("VirtualSensor: Did not find com.google.vrtoolkit.cardboard.HeadTransform but found com.google.vr.sdk.base.HeadTransform");
            }

            final Class headTransform = headTransformTMP;
            final int sensorToUse = Build.VERSION.SDK_INT >= 18 ? Sensor.TYPE_GAME_ROTATION_VECTOR : Sensor.TYPE_ROTATION_VECTOR;

            if (headTransform != null) {
                XposedBridge.log("VirtualSensor: Found the Google Cardboard library in " + lpparam.packageName + ", hooking HeadTransform");
                XposedHelpers.findAndHookConstructor(headTransform, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        SensorManager mgr = (SensorManager) AndroidAppHelper.currentApplication().getSystemService(Context.SENSOR_SERVICE);
                        SensorEventListener listener = new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event) {
                                if (event.sensor.getType() == sensorToUse) {
                                    if (event.values != null) {
                                        try {
                                            Field htMatrix = XposedHelpers.findFirstFieldByExactType(headTransform, float[].class);
                                            float[] rotationMatrix = (float[]) htMatrix.get(param.thisObject);
                                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

                                            XposedHelpers.setObjectField(param.thisObject, htMatrix.getName(), rotationMatrix);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                            }
                        };
                        mgr.registerListener(listener, mgr.getDefaultSensor(sensorToUse), mgr.getDefaultSensor(sensorToUse).getMinDelay());
                        super.afterHookedMethod(param);
                    }
                });
            }

        } catch (Exception e) {e.printStackTrace();}
    }
}
