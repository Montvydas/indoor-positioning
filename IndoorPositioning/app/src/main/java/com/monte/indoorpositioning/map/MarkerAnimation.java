package com.monte.indoorpositioning.map;

/* Copyright 2013 Google Inc.
   Licensed under Apache 2.0: http://www.apache.org/licenses/LICENSE-2.0.html */

        import android.animation.ObjectAnimator;
        import android.animation.TypeEvaluator;
        import android.animation.ValueAnimator;
        import android.annotation.TargetApi;
        import android.os.Build;
        import android.os.Handler;
        import android.os.SystemClock;
        import android.util.Property;
        import android.view.animation.AccelerateDecelerateInterpolator;
        import android.view.animation.Interpolator;

        import com.google.android.gms.maps.model.GroundOverlay;
        import com.google.android.gms.maps.model.LatLng;
        import com.google.android.gms.maps.model.Marker;
        import com.monte.indoorpositioning.LatLngInterpolator;

/**
 * This class is used to animate objects on the map (markers and ground overlays). All methods
 * regarding the marker animation could be taken from Google Inc. (https://gist.github.com/broady/6314689)
 * and ground overlay animation adoption was created according to the given examples. Only one
 * animation is used, animateMarkerToHC, thus the rest were deleted.
 */
public class MarkerAnimation {

    public static ValueAnimator valueAnimator = new ValueAnimator();
    /**
     * Animate marker to go from one location to another smoothly.
     *
     * @param marker
     * @param finalPosition
     * @param latLngInterpolator
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    static void animateMarkerToHC(final Marker marker, final LatLng finalPosition, final LatLngInterpolator latLngInterpolator) {
        // Get current position of the marker
        final LatLng startPosition = marker.getPosition();

        // Create value animator
        valueAnimator = new ValueAnimator();
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = animation.getAnimatedFraction();
                // interpolate using interpolator
                LatLng newPosition = latLngInterpolator.interpolate(v, startPosition, finalPosition);
                // and set the new location for the marker
                marker.setPosition(newPosition);
            }
        });
        // set duration and start animation
        valueAnimator.setFloatValues(0, 1); // Ignored.
        valueAnimator.setDuration(300);
        valueAnimator.start();
    }

    /**
     * Animate the ground overlay to move from one location to another smoothly.
     *
     * @param overlay
     * @param finalPosition
     * @param latLngInterpolator
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    static void animateGroundOverlayToHC(final GroundOverlay overlay, final LatLng finalPosition, final LatLngInterpolator latLngInterpolator) {
        // Get current position of the ground overlay.
        final LatLng startPosition = overlay.getPosition();

        // Create value animator
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = animation.getAnimatedFraction();
                // interpolate using interpolator
                LatLng newPosition = latLngInterpolator.interpolate(v, startPosition, finalPosition);
                // and set the new location for the ground overlay
                overlay.setPosition(newPosition);
            }
        });
        // set duration and start animation
        valueAnimator.setFloatValues(0, 1); // Ignored.
        valueAnimator.setDuration(300);
        valueAnimator.start();
    }
}