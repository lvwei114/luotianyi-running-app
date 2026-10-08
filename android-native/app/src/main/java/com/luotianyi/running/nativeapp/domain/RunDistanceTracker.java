package com.luotianyi.running.nativeapp.domain;

/** Android-free distance calculation, also exercised by the local Java regression test. */
public final class RunDistanceTracker {
    public static final long GAP_MS = 10000;
    public boolean running;
    public double distanceMeters, gpsMeters, estimatedMeters, strideLength = .7;
    public long steps, lastFixTime;
    public long gpsSteps;
    public double gpsStepMeters;
    public Double accuracy, speed;
    private long consumedSteps, lastInputTime, segmentStart;
    private long anchorSteps;
    private final boolean allowEstimation;
    private Point anchor, sample;

    private static final class Point {
        final double lat, lon;
        final long time;
        Point(double lat, double lon, long time) { this.lat = lat; this.lon = lon; this.time = time; }
    }
    public RunDistanceTracker(long now) { this(now, true); }
    public RunDistanceTracker(long now, boolean allowEstimation) { segmentStart = now; this.allowEstimation = allowEstimation; }
    public void setRunning(boolean value, long now) {
        if (running == value) return;
        if (!value) update(now);
        running = value; anchor = sample = null; speed = null;
        lastFixTime = 0; consumedSteps = steps; segmentStart = now;
    }
    public void step(long now) { if (running) { steps++; update(now); } }
    public void update(long now) {
        if (!allowEstimation || !running || now - (lastFixTime > 0 ? lastFixTime : segmentStart) < GAP_MS) return;
        double extra = Math.max(0, steps - consumedSteps) * Math.max(.3, Math.min(1.5, strideLength));
        distanceMeters += extra; estimatedMeters += extra; consumedSteps = steps;
    }
    public boolean fix(double lat, double lon, double precision, Double reportedSpeed, long time, long now) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return false;
        if (time <= lastInputTime || now - time > 15000 || time - now > 2000) return false;
        accuracy = Double.isFinite(precision) && precision > 0 ? precision : null;
        if (accuracy == null || accuracy > 50) return false;
        Point point = new Point(lat, lon, time);
        if (running && sample != null) {
            double dt = (time - sample.time) / 1000.0;
            if (dt < .2) return false;
            if (dt < GAP_MS / 1000.0 && meters(sample, point) > 8.5 * dt + Math.min(6, accuracy * .2)) return false;
        }
        lastInputTime = time;
        boolean gap = now - (lastFixTime > 0 ? lastFixTime : segmentStart) >= GAP_MS;
        update(now);
        lastFixTime = time;
        if (!running) { anchor = sample = null; return true; }
        if (gap || (sample != null && time - sample.time >= GAP_MS)) anchor = sample = null;
        consumedSteps = steps;
        speed = reportedSpeed != null && Double.isFinite(reportedSpeed) && reportedSpeed >= 0 && reportedSpeed <= 8.5 ? reportedSpeed : null;
        sample = point;
        if (speed != null && speed < .35) { anchor = point; anchorSteps = steps; speed = 0.0; return true; }
        if (anchor == null) { anchor = point; anchorSteps = steps; return true; }
        double distance = meters(anchor, point);
        if (distance >= Math.max(2, Math.min(6, accuracy * .15))) {
            distanceMeters += distance; gpsMeters += distance; anchor = point;
            long pairedSteps = Math.max(0, steps - anchorSteps);
            if (pairedSteps > 0) { gpsSteps += pairedSteps; gpsStepMeters += distance; }
            anchorSteps = steps;
        }
        return true;
    }
    public boolean fresh(long now) { return lastFixTime > 0 && now - lastFixTime < GAP_MS; }
    private static double meters(Point a, Point b) {
        double lat = Math.toRadians(b.lat - a.lat), lon = Math.toRadians(b.lon - a.lon);
        double value = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.pow(Math.sin(lon / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, value)));
    }
}
