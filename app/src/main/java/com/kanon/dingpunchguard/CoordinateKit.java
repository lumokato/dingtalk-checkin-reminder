package com.kanon.dingpunchguard;

import android.location.Location;

final class CoordinateKit {
    private static final double PI = 3.1415926535897932384626d;
    private static final double A = 6378245.0d;
    private static final double EE = 0.00669342162296594323d;

    private CoordinateKit() {
    }

    static double[] gcj02ToWgs84(double lat, double lon) {
        if (outsideChina(lat, lon)) {
            return new double[]{lat, lon};
        }
        double dLat = transformLat(lon - 105.0d, lat - 35.0d);
        double dLon = transformLon(lon - 105.0d, lat - 35.0d);
        double radLat = lat / 180.0d * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0d) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0d) / (A / sqrtMagic * Math.cos(radLat) * PI);
        double mgLat = lat + dLat;
        double mgLon = lon + dLon;
        return new double[]{lat * 2 - mgLat, lon * 2 - mgLon};
    }

    static float distanceMetersFromWgsToGcjTarget(
            double currentWgsLat,
            double currentWgsLon,
            double targetGcjLat,
            double targetGcjLon
    ) {
        double[] targetWgs = gcj02ToWgs84(targetGcjLat, targetGcjLon);
        float[] results = new float[1];
        Location.distanceBetween(
                currentWgsLat,
                currentWgsLon,
                targetWgs[0],
                targetWgs[1],
                results
        );
        return results[0];
    }

    private static boolean outsideChina(double lat, double lon) {
        return lon < 72.004d || lon > 137.8347d || lat < 0.8293d || lat > 55.8271d;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0d + 2.0d * x + 3.0d * y + 0.2d * y * y + 0.1d * x * y + 0.2d * Math.sqrt(Math.abs(x));
        ret += (20.0d * Math.sin(6.0d * x * PI) + 20.0d * Math.sin(2.0d * x * PI)) * 2.0d / 3.0d;
        ret += (20.0d * Math.sin(y * PI) + 40.0d * Math.sin(y / 3.0d * PI)) * 2.0d / 3.0d;
        ret += (160.0d * Math.sin(y / 12.0d * PI) + 320.0d * Math.sin(y * PI / 30.0d)) * 2.0d / 3.0d;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0d + x + 2.0d * y + 0.1d * x * x + 0.1d * x * y + 0.1d * Math.sqrt(Math.abs(x));
        ret += (20.0d * Math.sin(6.0d * x * PI) + 20.0d * Math.sin(2.0d * x * PI)) * 2.0d / 3.0d;
        ret += (20.0d * Math.sin(x * PI) + 40.0d * Math.sin(x / 3.0d * PI)) * 2.0d / 3.0d;
        ret += (150.0d * Math.sin(x / 12.0d * PI) + 300.0d * Math.sin(x / 30.0d * PI)) * 2.0d / 3.0d;
        return ret;
    }
}
