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
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.monte.indoorpositioning.database.IndoorDatabaseHelper;
import com.monte.indoorpositioning.database.IndoorLocation;
import com.monte.indoorpositioning.database.IndoorSignal;

import com.monte.indoorpositioning.map.*;
import com.monte.indoorpositioning.database.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by monte on 23/03/2017.
 *
 * Training activity is responsible for collecting the Wifi data and storing that into the database. It
 * uses markers on the map which can be easily generated on the map. Different data is being stored,
 * such as the building, room, floor level, latitude and longitude.
 *
 * The activity works in these steps:
 * 1) It initialises all of the basic views such as buttons, seekBars etc.
 * 2) It reads the database information and parses that into an easily readable Map structure (for cache - quicker access)
 * 3) (EXTRA FEATURE) It then setups the Map and floor plans of the ground and the first floors of the Fleeming Jenkins Building.
 * 4) Now the storing of the data can begin. The user selects the floor number using the buttons on the bottom-right.
 * He then enters the building and the room information about the location in a form of "building/room"
 * in the provided text field on the top of the screen. He then selects how many Wifi scans does he want
 * to perform in the location on the center-right side. Finally he selects a place on the map of where
 * he is at the moment, which generates a marker. A marker is then pressed again to select it. Press
 * button "save!" to save the location in the database.
 * 5) The saved information about the location can be viewed by selecting the marker again and then
 * pressing the button at the bottom-left side of the screen, which should show "T". If the marker
 * doesn't have any information associated with it the button will show "F" and will not display any info.
 *
 * 6) (EXTRA FEATURE) Because generating markers is cumbersome, an function was provided to automatically
 * generate markers in the room. Press button "GRID", and then select 3 markers on the map: top-left,
 * top-right and bottom-left and the markers will be automatically generated for you.
 * 7) You can clear all of the markers from the map by pressing the button "CLEAR" (This only clears the from the
 * map view and not from the database)
 * 8) When you press the button "DB" (database) all of the markers from the database will be put on the screen.
 * This allows seeing the information but it also allows to updated the information with a new one.
 * 9) To delete a single marker from the database firstly select it and then press "SINGLE DB" black button
 * which will then delete that information from the database.
 * 10) To delete all of the database information press "CLEAR DB". It then asks you for the password,
 * which can be found in Constants.DELETE_PASSWORD field = "2468". Without the password you won't be able to delete info.
 * 11) To export the database into the phone memory press "EXPORT!". It then asks you for the filename.
 * Filename has to be a different from the ones already existing. Database is stored into folder "IndoorDatabase".
 * 12) The existing database can be imported by from the phone. Press the button "IMPORT!" and
 * File Chooser file open up where you can find your database to be imported. Then simply click on a file
 * and if asked, import it as a "File Way".
 */

public class TrainingActivity extends FragmentActivity implements OnMapReadyCallback,
        LocationUpdater.LocationTaskListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMapLongClickListener, IndoorMapManager.OnMapManagerListener, WifiCollector.OnWifiCollectorListener,
        GoogleMap.OnMarkerDragListener{

    private GoogleMap mMap;                         // Google maps instance used to control outdoor maps
    private LocationUpdater mLocationUpdater;       // TODO delete this one
    private Button selectedFloorButton;             // currently selected floor button
    private EditText locationEditText;              // edit text field where user enters the building/room
    private WifiCollector mWifiCollector;           // wifi collector instance used to collect wifi and process it
    private Button showInfoDialogButton;            // button which is used to show more information about the location
    private List<Marker> markerList = new ArrayList<>();// All collected markers on the map are stored in a list
    private int selectedFloor = 0;                  // Selected floor for data collection
    private Marker selectedMarker;                  // Selected marker on the map
    private IndoorMapManager mIndoorMapManager;     // Instance iof Indoor map manager which manages indoor maps

    // Processed and sorted info from the database is stored in the given map structure
    private Map<IndoorLocation, List<ProcessedSignal>> processedMap = new HashMap<>();

    // Constant when calling activity for result when finding the database file
    private static final int READ_REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); //screen always portrait
        Constants.setStatusBarTranslucent(true, getWindow());                   // status bar transparent
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // screen is always on

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // setup the basic views such as buttons etc
        setupViews();

        // Wifi collector instance used to collect and process wifi signals
        mWifiCollector = new WifiCollector(this);
        mWifiCollector.setOnWifiCollectorListener(this);

        // TODO delete this
        initialiseLocationUpdater();
    }

    /**
     * buttons, text and other things are being initialised here.
     */
    private void setupViews(){
        selectedFloorButton = (Button) findViewById(R.id.ground_floor_button);
        selectedSampleButton = (Button) findViewById(R.id.five_sample_button);
        locationEditText = (EditText) findViewById(R.id.location_edit_text);
        showInfoDialogButton = (Button) findViewById(R.id.show_info_dialog_button);
        showInfoDialogButton.setEnabled(false);
        showInfoDialogButton.setText("F");
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // get the instance of google maps
        mMap = googleMap;
        // because status bar translucent, everything is shifted, shift back
        mMap.setPadding(0, 70, 0, 0);
        // When marker is being pressed, it opens up a menu, disable this function
        mMap.getUiSettings().setMapToolbarEnabled(false);

        // Setup some map listeners such as on map clicked, on marker clicked etc.
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapLongClickListener(this);
        mMap.setOnMarkerDragListener(this);

        // Move the camera to Kings buildings - the location where training is performed
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(55.922082, -3.172315), 18.5f));

        // Create an instance of indoor map manager, which manages indoor maps and the red dot
        mIndoorMapManager = new IndoorMapManager(mMap, this);
        // setup ground overlays for the map
        mIndoorMapManager.setupMapOverlays();
        // set a listener
        mIndoorMapManager.setOnMapManagerListener(this);

        //allow showing the current location with a blue dot
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // show Google Blue dot  of the user location
        mMap.setMyLocationEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // check that gps is actually turned on before scanning
        if (!LocationUpdater.getGPSStatus(this)){
            // display a message to enable gps and go out of the activity
            Toast.makeText(this, "Firstly Enable GPS!", Toast.LENGTH_SHORT).show();
            finish();
        }
        // start wifi updates
        mWifiCollector.registerWifiReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // stop wifi updates
        mWifiCollector.unregisterWifiReceiver();
    }

    /**
     * Location provider - GoogleApiClient is created. TODO delete this cause no longer needed
     */
    private void initialiseLocationUpdater() {
        //Allows an app to access precise location, thus ask for permissions for that
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    Constants.MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    Constants.MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        }

        mLocationUpdater = new LocationUpdater(this, this);
        mLocationUpdater.createLocationProvider();
    }

    // TODO delete both these because no longer needed
    @Override
    public void onLocationUpdated(Location location) {
        mLocationUpdater.stopLocationUpdates();
        location.getLatitude();
        location.getLongitude();
    }

    @Override
    public void onConnected() {
        if (LocationUpdater.getGPSStatus(this)){
            mLocationUpdater.startLocationUpdates();
        } else {
            Toast.makeText(this, "You need to enable GPS for this application to work!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // TODO delete this as no longer needed
        if (mLocationUpdater != null)
            mLocationUpdater.connectLocationProvider();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // TODO delete this as no longer needed
        mLocationUpdater.disconnectLocationProvider();
    }

    /**
     * When user clicks on a map, generate a marker on it and then check if generate grid or not
     * @param latLng
     */
    @Override
    public void onMapClick(LatLng latLng) {
        // add the marker
        markerList.add(mIndoorMapManager.addMarker(latLng, BitmapLoader.getInstance().blueMarker));
        // check if grid is generated or not
        mIndoorMapManager.checkIfGenerateGrid(latLng);
    }

    /**
     * when user pressed on the marker, it's color changes showing which marker was selected.
     * Also the "more info" button can be enabled or disabled accordingly.
     * @param marker
     * @return
     */
    @Override
    public boolean onMarkerClick(Marker marker) {
        // Check for the previously selected marker. Need to revert it's color
        if (selectedMarker != null) {
            // If the marker already has a location associated with ti, then make the marker green
            if (selectedMarker.getTag() instanceof IndoorLocation){
                selectedMarker.setIcon(BitmapLoader.getInstance().greenMarker);
            } else {
                // otherwise make it blue
                selectedMarker.setIcon(BitmapLoader.getInstance().blueMarker);
            }
        }

        // the selected marker has a red color
        marker.setIcon(BitmapLoader.getInstance().redMarker);

        // set the newly selected marker
        selectedMarker = marker;

        // now need to handle more info button
        if (selectedMarker != null && selectedMarker.getTag() instanceof IndoorLocation){
            // if the marker has info associated with it, the enable
            // button press and change it's text to "T" which stands for True
            showInfoDialogButton.setEnabled(true);
            showInfoDialogButton.setText("T");
        } else {
            // Else disable the button and set the text to be "F", which stand fir False
            showInfoDialogButton.setEnabled(false);
            showInfoDialogButton.setText("F");
        }
        return false;
    }

    /**
     * When button "GRID" is pressed on the window, then display a message which markers to press.
     * @param v
     */
    public void onClickGenerateGrid (View v){
        // Display info of which marker to select
        Toast.makeText(this, "Set Top-Left Marker!", Toast.LENGTH_LONG).show();
        // Set the flag that we're generating the grid
        mIndoorMapManager.requestGridCoordinates();
    }

    /**
     * when a long click is done generate a marker. This is often used when
     * a marker is being placed next to another marker, thus a short click wouldn't help
     * @param latLng
     */
    @Override
    public void onMapLongClick(LatLng latLng) {
        // Add a marker to the grid
        markerList.add(mIndoorMapManager.addMarker(latLng, BitmapLoader.getInstance().blueMarker));
        // check if generating grid or not
        mIndoorMapManager.checkIfGenerateGrid(latLng);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void onClickSwitchFloor(View v){
        if (mIndoorMapManager.getGroundOverlayAnimator().isRunning() || selectedFloorButton == v){
            return;
        }
        switch (v.getId()){
            case R.id.ground_floor_button:
                selectedFloor = 0;
                mIndoorMapManager.animateGroundOverlay(mIndoorMapManager.fleemingJenkinsFirstOverlay,
                        mIndoorMapManager.fleemingJenkinsGroundOverlay);
                break;
            case R.id.first_floor_button:
                selectedFloor = 1;
                mIndoorMapManager.animateGroundOverlay(mIndoorMapManager.fleemingJenkinsGroundOverlay,
                        mIndoorMapManager.fleemingJenkinsFirstOverlay);
        }

        // Now we simply need to change the colors of the buttons so that used could distinguish
        // Firstly need to make the normal grey color the previous button
        if (selectedFloorButton != null) {
            selectedFloorButton.setBackgroundTintList(
                    getResources().getColorStateList(R.color.colorGrey, null));
        }
        // and then set the background color on the newly pressed button
        v.setBackgroundTintList(
                getResources().getColorStateList(R.color.colorPrimary, null));
        // Finally set the v to be the next selectedFloorButton
        selectedFloorButton = (Button) v;
    }

    /**
     * Function clears all the markers from the map and is called when button "CLEAR ALL" is pressed.
     * @param v
     */
    public void onMapClear(View v){
        // Firstly ask if user actually wanted this in an alert dialog
        new AlertDialog.Builder(this)
                .setTitle("Clear All Markers?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with clear. Go thorugh all markers and then remove them from the map
                        for (Marker m : markerList){
                            m.remove();
                        }

                        // clear marker list cause no longer need it.
                        markerList.clear();
                        // disable the button to show more info and make sure selected marker is null
                        showInfoDialogButton.setEnabled(false);
                        showInfoDialogButton.setText("F");
                        selectedMarker = null;
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }


    /**
     * Clears the whole database which is attached to the app. This required entering a password.
     * @param v
     */
    public void onClickDBClear(View v){
        // Alert dialog is used to check if the user knows the password to delete the database
        // This is used just in case the users might accidentally press delete and then will press OK...
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Delete All Database?")
                .setMessage("Please Enter the password = " + Constants.DELETE_PASSWORD + ".");

        final EditText password = new EditText(this);
        // Specify the type of input expected as a password
        password.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(password);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // If the entered password is correct, wipe out the database
                        if (password.getText().toString().equals(Constants.DELETE_PASSWORD)) {
                            // continue with delete. get database instance and then delete the location
                            IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(getApplicationContext());
                            databaseHelper.deleteAllLocationsAndSignals();
                            // display some message
                            Toast.makeText(getApplicationContext(), "Database is deleted!!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "Password is incorrect!", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert);
        builder.show();
    }

    IndoorLocation mCurrentIndoorLocation = new IndoorLocation();

    private boolean isScanSaving = false;
    private int leftSampleNumber = 0;

    /**
     * When a button "SAVE!" is pressed on the screen then this method is being called.
     * It saves the information regarding the location and then starts collecting wifi data in the location.
     * How much data to collect is being selected with the buttons on the right side of the screen.
     * Location has some information regarding that which needs to be entered. In the edit text field
     * need to enter Building/Room in this format.
     * @param v
     */
    public void onClickSaveLocation(View v){
        // Firstly read the edit text field and trim all spaces
        String locText = locationEditText.getText().toString().trim();

        // Need to firstly select the marker. Otherwise display the message.
        // Also need to enter the data correctly (e.g. Fleeming Jenkin/TLG)
        if (selectedMarker == null){
            Toast.makeText(this, "Select a marker first!", Toast.LENGTH_SHORT).show();
            return;
        } else if (locText.isEmpty() || !locText.contains("/")){
            Toast.makeText(this, "Enter Building/Room", Toast.LENGTH_SHORT).show();
            return;
        }

        // How many wifi scans to be performed
        leftSampleNumber = selectedSampleNumber;
        // Are wifi scans being performed? true
        isScanSaving = true;

        // get the position of the selected marker
        LatLng latLng = selectedMarker.getPosition();

        // some info which was entered bythe user
        String tmp[] = locText.split("/");
        String building = tmp[0].trim();
        String room = tmp[1].trim();

        // Create an instance of Indoor Location
        // Enter the data inside such as floor number, latitude, longitude, building and room
        mCurrentIndoorLocation = new IndoorLocation(selectedFloor, room, building,
                latLng.latitude, latLng.longitude);

        // Set the tag for the marker which later allows to read info about the location
        selectedMarker.setTag(mCurrentIndoorLocation);

        // Start wifi scans but check is gPS is enabled
        if (!LocationUpdater.getGPSStatus(this)){
            Toast.makeText(this, "Firstly Enable GPS!", Toast.LENGTH_SHORT).show();
        }
        mWifiCollector.startWifiScan();
    }

    /**
     * When a button "DB" is pressed from the screen all of the data from the database is being read
     * and then the markers for the specific floor are being added on the map. Which floor data to be
     * imported is decided with the button presses on the bottom-right side.
     * @param v
     */
    public void onClickFromDatabase (View v) {
        // So firstly read all of the database and import all of the signals. The process them and sort by average
        IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(this);
        List<IndoorSignal> signals = databaseHelper.getAllSignals();
        processedMap = WifiCollector.sortByAverage(WifiCollector.getProcessed(signals));

        // total number of markers
        int total = 0;
        // Go through all locations and select the ones which are on the correct floor only
        for (Map.Entry<IndoorLocation, List<ProcessedSignal>> entry : processedMap.entrySet()) {
            if (entry.getKey().floor == selectedFloor) {    // If correct floor
                total++;                                    // increment total
                // Then add a new marker and set the correct tag. Add the marker to the marker List.
                Marker m = mIndoorMapManager.addMarker(new LatLng(entry.getKey().lat, entry.getKey().lng), BitmapLoader.getInstance().greenMarker);
                m.setTag(entry.getKey());
                markerList.add(m);
            }
        }
        // Display how many markers were imported
        Toast.makeText(this, "You have " + total + " locations in this Floor!", Toast.LENGTH_SHORT).show();
    }


    private int selectedSampleNumber = 5;
    private Button selectedSampleButton;

    /**
     * User can change how many wifi scans to be performed in the location using the buttons
     * on the right side of the screen. There is available 1, 3 or 5 wifi scans samples are being
     * stored in the database
     * @param v
     */
    @TargetApi(Build.VERSION_CODES.M)
    public void onClickSelectSampleNumber (View v){
        // check which button was pressed cause each of the button calls this function
        switch (v.getId()){
            // first button to scan wifi once
            case R.id.one_sample_button:
                selectedSampleNumber = 1;
                break;
            // scan wifi 3 times...
            case R.id.three_sample_button:
                selectedSampleNumber = 3;
                break;
            case R.id.five_sample_button:
                selectedSampleNumber = 5;
                break;
            default: selectedSampleNumber = 5;
        }
        // Now we simply need to change the colors of the buttons so that used could distinguish
        // Firstly need to make the normal grey color the previous button
        if (selectedSampleButton != null) {
            selectedSampleButton.setBackgroundTintList(
                    getResources().getColorStateList(R.color.colorGrey, null));
        }
        // and then set the backgruond color on the newly pressed button
        v.setBackgroundTintList(
                getResources().getColorStateList(R.color.colorPrimary, null));
        // Finally set the v to be the next selectedSampleButton
        selectedSampleButton = (Button) v;
    }

    /**
     * When grid is being finished this function is being called automatically.
     * @param markerList
     */
    @Override
    public void onGridDrawFinish(List<Marker> markerList) {
        // What we simply do is display a message that the grid is finished and add
        // all of the markers from the grid to our global markerList
        Toast.makeText(this, "Grid Finished!", Toast.LENGTH_SHORT).show();
        this.markerList.addAll(markerList);
    }

    /**
     * When a wifi signals gets collected, this function is being called automatically.
     * @param spots
     */
    @Override
    public void onWifiCollected(List<ScanResult> spots) {
        // Sometimes wifi is being scanned by the telephone automatically, thus need to ensure
        // that we are actually collecting the data thus isScanSaving variable is doing that
        if (isScanSaving){
            // Call a function to store the scan to the database
            addToDatabase(spots);
            // Display a message of how many signals collected and many left to be collected
            Toast.makeText(this, "Collected " + spots.size() + " signals!\n" +
                    (leftSampleNumber-1) + " samples left!", Toast.LENGTH_LONG).show();
            // minus one how many scans left
            leftSampleNumber--;
            //while scans is not equal to 0, keep repeating scans
            if (leftSampleNumber != 0){
                mWifiCollector.startWifiScan();
            } else {
                // scans done, thus make this false and do not call scan any more
                isScanSaving = false;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Runtime.getRuntime().gc();
        mMap.clear();
    }

    /**
     * The function stores data into the database. This requires getting the database and
     * calling a store function on the collected signals.
     * @param spots
     */
    public void addToDatabase(List<ScanResult> spots){
        // get unix timestamp
        long timestamp = System.currentTimeMillis() / 1000;
        // get the database instance
        IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(this);

        // Go through all of the collected signals and
        for (ScanResult spot : spots) {
            // An instance of IndoorSignal to be stored in a database
            IndoorSignal signal = new IndoorSignal(mCurrentIndoorLocation, spot.BSSID,
                    spot.SSID, spot.level, timestamp);
            // sotre it in the database
            databaseHelper.addSignal(signal);
        }
    }

    /**
     * Function is called when "SINGLE DB" button is pressed on the screen. It allows deleting a
     * single entry from the database. Need to select on a marker to do that, which has info regarding it.
     * @param v
     */
    public void onClickDeleteSingleDB (View v){
        // Firstly check that user selected a marker and that the marker has valid info in it
        if (selectedMarker != null && selectedMarker.getTag() instanceof IndoorLocation) {
            // Create a new alert dialog, which asks if you want to delete a single entry
            new AlertDialog.Builder(this)
                    .setTitle("Delete Selected From Database?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete, thus get database instance and call a function to delete the location
                            // which also deletes the signals associated with the location
                            IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(getApplicationContext());
                            databaseHelper.deleteSingleLocation((IndoorLocation) selectedMarker.getTag());
                            // Need to set the tag to smth which is not instanceof IndoorLocation
                            selectedMarker.setTag(0);
                            // Remove the marker from out processed map
                            processedMap.remove(selectedMarker.getTag());
                            // Change the status of the button whic allows getting more info about the location
                            showInfoDialogButton.setEnabled(false);
                            showInfoDialogButton.setText("F");
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        } else {
            // If incalid marker or no marker selected at all, call a message
            Toast.makeText(this, "Select a valid marker!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The method is called when the button to show more information about the location is
     * pressed. It opens up a dialog containing all of the averages of collected signals.
     * @param v
     */
    public void onClickShowInfoDialog(View v){
        String message = "";

        // If the collected marker isn't inside the processed map, that means the info is in database
        // thus need to get all signals from there and again sort my average like at the beginning
        if (!processedMap.containsKey(selectedMarker.getTag())){
            IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(this);
            List<IndoorSignal> signals = databaseHelper.getAllSignals();
            processedMap = WifiCollector.sortByAverage(WifiCollector.getProcessed(signals));
        }

        // Total number of signals in the location
        message += "Total " + processedMap.get(selectedMarker.getTag()).size() + " Signals\n";
        // Then need to populate the message with the info. I use SSID and average signal level.
        for (ProcessedSignal s : processedMap.get(selectedMarker.getTag())){
            message += String.format("%s      %.2fdBm\n", s.ssid, s.average);
        }

        // Get indoor location instance to get the room and floor level
        IndoorLocation loc = (IndoorLocation) selectedMarker.getTag();
        // Create a dialog to display info
        new AlertDialog.Builder(this)
                .setTitle(loc.building)
                .setMessage("Room: " + loc.room + ", Floor: " + loc.floor + "\n" + message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    /**
     * The method is being calls when the user presses the "IMPORT!" button
     * on the screen. It then opens a file explorer where the user can navigate to the
     * file. Then importing function is being called.
     * @param v
     */
    public void onClickImportDatabase (View v){
        //ask for permissions to access files on the phone
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    Constants.MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
            // browser.
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

            // Filter to only show results that can be "opened", such as a
            // file (as opposed to a list of contacts or timezones)
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            // To search for all documents available via installed storage providers,
            // it would be "*/*".
            intent.setType("*/*");

            startActivityForResult(intent, READ_REQUEST_CODE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                importFromDatabase(uri);
            }
        }
    }

    /**
     * The function checks that the imported file is of correct format and then
     * imports the selected database from the file explorer
     * @param uri
     */
    private void importFromDatabase (Uri uri){
        // Fisrlt need to check if the type of the file is a database (*.db)
        // thus split the path by the dot

        Log.e("path", uri.getPath());
        String[] split = uri.getPath().split("\\.");
//
        for (int i = 0; i < split.length; i++)
            Log.e("split files", split[i]);

        // If the last value in the parsed array isn't equal to "db", then it's a wrong file format
        if (!split[split.length-1].equals("db")){
            Toast.makeText(this, "Please select a database file *.db!", Toast.LENGTH_LONG).show();
        }

        // Else we can proceed by getting database instance and then
        // use the function fro mthe database to import the file
        IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(this);
        try {
            boolean status = databaseHelper.importDatabase(uri.getPath());
            if (status){
                Toast.makeText(this, "Database imported!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to import!", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * When export button (on the screen) is clicked, call this function.
     * It opens up a dialog where the user is asked to enter the name of the database
     * to be exported. It then calls another function whic hactually handles exporting
     * @param v
     */
    public void onClickExportDatabase (View v){
        //ask for permissions to access files on the phone
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    Constants.MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            //create a dialog box with a text field to enter the folder name for storing data
            android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
            builder.setTitle("Enter file Name (with .db)");

            // Set up the input text field
            final EditText fileName = new EditText(this);
            // Specify the type of input expected
            fileName.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            builder.setView(fileName);
            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //calls a method for storing files
                    exportToFile (fileName.getText().toString());
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //else does nothing
                    dialog.cancel();
                }
            });
            // show the dialog
            builder.show();
        }
    }

    /**
     * Function handles exporting the database to the mobile device.
     * It calls my database which manages file transfers.
     * @param fileName - string filename such as my_database.db, which will be exported to IndoorDatabase folder
     */
    private void exportToFile (String fileName){
        // Get the instance of database
        IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(this);
        // Get the external directory of the phone where data can be stored
        File root = Environment.getExternalStorageDirectory();

        try {
            // If can write to file (permission given) etc.
            if (root.canWrite()){
                // get the full path of the folder to which export the database
                File dir = new File (root.getAbsolutePath() + "/IndoorDatabase/");
                // If such folder doesn't exist, create one, otherwise ignore this
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                // Try to export the database to the specific file which was specified in the text field
                boolean status = databaseHelper.exportDatabase(dir.getAbsolutePath()+ "/"+fileName);
                // Display the message if the export was a success or not.
                if (status){
                    Toast.makeText(this, "Exported to IndoorDatabase/"+fileName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "File IndoorDatabase/"+fileName+" already Exists!", Toast.LENGTH_LONG).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Print the full path to the exported file
        Log.i("database path", databaseHelper.getDatabasePath());
    }

    // Methods have to be implemented if we want the marker to change it's location LatLng
    // When it is being dragged. They are not used but are needed to be implemented!
    @Override
    public void onMarkerDragStart(Marker marker) {
    }

    @Override
    public void onMarkerDrag(Marker marker) {
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
    }
}
