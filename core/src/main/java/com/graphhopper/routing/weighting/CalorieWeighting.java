package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.Iterator;

public class CalorieWeighting extends AbstractWeighting {
    protected final static double SPEED_CONV = 3.6;
    private final double headingPenalty;
    private final long headingPenaltyMillis;
    private final double maxSpeed;


    public CalorieWeighting(FlagEncoder encoder, PMap map) {
        super(encoder);
        headingPenalty = map.getDouble(Routing.HEADING_PENALTY, Routing.DEFAULT_HEADING_PENALTY);
        headingPenaltyMillis = Math.round(headingPenalty * 1000);
        maxSpeed = encoder.getMaxSpeed() / SPEED_CONV;
    }

    public CalorieWeighting(FlagEncoder encoder) {
        this(encoder, new HintsMap(0));
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed;
    }

    // Temporary method
    public double getSpeedConv() {
        return SPEED_CONV;
    }

    public double calcElevationChange(EdgeIteratorState edge, boolean reverse) {
        PointList pl = edge.fetchWayGeometry(3);
        Iterator itr = pl.iterator();
        double firstElevation = 0;
        double secondElevation = 0;
        while(itr.hasNext()) {
            firstElevation = ((GHPoint3D)itr.next()).getElevation();
            secondElevation = ((GHPoint3D)itr.next()).getElevation();
        }
        if (reverse == false) return secondElevation - firstElevation;
        else return firstElevation - secondElevation;
    }

    public double calcDistance(EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(3);
        double distance = pl.calcDistance(new DistanceCalcEarth());
        return distance;
    }
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double speed = reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
        if (speed == 0)
            return Double.POSITIVE_INFINITY;

        double time = edge.getDistance() / speed * SPEED_CONV;

        // add direction penalties at start/stop/via points
        boolean unfavoredEdge = edge.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            time += headingPenalty;

        return time;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long time = 0;
        boolean unfavoredEdge = edgeState.getBool(EdgeIteratorState.K_UNFAVORED_EDGE, false);
        if (unfavoredEdge)
            time += headingPenaltyMillis;

        return time + super.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public String getName() {
        return "calorie";
    }
}
