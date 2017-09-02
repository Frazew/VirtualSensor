package fr.frazew.virtualgyroscope.hooks.constructor;

import android.hardware.Sensor;

import java.util.ArrayList;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.hooks.SystemSensorManagerHook;

public class API24 extends XC_MethodHook {
    private SystemSensorManagerHook mSystemSensorManagerHook;

    public API24(XC_LoadPackage.LoadPackageParam lpparam) {
        this.mSystemSensorManagerHook = new SystemSensorManagerHook(lpparam);
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        super.afterHookedMethod(param);
        ArrayList<Sensor> mFullSensorsList = (ArrayList<Sensor>) XposedHelpers.getObjectField(param.thisObject, "mFullSensorsList");
        HashMap<Integer, Sensor> mHandleToSensor = (HashMap<Integer, Sensor>)((HashMap<Integer, Sensor>) XposedHelpers.getObjectField(param.thisObject, "mHandleToSensor")).clone();

        XposedHelpers.findField(SystemSensorManagerHook.SYSTEM_SENSOR_MANAGER, "mFullSensorsList").setAccessible(true);
        XposedHelpers.findField(SystemSensorManagerHook.SYSTEM_SENSOR_MANAGER, "mHandleToSensor").setAccessible(true);

        this.mSystemSensorManagerHook.fillSensorLists(mFullSensorsList, mHandleToSensor);
        XposedHelpers.setObjectField(param.thisObject, "mHandleToSensor", mHandleToSensor.clone());
        XposedHelpers.setObjectField(param.thisObject, "mFullSensorsList", mFullSensorsList.clone());
    }
}
