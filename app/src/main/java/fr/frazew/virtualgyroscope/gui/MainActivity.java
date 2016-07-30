package fr.frazew.virtualgyroscope.gui;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import de.robv.android.xposed.XposedBridge;
import fr.frazew.virtualgyroscope.BuildConfig;
import fr.frazew.virtualgyroscope.R;

public class MainActivity extends AppCompatActivity {
    private SensorManager sensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(getString(R.string.app_name), bm, getResources().getColor(R.color.cyan_900));
            setTaskDescription(taskDesc);
        }

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        TextView version = (TextView) findViewById(R.id.versionValue);
        version.setText(BuildConfig.VERSION_NAME);

        TextView accelerometer = (TextView) findViewById(R.id.accelerometerValue);
        accelerometer.setText(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ? "true" : "false");

        final TextView accelerometerValues = (TextView) findViewById(R.id.accelerometerValuesTextValues);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensorManager.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    String text = "";
                    for (int i = 0; i < event.values.length; i++) {
                        text += Math.round(event.values[i] * 100) / (float) 100;
                        if (i < event.values.length - 1) text += "; ";
                    }
                    accelerometerValues.setText(text);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            }, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        }

        TextView magnetic = (TextView) findViewById(R.id.magneticSensorValue);
        magnetic.setText(sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null ? "true" : "false");

        final TextView magneticValues = (TextView) findViewById(R.id.magneticValuesTextValues);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            sensorManager.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    String text = "";
                    for (int i = 0; i < event.values.length; i++) {
                        text += Math.round(event.values[i] * 100) / (float)100;
                        if (i < event.values.length - 1) text += "; ";
                    }
                    magneticValues.setText(text);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
