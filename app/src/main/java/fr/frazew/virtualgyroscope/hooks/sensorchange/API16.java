package fr.frazew.virtualgyroscope.hooks.sensorchange;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.VirtualSensorListener;
import fr.frazew.virtualgyroscope.hooks.SensorChange;

public class API16 extends XC_MethodReplacement {
    private static Class SYSTEM_SENSOR_MANAGER;
    private SensorChange mSensorChange;
    private XC_LoadPackage.LoadPackageParam lpparam;

    public API16(final XC_LoadPackage.LoadPackageParam lpparam) {
        super(XC_MethodReplacement.PRIORITY_HIGHEST);
        this.mSensorChange = new SensorChange();
        this.lpparam = lpparam;
        SYSTEM_SENSOR_MANAGER = XposedHelpers.findClass("android.hardware.SystemSensorManager", this.lpparam.classLoader);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object replaceHookedMethod(XC_MethodReplacement.MethodHookParam param) throws Throwable {
        SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(SYSTEM_SENSOR_MANAGER, "sHandleToSensor");
        Object listener = XposedHelpers.getObjectField(param.thisObject, "mSensorEventListener");
        Sensor s = (Sensor)param.args[0];

        Handler mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
        Object sPool = XposedHelpers.getStaticObjectField(SYSTEM_SENSOR_MANAGER, "sPool");

        SensorEvent t = (SensorEvent)XposedHelpers.callMethod(sPool, "getFromPool");
        final float[] v = t.values;

        boolean handled = false;
        if (listener instanceof VirtualSensorListener) {
            float[] values = this.mSensorChange.handleListener(s, (VirtualSensorListener) listener, ((float[]) param.args[1]).clone(), (int) param.args[2], (long) param.args[3]);
            if (values != null) {
                v[0] = values[0];
                v[1] = values[1];
                v[2] = values[2];
                t.sensor = ((VirtualSensorListener) listener).getSensor();
                handled = true;
            }
        }
        if (!handled) {
            v[0] = ((long[])param.args[1])[0];
            v[1] = ((long[])param.args[1])[1];
            v[2] = ((long[])param.args[1])[2];
            t.sensor = s;
        }

        t.timestamp = ((long[])param.args[2])[0];
        t.accuracy = (int)param.args[3];
        Message msg = Message.obtain();
        msg.what = 0;
        msg.obj = t;
        XposedHelpers.callMethod(msg, "setAsynchronous", true); // Refuses to compile if the method is called directly
        mHandler.sendMessage(msg);

        return null;
    }
}