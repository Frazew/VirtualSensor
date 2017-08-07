package fr.frazew.virtualgyroscope.hooks.sensorchange;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.SparseArray;
import android.util.SparseIntArray;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import fr.frazew.virtualgyroscope.VirtualSensorListener;
import fr.frazew.virtualgyroscope.XposedMod;
import fr.frazew.virtualgyroscope.hooks.SensorChange;

@SuppressWarnings("unchecked")
public class API23 extends XC_MethodReplacement {
    private SensorChange mSensorChange;

    public API23() {
        super(XC_MethodReplacement.PRIORITY_HIGHEST);
        this.mSensorChange = new SensorChange();
    }

    @Override
    protected Object replaceHookedMethod(XC_MethodReplacement.MethodHookParam param) throws Throwable {
        SensorEventListener listener = (SensorEventListener) XposedHelpers.getObjectField(param.thisObject, "mListener");
        int handle = (int) param.args[0];
        Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
        SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
        Sensor s = sensors.get(handle);

        SensorEvent t = null;
        SparseArray<SensorEvent> mSensorsEvents = (SparseArray<SensorEvent>) XposedHelpers.getObjectField(param.thisObject, "mSensorsEvents");
        synchronized (mSensorsEvents) {
            t = mSensorsEvents.get(XposedMod.sensorTypetoHandle.get(((VirtualSensorListener) listener).getSensor().getType()));
        }
        if (t == null) {
            return null;
        }
        t.timestamp = (long) param.args[3];
        t.accuracy = (int) param.args[2];

        boolean handled = false;
        if (listener instanceof VirtualSensorListener) {
            float[] values = this.mSensorChange.handleListener(s, (VirtualSensorListener) listener, ((float[]) param.args[1]).clone(), (int) param.args[2], (long) param.args[3]);
            if (values != null) {
                System.arraycopy(values, 0, t.values, 0, Math.min(t.values.length, values.length));
                t.sensor = ((VirtualSensorListener) listener).getSensor();
                handled = true;
            }
        }
        if (!handled) { //TODO Better way to call the original function than to copy it here ?
            System.arraycopy(param.args[1], 0, t.values, 0, t.values.length);
            t.sensor = s;
            SparseIntArray mSensorAccuracies = (SparseIntArray) XposedHelpers.getObjectField(param.thisObject, "mSensorAccuracies");
            // call onAccuracyChanged() only if the value changes
            final int accuracy = mSensorAccuracies.get(handle);
            if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                mSensorAccuracies.put(handle, t.accuracy);
                listener.onAccuracyChanged(t.sensor, t.accuracy);
            }
        }
        listener.onSensorChanged(t);

        return null;
    }
}