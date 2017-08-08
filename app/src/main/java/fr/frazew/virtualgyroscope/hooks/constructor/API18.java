package fr.frazew.virtualgyroscope.hooks.constructor;

import android.hardware.Sensor;
import android.util.SparseArray;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.hooks.SystemSensorManagerHook;

public class API18 extends XC_MethodHook {
    private SystemSensorManagerHook mSystemSensorManagerHook;

    public API18(XC_LoadPackage.LoadPackageParam lpparam) {
        this.mSystemSensorManagerHook = new SystemSensorManagerHook(lpparam);
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        super.afterHookedMethod(param);
        Object sSensorModuleLock = XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sSensorModuleLock");

        synchronized (sSensorModuleLock) {
            ArrayList<Sensor> sFullSensorsList = (ArrayList<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sFullSensorsList");
            SparseArray<Sensor> sHandleToSensor = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(param.thisObject.getClass(), "sHandleToSensor");

            XposedHelpers.findField(SystemSensorManagerHook.SYSTEM_SENSOR_MANAGER, "sFullSensorsList").setAccessible(true);
            XposedHelpers.findField(SystemSensorManagerHook.SYSTEM_SENSOR_MANAGER, "sHandleToSensor").setAccessible(true);

            this.mSystemSensorManagerHook.fillSensorLists(sFullSensorsList, sHandleToSensor);
            XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sHandleToSensor", sHandleToSensor.clone());
            XposedHelpers.setStaticObjectField(param.thisObject.getClass(), "sFullSensorsList", sFullSensorsList.clone());
        }
    }
}