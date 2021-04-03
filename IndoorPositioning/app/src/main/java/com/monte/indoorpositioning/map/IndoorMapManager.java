package com.monte.indoorpositioning.map;

/*
    MIT License

    Copyright (c) 2017 Montvydas

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
 */

import android.animation.ValueAnimator;
import android.content.Context;
import android.location.Location;
import android.media.MediaPlayer;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.monte.indoorpositioning.LatLngInterpolator;
import com.monte.indoorpositioning.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by monte on 27/03/2017.
 *
 * A class used to manage indoor maps. It stores information about the indoor maps, draws markers
 * on the screen, and allows some more options for the user than the basic GoogleMaps object.
 * It's like a Maps helper and stores several of the functions to modify the google maps.
 *
 * Additionally it manages the Pokemon class. It is responsible for pokemon
 */
public class IndoorMapManager implements GoogleMap.OnMarkerClickListener{
    GoogleMap map;                                                                  // Instance of a map
    public GroundOverlay fleemingJenkinsGroundOverlay;                              // ground floor map
    public GroundOverlay fleemingJenkinsFirstOverlay;                               // First floor map
    private ValueAnimator groundOverlayAnimator = ValueAnimator.ofFloat(0f, 1f);    // animator used to switch between floors

    private Marker mPositionMarker;                         // Red dot on the screen denoting the users location
    private Marker mDirectionMarker;                        // Arrow next to the position marker denoting the direction the user is facing to
    private GroundOverlay mBackgroundOverlay;               // transparent red background denoting the accuracy of the location

    private OnMapManagerListener mapListener;               // Listener to tell when map was updated

    private Context context;                                // Context passed from the activity

    private boolean lockCamera = false;                      // Set to true when the main camera is following the red dot

    // Variables used when drawing the marker grid
    private LatLng firstGridMarker;                         // top-left marker
    private LatLng secondGridMarker;                        // top-right marker
    private boolean isGeneratingGrid = false;               // variable is set to true when grid is to be generated
    private boolean isFirstGridMarkerSelected = false;      // set to true after the first marker is put on a map
    private boolean isSecondGridMarkerSelected = false;     // set to true after the second marker is put on a map

    private int foundPikachu = 0;                       // The number tells how many pikachus were captured
    private AnimalManager mAnimalManager;               // manages pokemon pokemons on the map and their capturing
    /**
     * Constructor of the IndoorMapManager instance. Need to pass the existing google maps
     * and the context which will allow to and and modify images on the google maps.
     * @param map
     * @param context
     */
    public IndoorMapManager(GoogleMap map, Context context) {
        this.map = map;
        this.context = context;
    }

    /**
     * Add animals to the map for the game. At the moment will only add pikachu at one single location.
     */
    public void addAnimalsToMap (){
        // create animal map manager if null
        if (mAnimalManager == null) {
            mAnimalManager = new AnimalManager(IndoorMapManager.this);

        }
        this.map.setOnMarkerClickListener(this);
        mAnimalManager.addPikachu();
    }

    /**
     * Function for putting the indoor ground overlay over the google maps.
     * It required processing the indoor map and setting that as a ground Overlay.
     */
    public void setupMapOverlays(){
        // Ground floor indoor map
        fleemingJenkinsGroundOverlay = map.addGroundOverlay(new GroundOverlayOptions()
                .image(BitmapLoader.getInstance().fleemingJenkinsGroundFoor)   // Bitmap
                .bearing(57)                                        // rotation of the image
                .position(new LatLng(55.922684, -3.172951), 94.2f)  //top left corner is reference
                .anchor(0.018f, 0.8186f)                            // Set the anchor to be bottom-left edge
                .transparency(0f));                                 // fully visible
        // First floor indoor map
        fleemingJenkinsFirstOverlay = map.addGroundOverlay(new GroundOverlayOptions()
                .image(BitmapLoader.getInstance().fleemingJenkinsFirstFloor) // Bitmap
                .bearing(57)                                        // rotation of the image
                .position(new LatLng(55.922684, -3.172951), 79.3f)  //top left corner is reference
                .anchor(0.0f, 0.8927f)                              // Set the anchor to be bottom-left edge
                .transparency(1f));                                 // fully transparent, thus invisible
    }


    /**
     * Animated the ground overlay. This is done by hiding one overlay and then showing another
     * in a smooth manner.
     * @param out
     * @param in
     */
    public void animateGroundOverlay(final GroundOverlay out, final GroundOverlay in){
        // We need value animator which will change from 0 to 1
        groundOverlayAnimator = ValueAnimator.ofFloat(0f, 1f);
        // with a duration of half a second
        groundOverlayAnimator.setDuration(500);
        groundOverlayAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // One overlay transparency is set to animated Value
                // and another to 1 - animatedValue. This created a nice effect like we're
                // changing floors
                float animatedValue = (float) animation.getAnimatedValue();
                out.setTransparency(animatedValue);
                in.setTransparency(1 - animatedValue);
            }
        });
        groundOverlayAnimator.start();
    }

    /**
     * Ground overlay animator for hiding and showing again the ground and first floors indoor maps.
     * @return
     */
    public ValueAnimator getGroundOverlayAnimator() {
        return groundOverlayAnimator;
    }


    /**
     * Function for generating marker grid for automatic building scanning. This is useful
     * because u then dont need to generate markers manually. For that u need to select 3 points
     * in space when map bearing is 0 degrees.
     * @param topLeft
     * @param topRight
     * @param bottomLeft
     * @param marker
     */
    public void generateMarkerGrid (LatLng topLeft, LatLng topRight, LatLng bottomLeft, BitmapDescriptor marker) {
        // Need to find the horizontal bearing / angle of the generating grid
        double horizontalAngle = Math.atan2(topRight.latitude - topLeft.latitude,
                topRight.longitude - topLeft.longitude);
        // Need to find the vertical bearing / angle of the generating grid
        double verticalAngle = Math.atan2(topLeft.longitude - bottomLeft.longitude,
                topLeft.latitude - bottomLeft.latitude);

        // Step size for the markers will depend on the angle of rotation, otherwise
        // step size will be inconsistent and will depend on the angle, which is bad...

        double incrH = 0.000035*(1-Math.abs(Math.sin(horizontalAngle)));
//        double incrH = 0.000035*Math.cos(horizontalAngle);
        double incrV = 0.00002*Math.cos(verticalAngle);

        // all markers will be stored in a list
        List<Marker> markerList = new ArrayList<>();

        // Go incrementally from left to right and from top to bottom
        for (double h = topLeft.longitude; h < topRight.longitude+incrH; h += incrH){//0.00004
            for (double v = topLeft.latitude; v > bottomLeft.latitude-incrV; v -= incrV){//0.000025
                // Calculate the latitude and longitude of the marker to be drawn
                double dv = Math.tan(horizontalAngle)*(h-topLeft.longitude);
                double dh = Math.tan(verticalAngle)*(v-topLeft.latitude);
                // And the draw that marker and store it in a list
                markerList.add(addMarker(new LatLng(v + dv, h + dh), marker));
            }
        }
        // When grid is generated, call a listener and pass the marker list
        if (mapListener != null) {
            mapListener.onGridDrawFinish(markerList);
        }
    }

    /**
     * Functions used to add a marker to the from the location and the bitmap.
     * Less writing that the provided Google approach.
     * @param latLng
     * @param marker
     * @return
     */
    public Marker addMarker(LatLng latLng, BitmapDescriptor marker){
        Marker m = map.addMarker(new MarkerOptions()
                .position(latLng)           // define the location of the marker
                .draggable(true)            // Allow dragging the markers
                .anchor(0.5f, 0.5f)         // set the anchor to be the center
                .icon(marker));             // set the icon
        return m;
    }

    /**
     * Function is being periodically called to check if the user added new markers or not yet.
     * @param latLng
     */
    public void checkIfGenerateGrid(LatLng latLng){
        // If generating the grid
        if (isGeneratingGrid){
            // If first marker is not yet selected
            if (!isFirstGridMarkerSelected){
                // get first marker location and set the boolean value to true
                firstGridMarker = latLng;
                isFirstGridMarkerSelected = true;
                Toast.makeText(context, "Set Top-Right Marker.", Toast.LENGTH_LONG).show();

                //if the second marker wasn;t yet selected
            } else if (!isSecondGridMarkerSelected) {
                // get second marker location
                secondGridMarker = latLng;
                //set the boolean value to true
                isSecondGridMarkerSelected= true;
                Toast.makeText(context, "Set Bottom-Left Marker.", Toast.LENGTH_LONG).show();
            } else {
                // finally we can generate a grid having 3 points in space
                generateMarkerGrid(firstGridMarker, secondGridMarker, latLng, BitmapLoader.getInstance().blueMarker);
                calcelRequestGridCoordinates();     // We can start from the beginning
            }
        }
    }

    /**
     * When requesting a grid to be drawn, a variable.
     */
    public void requestGridCoordinates(){
        isGeneratingGrid = true;
    }

    /**
     * Function could be used if used wanted to no more draw the frid coordintes.
     */
    public void calcelRequestGridCoordinates(){
        // To do that several variables have to be set to false
        isGeneratingGrid = false;
        isFirstGridMarkerSelected = false;
        isSecondGridMarkerSelected = false;
    }

    /**
     * Functions created a custom red dot showing the user location with the ground overlay showing the accuracy
     * and the direction arrow pointing where the user is turning to.
     * @param accuracySize
     * @param latLng
     */
    public void createCurrentPositionMarker(int accuracySize, LatLng latLng){
        // Position marker is simply a red dot with a white circle around it. Need to scale
        // the bitmap to make sure it's not too big. Also disable dragging obviously.
        mPositionMarker = addMarker(latLng, BitmapLoader.getInstance().positionMarker);
        mPositionMarker.setDraggable(false);

        // Direction marker is simply a red arrow . Need to scale the bitmap to make sure it's not
        // too big. Also disable dragging obviously.
        mDirectionMarker = addMarker(latLng, BitmapLoader.getInstance().arrowMarker);
        mDirectionMarker.setDraggable(false);

        // Markers fo not change their size when zoomed in but ground overlay does. Thus ground
        // overlay is used to display the transparent red circle which tells the accuracy of the
        // predicted location.
        mBackgroundOverlay = map.addGroundOverlay(new GroundOverlayOptions()
                .image(BitmapLoader.getInstance().positionBackgroundOverlay)
                .position(latLng, accuracySize)
                .anchor(0.5f, 0.5f));
    }

    /**
     * Red dot/circle is can be either hidden or visible.
     * @param state
     */
    public void setCurrentPositionVisible (boolean state){
        // to achieve the functionality three different objects have to be set at once
        if (mPositionMarker != null) {
            mPositionMarker.setVisible(state);
            mDirectionMarker.setVisible(state);
            mBackgroundOverlay.setVisible(state);
        }
    }

    /**
     * Moves the current red dot position to the specified location.
     * @param latLng
     */
    public void moveCurrentPosition(LatLng latLng){
        // to achieve the functionality three different objects have to be moved at once
        // They are moved with an animation which smooths the movement
        if (mPositionMarker != null) {
            MarkerAnimation.animateMarkerToHC(mPositionMarker, latLng, new LatLngInterpolator.Linear());
            MarkerAnimation.animateMarkerToHC(mDirectionMarker, latLng, new LatLngInterpolator.Linear());
            MarkerAnimation.animateGroundOverlayToHC(mBackgroundOverlay, latLng, new LatLngInterpolator.Linear());

            // check if pikachu is close or not
            Location loc = new Location("curr");
            loc.setLatitude(latLng.latitude);
            loc.setLongitude(latLng.longitude);

            // set pikachu visible is user is close, otherwise invisible
            mAnimalManager.setPikachuVisibleIfClose(loc);
        }
        // If camera is locked, then move camera to put the red dot in a center
        if (lockCamera) {
            map.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    /**
     * Method to lock the camera to the red dot.
     * @param status
     */
    public void setLockCamera(boolean status){
        // sets the lockCamera status for the future camera animations
        lockCamera = status;
        if (lockCamera){
            // And then moves the camera to the current red dot location if needed
            map.animateCamera(CameraUpdateFactory.newLatLng(getCurrentPosition()));
        }
    }

    /**
     * Rotates the direction marker, which is attached to the red dot accordingly.
     * @param degrees
     */
    public void setCurrentPositionDirection(float degrees){
        // make sure the marker is not null
        if (mDirectionMarker != null) {
            // simply set the rotation
            mDirectionMarker.setRotation(degrees);
        }
    }

    public void setLocationAccuracy(float accuracy){
        if (mBackgroundOverlay != null) {
            mBackgroundOverlay.setDimensions(accuracy);
        }
    }

    /**
     * Used to get the current position of the red dot marker
     * @return
     */
    public LatLng getCurrentPosition(){
        if (mPositionMarker != null) {
            return mPositionMarker.getPosition();
        } else {
            return map.getCameraPosition().target;
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        // when a marker is selected, check that it is a pikachu marker
        if (mAnimalManager.isPikachuSelected(marker)){
            foundPikachu++;                 // increment the found pikachus
            // display the toast telling how many pikachus were found
            Toast.makeText(context, "You found " + foundPikachu + "/5 Pikachuuus!", Toast.LENGTH_SHORT).show();
            // start playing pikachu sound
            final MediaPlayer mp = MediaPlayer.create(context, R.raw.pikachu);
            mp.start();
        }
        // by returning true the camera wont focus on the marker
        return true;
    }

    /**
     * Check when grid drawing was finished and return all associated markers with it.
     */
    public interface OnMapManagerListener {
        void onGridDrawFinish(List<Marker> markerList);
    }

    /**
     * Create a method to set the listener from the activity.
     * @param mapListener
     */
    public void setOnMapManagerListener(OnMapManagerListener mapListener){
        this.mapListener = mapListener;
    }
}
