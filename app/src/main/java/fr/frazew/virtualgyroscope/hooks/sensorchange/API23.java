package fr.frazew.virtualgyroscope.hooks.sensorchange;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.util.SparseArray;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import fr.frazew.virtualgyroscope.VirtualSensorListener;
import fr.frazew.virtualgyroscope.XposedMod;
import fr.frazew.virtualgyroscope.hooks.SensorChange;

@SuppressWarnings("unchecked")
public class API23 extends XC_MethodHook {
    private SensorChange mSensorChange;

    public API23() {
        super(XC_MethodHook.PRIORITY_HIGHEST);
        this.mSensorChange = new SensorChange();
    }

    @Override
    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        SensorEventListener listener = (SensorEventListener) XposedHelpers.getObjectField(param.thisObject, "mListener");
        int handle = (int) param.args[0];
        Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
        SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
        Sensor s = sensors.get(handle);

        boolean handled = false;
        if (listener instanceof VirtualSensorListener) {
            float[] values = this.mSensorChange.handleListener(s, (VirtualSensorListener) listener, ((float[]) param.args[1]).clone(), (int) param.args[2], (long) param.args[3]);
            if (values != null) {
                System.arraycopy(values, 0, ((float[]) param.args[1]), 0, ((float[]) param.args[1]).length);
                param.args[0] = XposedMod.sensorTypetoHandle.get(((VirtualSensorListener) listener).getSensor().getType());
            }
        }
    }
}