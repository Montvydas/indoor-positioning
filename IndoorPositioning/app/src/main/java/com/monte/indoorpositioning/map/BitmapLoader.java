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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.monte.indoorpositioning.R;

/**
 * Created by monte on 04/04/2017.
 *
 * Singleton Class used to pre-load the images when the application starts. This saves up memory
 * resources and time as otherwise each image would have to be loaded multiple times whenever
 * another activity starts.
 *
 * After being pre-loaded, each image can be accessed and used within any of the applications
 */

public class BitmapLoader {
    private static BitmapLoader sInstance;                  // static singleton instance

    public BitmapDescriptor blueMarker;                     // Different markers are being provided by the API
    public BitmapDescriptor redMarker;                      // blue, red and green are used for different purposes
    public BitmapDescriptor greenMarker;

    public BitmapDescriptor pikachu;                        // pikachu marker is used to display it's secret location

    public BitmapDescriptor fleemingJenkinsFirstFloor;      // ground floor and first floor building overlays
    public BitmapDescriptor fleemingJenkinsGroundFoor;      // for displaying the accurate construction

    public BitmapDescriptor positionBackgroundOverlay;      // red dot, similarly to google blue dot, is used to
    public BitmapDescriptor positionMarker;                 // show the current users location indoors
    public BitmapDescriptor arrowMarker;                    // arrow shows the users facing direction

    private Context context;                                // context is required when accessing the resources

    public void setContext (Context context){
        this.context = context;
    }

    public static synchronized BitmapLoader getInstance() {
        // when getting the singleton instance, firstly create the class if one did not exist before
        if (sInstance == null) {
            sInstance = new BitmapLoader();
        }
        return sInstance;
    }

    public void loadMarkers (){
        // Several of the images have to be processed at the moment being to be able to put
        // them onto the screen. These include three different markers.
        blueMarker = BitmapDescriptorFactory.fromResource(R.drawable.blue_marker);
        redMarker = BitmapDescriptorFactory.fromResource(R.drawable.red_marker);
        greenMarker = BitmapDescriptorFactory.fromResource(R.drawable.green_marker);
    }

    public void loadPikachu(int size){
        // pikachu image needs to be scaled and then applied to the bitmap descriptor factory
        Bitmap pikachuMarker = BitmapFactory.decodeResource(context.getResources(), R.drawable.pikachu_small);
        Bitmap scaledPikachuMarker = Bitmap.createScaledBitmap(pikachuMarker, size, size, false);
        pikachu = BitmapDescriptorFactory.fromBitmap(scaledPikachuMarker);
    }

    public void loadBuildings(){
        // load both floors of the building
        fleemingJenkinsGroundFoor = BitmapDescriptorFactory.fromResource(R.drawable.fleeming_jenkins_ground_numbered);
        fleemingJenkinsFirstFloor = BitmapDescriptorFactory.fromResource(R.drawable.fleeming_jenkins_first);
    }

    public void loadLocationMarker (int markerSize, int arrowSize){
        // creating a red dot location marker requires actually creating 3 different markers
        // one is the red dot of the user location
        Bitmap positionMarkerBitmap=BitmapFactory.decodeResource(context.getResources(), R.drawable.red_dot);
        Bitmap scaledPositionMarkerBitmap = Bitmap.createScaledBitmap(positionMarkerBitmap, markerSize, markerSize, false);
        positionMarker = BitmapDescriptorFactory.fromBitmap(scaledPositionMarkerBitmap);
        // then there is the arrow showing the user facing direction from gyroscope
        Bitmap directionMarkerBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.red_dot_arrow);
        Bitmap scaledDirectionMarkerBitmap = Bitmap.createScaledBitmap(directionMarkerBitmap, arrowSize, arrowSize, false);
        arrowMarker = BitmapDescriptorFactory.fromBitmap(scaledDirectionMarkerBitmap);
        // finally it has a transparent overlay showing the calculated accuracy
        positionBackgroundOverlay = BitmapDescriptorFactory.fromResource(R.drawable.red_dot_background);
    }
}
