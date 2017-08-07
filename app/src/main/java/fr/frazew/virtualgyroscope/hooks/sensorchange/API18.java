package fr.frazew.virtualgyroscope.hooks.sensorchange;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.frazew.virtualgyroscope.VirtualSensorListener;
import fr.frazew.virtualgyroscope.hooks.SensorChange;

import static android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH;

public class API18 extends XC_MethodReplacement {
    private static Class SYSTEM_SENSOR_MANAGER;
    private SensorChange mSensorChange;
    private XC_LoadPackage.LoadPackageParam lpparam;

    public API18(final XC_LoadPackage.LoadPackageParam lpparam) {
        super(XC_MethodReplacement.PRIORITY_HIGHEST);
        this.mSensorChange = new SensorChange();
        this.lpparam = lpparam;
        SYSTEM_SENSOR_MANAGER = XposedHelpers.findClass("android.hardware.SystemSensorManager", this.lpparam.classLoader);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object replaceHookedMethod(XC_MethodReplacement.MethodHookParam param) throws Throwable {
        Object listener = XposedHelpers.getObjectField(param.thisObject, "mListener");
        int handle = (int) param.args[0];
        Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
        SparseArray<Sensor> sensors = (SparseArray<Sensor>) XposedHelpers.getStaticObjectField(mgr.getClass(), "sHandleToSensor");
        Sensor s = sensors.get(handle);

        SparseBooleanArray mFirstEvent = (SparseBooleanArray) XposedHelpers.getObjectField(param.thisObject, "mFirstEvent");
        SparseIntArray mSensorAccuracies = (SparseIntArray) XposedHelpers.getObjectField(param.thisObject, "mSensorAccuracies");
        SparseArray<SensorEvent> mSensorsEvents = (SparseArray<SensorEvent>) XposedHelpers.getObjectField(param.thisObject, "mSensorsEvents");

        SensorEvent t = mSensorsEvents.get(handle);
        if (t == null) {
            return null;
        }

        boolean handled = false;
        if (listener instanceof VirtualSensorListener) {
            float[] values = this.mSensorChange.handleListener(s, (VirtualSensorListener) listener, ((float[]) param.args[1]).clone(), (int) param.args[2], (long) param.args[3]);
            if (values != null) {
                System.arraycopy(values, 0, t.values, 0, t.values.length);
                t.sensor = ((VirtualSensorListener) listener).getSensor();
                handled = true;
            }
        }
        if (!handled) {
            System.arraycopy((float[])param.args[1], 0, t.values, 0, t.values.length);
            t.sensor = s;
            switch (t.sensor.getType()) {
                // Only report accuracy for sensors that support it.
                case Sensor.TYPE_MAGNETIC_FIELD:
                case Sensor.TYPE_ORIENTATION:
                    // call onAccuracyChanged() only if the value changes
                    final int accuracy = mSensorAccuracies.get(handle);
                    if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                        mSensorAccuracies.put(handle, t.accuracy);
                        XposedHelpers.callMethod(listener, "onAccuracyChanged", t.sensor, t.accuracy);
                    }
                    break;
                default:
                    // For other sensors, just report the accuracy once
                    if (mFirstEvent.get(handle) == false) {
                        mFirstEvent.put(handle, true);
                        XposedHelpers.callMethod(listener, "onAccuracyChanged", t.sensor, SENSOR_STATUS_ACCURACY_HIGH);
                    }
                    break;
            }
        }

        t.timestamp = (long)param.args[3];
        t.accuracy = (int)param.args[3];
        XposedHelpers.callMethod(listener, "onSensorChanged", t);

        return null;
    }
}