package fr.frazew.virtualgyroscope.hooks.constructor;

import android.hardware.Sensor;
import android.util.SparseArray;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.hooks.SystemSensorManagerHook;

public class API16 extends XC_MethodHook {
    private Class SENSOR_EVENT_POOL;
    private SystemSensorManagerHook mSystemSensorManagerHook;

    public API16(XC_LoadPackage.LoadPackageParam lpparam) {
        this.mSystemSensorManagerHook = new SystemSensorManagerHook(lpparam);
        this.SENSOR_EVENT_POOL = XposedHelpers.findClass("android.hardware.SensorManager$SensorEventPool", lpparam.classLoader);
    }

    @Override
    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        super.afterHookedMethod(param);
        ArrayList sListeners = (ArrayList) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sListeners");

        synchronized (sListeners) {
            ArrayList<Sensor> sFullSensorsList = (ArrayList<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sFullSensorsList");
            SparseArray<Sensor> sHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sHandleToSensor");

            XposedHelpers.findField(SystemSensorManagerHook.SYSTEM_SENSOR_MANAGER, "sFullSensorsList").setAccessible(true);
            XposedHelpers.findField(SystemSensorManagerHook.SYSTEM_SENSOR_MANAGER, "sHandleToSensor").setAccessible(true);

            this.mSystemSensorManagerHook.fillSensorLists(sFullSensorsList, sHandleToSensor);
            XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sHandleToSensor", sHandleToSensor.clone());
            XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sFullSensorsList", sFullSensorsList.clone());

            Object sPool = XposedHelpers.newInstance(this.SENSOR_EVENT_POOL, int.class, sHandleToSensor.size() * 2);
            XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sPool", sPool);
        }
    }
}
