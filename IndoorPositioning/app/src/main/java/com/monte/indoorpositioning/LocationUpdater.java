package com.monte.indoorpositioning;
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
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Created by monte on 27/03/2017.
 *
 * A class used to access the current location of the user using GPS + Wifi. This is helps
 * to access and start scans for user location thus is being used in other classes. It
 * implements some other helpful funstino such as checking if GPS is turned on.
 */
public class LocationUpdater implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private Context context;            //Context of the activity
    GoogleApiClient mGoogleApiClient;   // Google Maps client
    private Location currentLocation;   // current location of the user

    // location task change listener to access the location of the user from the activity
    private LocationTaskListener taskListener;

    /**
     * Constructor requires adding the location updater and context
     * @param context
     * @param taskListener
     */
    public LocationUpdater(Context context, LocationTaskListener taskListener) {
        this.context = context;
        this.taskListener = taskListener;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // call the task listeners method
        if (taskListener != null){
            taskListener.onConnected();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        // when location was updated, update the current location and then call the function for that
        currentLocation = location;
        if (taskListener != null) {
            taskListener.onLocationUpdated(location);
        }
    }

    /**
     * Location updates are being started with specific parameters such as update interval
     */
    public void startLocationUpdates() {
        if (mGoogleApiClient != null){
            if (mGoogleApiClient.isConnected()){
                LocationRequest mLocationRequest = new LocationRequest();   //create location request object
                mLocationRequest.setInterval(5000);                        //specify request interval
                mLocationRequest.setFastestInterval(5000);                  //request location updates every 5 seconds at fastest
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);   //force to use GPS location for the best accuracy

                //check if the user gave the permission to access the device location

                //check again if the client was successfully created
                //and then request location to be updated
                if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            }
        }
    }

    /**
     * Location provider - GoogleApiClient is created
     */
    public void createLocationProvider() {
        //Allows an app to access precise location, thus ask for permissions for that
        if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

        } else {
            //If permissions are already given
            //Create an instance of GoogleAPIClient
            if (mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(this.context)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
            }
        }
    }

    //the method removes location updates
    public void stopLocationUpdates() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    public void connectLocationProvider (){
        if (mGoogleApiClient != null) {   //start location service by connecting to it
            mGoogleApiClient.connect();
        }
    }

    public void disconnectLocationProvider (){
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();  //then app stops, disconnect from google api client
        }
    }

    /**
     * can get the current location without the need of implementing the listener at all.
     * @return
     */
    public Location getCurrentLocation() {
        return currentLocation;
    }

    public GoogleApiClient getmGoogleApiClient() {
        return mGoogleApiClient;
    }

    /**
     * check if gps is turned off or on.
     * @param context
     * @return
     */
    public static boolean getGPSStatus (Context context){
        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /**
     * task listener methods
     */
    public interface LocationTaskListener {
        void onLocationUpdated(Location location);
        void onConnected();
    }
}
