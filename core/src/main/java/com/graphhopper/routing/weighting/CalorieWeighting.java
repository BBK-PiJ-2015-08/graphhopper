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


    public double calcElevationChange(EdgeIteratorState edge, boolean reverse) {
        PointList pl = edge.fetchWayGeometry(3);
        double firstElevation = pl.getElevation(0);
        double secondElevation = pl.getElevation(1);
        if (reverse == false) {
            if (Double.isNaN(secondElevation - firstElevation)) {
                throw new IllegalArgumentException("calcElevationChange should not return NaN");
            }
            return secondElevation - firstElevation;
        } else {
            if (Double.isNaN(firstElevation - secondElevation)) {
                throw new IllegalArgumentException("calcElevationChange should not return NaN");
            }
            return firstElevation - secondElevation;
        }
    }

    public double calcDistance(EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(3);
        double distance = pl.calcDistance(new DistanceCalcEarth());
        return distance;
    }

    public double calcPercentGrade(EdgeIteratorState edge, boolean reverse) {
        double elevationChange = calcElevationChange(edge, reverse);
        if (Double.isNaN(elevationChange)) {
            throw new IllegalArgumentException("elevationChange param should not be NaN");
        }
        double distance = calcDistance(edge);
        if (Double.isNaN(distance)) {
            throw new IllegalArgumentException("distance param should not be NaN");
        }
        if (distance == 0) {
            return 0;
        }
        double percentGrade = elevationChange / distance * 100;
        if (percentGrade < -8.0) return -8.0;
        else return percentGrade;
    }

    public double calcWalkingVelocity(EdgeIteratorState edge, boolean reverse) {
        double percentGrade = calcPercentGrade(edge, reverse);
        double velocity = ((6*Math.exp(-3.5 * ((percentGrade*0.01) + 0.05)) * 1000) / 60 / 60);
        return velocity;
    }

    public double calcExactTimeInSeconds(EdgeIteratorState edge, boolean reverse) {
        double distance = calcDistance(edge);
        double velocity = calcWalkingVelocity(edge, reverse);
        double exactTime = distance / velocity;
        return exactTime;
    }

    public double calcMR(EdgeIteratorState edge, boolean reverse) {
        double weight = 80;
        double load = 0;
        double terrain = 1.0;
        double C = 0;
        double percentGrade = calcPercentGrade(edge, reverse);
        double velocity = calcWalkingVelocity(edge, reverse);
        if (percentGrade < 0) {
            C = calcC(weight, load, percentGrade, velocity);
        }
        double M = (((1.5 * weight) + ((2 * (weight + load))) *  ((load / weight) * (load / weight)))) + (terrain * (weight + load)) * (((1.5 * velocity) * (1.5 * velocity)) + (0.35 * (velocity * percentGrade)));
        double MR = M - C;

        double SMR = calcSMR();
    public double calcSMR() {
        double height = 165;
        double weight = 80;
        boolean female = true;
        double age = 25;
        double BMR;
        if (female) {
            BMR = 655 + (9.6 * weight) + (1.7 * height) - (4.7 * age);
        } else {
            BMR = 66 + (13.7 * weight) + (5 * height) - (6.8 * age);
        }
        double SMR = 1.2 * BMR;
        if (SMR > MR) {
            return SMR;
        } else {
            return MR;
        }
    }

    public double calcC(double weight, double load, double percentGrade, double velocity) {
        double C = 1 * (((-percentGrade * (weight + load) * velocity)/3.5) - (((weight + load) * ((-percentGrade + 6)*(-percentGrade + 6))) /weight) + (25 - (velocity * velocity)));
        return C;
    }

    public double calcKcal(EdgeIteratorState edge, boolean reverse) {
        double MR = calcMR(edge, reverse);
        double exactTime = calcExactTimeInSeconds(edge, reverse);
        double kcal = MR * exactTime / 4184;
        return kcal;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        return calcKcal(edge, reverse);
    }

    @Override
    public String getName() {
        return "calorie";
    }
}
