package com.example.websocket;

public class NavPvtData {
    public String time;
    public double latitude;
    public double longitude;
    public double heightEllipsoid;
    public double heightMsl;
    public double horizontalAccuracy;
    public double verticalAccuracy;
    public String fixType;

    public NavPvtData(String time, double latitude, double longitude, double heightEllipsoid, double heightMsl,
                      double horizontalAccuracy, double verticalAccuracy, String fixType) {
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.heightEllipsoid = heightEllipsoid;
        this.heightMsl = heightMsl;
        this.horizontalAccuracy = horizontalAccuracy;
        this.verticalAccuracy = verticalAccuracy;
        this.fixType = fixType;
    }
}
