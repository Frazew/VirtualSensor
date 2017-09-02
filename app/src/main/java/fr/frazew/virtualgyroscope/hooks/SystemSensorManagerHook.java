package fr.frazew.virtualgyroscope.hooks;

import android.hardware.Sensor;
import android.os.Build;
import android.util.SparseArray;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.BuildConfig;
import fr.frazew.virtualgyroscope.SensorModel;
import fr.frazew.virtualgyroscope.XposedMod;

public class SystemSensorManagerHook {
    public static Class SYSTEM_SENSOR_MANAGER;

    public SystemSensorManagerHook(XC_LoadPackage.LoadPackageParam lpparam) {
        SYSTEM_SENSOR_MANAGER = XposedHelpers.findClass("android.hardware.SystemSensorManager", lpparam.classLoader);
    }

    public void fillSensorLists(ArrayList<Sensor> fullSensorList, Object handleToSensor) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Iterator<Sensor> iterator = fullSensorList.iterator();

        int minDelayAccelerometer = 0;
        List<SensorModel> sensorsNotToAdd = new ArrayList<>();
        while (iterator.hasNext()) {
            Sensor sensor = iterator.next();
            if (XposedMod.sensorsToEmulate.indexOfKey(sensor.getType()) >= 0) {
                sensorsNotToAdd.add(XposedMod.sensorsToEmulate.get(sensor.getType()));
                if (!sensor.getVendor().equals("Xposed")) XposedMod.sensorsToEmulate.get(sensor.getType()).isAlreadyNative = true;
            }

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                minDelayAccelerometer = sensor.getMinDelay();
                XposedMod.ACCELEROMETER_RESOLUTION = sensor.getResolution();
            }
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) XposedMod.MAGNETIC_RESOLUTION = sensor.getResolution();
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
        if (Build.VERSION.SDK_INT > 19) XposedHelpers.findField(Sensor.class, "mStringType").setAccessible(true);
        if (Build.VERSION.SDK_INT > 19) XposedHelpers.findField(Sensor.class, "mRequiredPermission").setAccessible(true);

        for (int i = 0; i < XposedMod.sensorsToEmulate.size(); i++) {
            SensorModel model = XposedMod.sensorsToEmulate.valueAt(i);
            if (!sensorsNotToAdd.contains(model)) {
                Sensor s = (Sensor) XposedHelpers.findConstructorBestMatch(Sensor.class).newInstance();
                XposedHelpers.setObjectField(s, "mType", XposedMod.sensorsToEmulate.keyAt(i));
                XposedHelpers.setObjectField(s, "mName", model.name);
                XposedHelpers.setObjectField(s, "mVendor", "Xposed");
                XposedHelpers.setObjectField(s, "mVersion", BuildConfig.VERSION_CODE);
                XposedHelpers.setObjectField(s, "mHandle", model.handle);
                XposedHelpers.setObjectField(s, "mMinDelay", model.minDelay == -1 ? minDelayAccelerometer : model.minDelay);
                if (Build.VERSION.SDK_INT > 19)
                    XposedHelpers.setObjectField(s, "mStringType", model.stringType);
                XposedHelpers.setObjectField(s, "mResolution", model.resolution == -1 ? 0.01F : model.resolution); // This 0.01F is a placeholder, it doesn't seem to change anything but I keep it
                if (model.maxRange != -1)
                    XposedHelpers.setObjectField(s, "mMaxRange", model.maxRange);

                if (!model.permission.equals("none") && Build.VERSION.SDK_INT > 19)
                    XposedHelpers.setObjectField(s, "mRequiredPermission", model.permission);

                fullSensorList.add(s);
                if (handleToSensor.getClass() == SparseArray.class) ((SparseArray) handleToSensor).put(model.handle, s);
                else if (handleToSensor.getClass() == HashMap.class) ((HashMap) handleToSensor).put(model.handle, s);
            }
        }
    }
}
