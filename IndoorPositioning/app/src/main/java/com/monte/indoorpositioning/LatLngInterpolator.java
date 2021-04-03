package com.monte.indoorpositioning;
/* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html */

import com.google.android.gms.maps.model.LatLng;

import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;
/* */
/**
 * Class responsible for calculating the interpolated latitude/longitude.
 * This is used when animating the markers and groundoverlays in the moving map.
 * On more how to use it look into MarkerAnimation and IndoorMapManager classes.
 * Function was imported from google github.com/googlemaps/android-maps-utils.
 * More interpolators were provided but were not used thus were deleted.
 */
public interface LatLngInterpolator {
    /**
     * interface of the interpolator.
     * @param fraction
     * @param a
     * @param b
     * @return
     */
    public LatLng interpolate(float fraction, LatLng a, LatLng b);

    /**
     * Linear interpolator class. It required the fraction of the animation, start location and
     * final location and then it generates the location in between for the animated marker/ground overlay.
     */
    public class Linear implements LatLngInterpolator {
        @Override
        public LatLng interpolate(float fraction, LatLng a, LatLng b) {
            // 1) get the total distance between locations
            // 2) multiply that by the animated fraction which changes from 0 to 1
            // 3) add the start location and return the calculated location for the given values
            double lat = (b.latitude - a.latitude) * fraction + a.latitude;
            double lng = (b.longitude - a.longitude) * fraction + a.longitude;
            return new LatLng(lat, lng);
        }
    }
}