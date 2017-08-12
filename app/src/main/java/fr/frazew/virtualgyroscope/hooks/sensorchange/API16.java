package fr.frazew.virtualgyroscope.hooks.sensorchange;

import android.hardware.Sensor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import fr.frazew.virtualgyroscope.VirtualSensorListener;
import fr.frazew.virtualgyroscope.XposedMod;
import fr.frazew.virtualgyroscope.hooks.SensorChange;

public class API16 extends XC_MethodHook {
    private SensorChange mSensorChange;

    public API16() {
        super(XC_MethodReplacement.PRIORITY_HIGHEST);
        this.mSensorChange = new SensorChange();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        Object listener = XposedHelpers.getObjectField(param.thisObject, "mSensorEventListener");
        Sensor s = (Sensor)param.args[0];

        if (listener instanceof VirtualSensorListener) {
            float[] values = this.mSensorChange.handleListener(s, (VirtualSensorListener) listener, ((float[]) param.args[1]).clone(), (int) param.args[2], (long) param.args[3], XposedMod.ACCELEROMETER_RESOLUTION, XposedMod.MAGNETIC_RESOLUTION);
            if (values != null) {
                System.arraycopy(values, 0, param.args[1], 0, values.length);
                param.args[0] = ((VirtualSensorListener) listener).getSensor();
            }// else param.setResult(null);
        }
    }
}