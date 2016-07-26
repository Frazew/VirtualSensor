package fr.frazew.virtualgyroscope.hooks;

import android.hardware.Sensor;
import android.util.SparseArray;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.BuildConfig;
import fr.frazew.virtualgyroscope.SensorModel;
import fr.frazew.virtualgyroscope.XposedMod;

public class SystemSensorManagerHook {

    public static List<Object> fillSensorLists(ArrayList<Sensor> fullSensorList, SparseArray<Sensor> handleToSensor, XC_LoadPackage.LoadPackageParam lpparam) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Iterator<Sensor> iterator = fullSensorList.iterator();

        int minDelayAccelerometer = 0;
        while (iterator.hasNext()) {
            Sensor sensor = iterator.next();
            if (XposedMod.sensorsToEmulate.indexOfKey(sensor.getType()) >= 0) {
                XposedMod.sensorsToEmulate.get(sensor.getType()).alreadyThere = true;
            }
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) minDelayAccelerometer = sensor.getMinDelay();
        }

        XposedHelpers.findConstructorBestMatch(Sensor.class).setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mName").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mType").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mVendor").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mVersion").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mHandle").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mResolution").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mMinDelay").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mMaxRange").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mStringType").setAccessible(true);
        XposedHelpers.findField(Sensor.class, "mRequiredPermission").setAccessible(true);

        for (int i = 0; i < XposedMod.sensorsToEmulate.size(); i++) {
            SensorModel model = XposedMod.sensorsToEmulate.valueAt(i);
            if (!model.alreadyThere) {
                Sensor s = (Sensor) XposedHelpers.findConstructorBestMatch(Sensor.class).newInstance();
                XposedHelpers.setObjectField(s, "mType", XposedMod.sensorsToEmulate.keyAt(i));
                XposedHelpers.setObjectField(s, "mName", model.name);
                XposedHelpers.setObjectField(s, "mVendor", "Frazew");
                XposedHelpers.setObjectField(s, "mVersion", BuildConfig.VERSION_CODE);
                XposedHelpers.setObjectField(s, "mHandle", model.handle);
                XposedHelpers.setObjectField(s, "mMinDelay", model.minDelay == -1 ? minDelayAccelerometer : model.minDelay);
                XposedHelpers.setObjectField(s, "mResolution", model.resolution == -1 ? 0.01F : model.resolution); // This 0.01F is a placeholder, it doesn't seem to change anything but I keep it
                if (model.maxRange != -1)
                    XposedHelpers.setObjectField(s, "mMaxRange", model.maxRange);
                XposedHelpers.setObjectField(s, "mStringType", model.stringType);
                if (!model.permission.equals("none")) {
                    XposedHelpers.setObjectField(s, "mRequiredPermission", model.permission);
                }
                fullSensorList.add(s);
                handleToSensor.append(model.handle, s);
            }
        }

        List<Object> list = new ArrayList<>();
        list.add(fullSensorList);
        list.add(handleToSensor);
        return list;
    }

    @SuppressWarnings("unchecked")
    public static class API1617 extends XC_MethodHook {
        private XC_LoadPackage.LoadPackageParam lpparam;

        public API1617(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        @Override
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            ArrayList sListeners = (ArrayList) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sListeners");

            synchronized (sListeners) {
                if (!XposedHelpers.getStaticBooleanField(param.thisObject.getClass(), "sSensorModuleInitialized")) {
                    ArrayList<Sensor> sFullSensorsList = (ArrayList<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sFullSensorsList");
                    SparseArray<Sensor> sHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sHandleToSensor");

                    List<Object> sensors = fillSensorLists(sFullSensorsList, sHandleToSensor, lpparam);

                    XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "sFullSensorsList").setAccessible(true);
                    XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "sHandleToSensor").setAccessible(true);

                    XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sHandleToSensor", sensors.get(0));
                    XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sFullSensorsList", sensors.get(1));

                    Class sensorEventPoolClass = XposedHelpers.findClass("android.hardware.SensorManager$SensorEventPool", lpparam.classLoader);
                    Object sPool = XposedHelpers.newInstance(sensorEventPoolClass, int.class, ((ArrayList<Sensor>)sensors.get(1)).size() * 2);
                    XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sPool", sPool);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static class API18Plus extends XC_MethodHook {
        private XC_LoadPackage.LoadPackageParam lpparam;

        public API18Plus(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            Object sSensorModuleLock = XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sSensorModuleLock");

            synchronized (sSensorModuleLock) {
                if (!XposedHelpers.getStaticBooleanField(param.thisObject.getClass(), "sSensorModuleInitialized")) {
                    ArrayList<Sensor> sFullSensorsList = (ArrayList<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sFullSensorsList");
                    SparseArray<Sensor> sHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sHandleToSensor");

                    List<Object> sensors = fillSensorLists(sFullSensorsList, sHandleToSensor, lpparam);

                    XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "sFullSensorsList").setAccessible(true);
                    XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "sHandleToSensor").setAccessible(true);
                    XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sHandleToSensor", sensors.get(1));
                    XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sFullSensorsList", sensors.get(0));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static class API23Plus extends XC_MethodHook {
        private XC_LoadPackage.LoadPackageParam lpparam;

        public API23Plus(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            ArrayList<Sensor> mFullSensorsList = (ArrayList<Sensor>) XposedHelpers.getObjectField(param.thisObject, "mFullSensorsList");
            SparseArray<Sensor> mHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getObjectField(param.thisObject, "mHandleToSensor");

            List<Object> sensors = fillSensorLists(mFullSensorsList, mHandleToSensor, lpparam);

            XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "mFullSensorsList").setAccessible(true);
            XposedHelpers.findField(XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader), "mHandleToSensor").setAccessible(true);

            XposedHelpers.setObjectField(param.thisObject, "mHandleToSensor", sensors.get(1));
            XposedHelpers.setObjectField(param.thisObject, "mFullSensorsList", sensors.get(0));
        }
    }
}
