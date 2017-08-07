package fr.frazew.virtualgyroscope;

public class SensorModel {
    public String name;
    public int handle;
    public float resolution;
    public int minDelay;
    public float maxRange;
    public boolean isAlreadyNative = false;
    public String stringType;
    public String permission;

    public SensorModel(int sensorType, String name, int handle, float resolution, int minDelay, float maxRange, String stringType, String permission) {
        this.name = name;
        this.handle = handle;
        this.resolution = resolution;
        this.minDelay = minDelay;
        this.maxRange = maxRange;
        this.permission = permission;
        this.stringType = stringType;
    }
}
