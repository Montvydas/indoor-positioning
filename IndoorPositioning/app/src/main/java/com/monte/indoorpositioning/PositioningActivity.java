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
import android.annotation.TargetApi;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.jjoe64.graphview.GraphView;
import com.monte.indoorpositioning.map.*;
import com.monte.indoorpositioning.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 *
 * Created by monte on 26/03/2017.
 *
 * Positioning activity is responsible for showing the red dot on the map of the user location and
 * updating the location as it scans the wifi signals, records barometer data and location from an
 * accelerometer based step counter.
 *
 * The activity works in these steps:
 * 1) It initialises all of the basic views such as buttons, seekBars etc.
 * 2) It reads the database information and parses that into an easily readable Map structure (for cache - quicker access)
 * 3) (EXTRA FEATURE) It then setups the Map and floor plans of the ground and the first floors of the Fleeming Jenkins Building.
 * 4) Initialises the custom red dot and then hides it from the user until it's indoor location is being detected.
 * 5) Now the wifi scan starts. Whenever a new scan is received, it's info is being sent here.
 * 6) Wifi scan info is processed using KNN algorithm and the new location is being evaluated using special algorithm.
 * 7) Red dot location is being changed according to the newly evaluated position.
 *
 *
 * 8) (EXTRA FEATURE) As wifi positioning is not smooth, a custom step counter is added using accelerometer sensor.
 * Whenever the user walks to a certain direction, which can be deduced from the magnetometer sensor,
 * the user's location is being updated accordingly in a smooth way.
 * 9) (EXTRA FEATURE) A barometer sensor is being used to detect when user moves the floor up or down. This allows to
 * change between the indoor maps (either ground or first floor) and also allows to only use the data
 * from the database which was collected in the users current floor.
 * 10) (EXTRA FEATURE) Blind people can't tell where they are thus in "Position!" mode whenever the user presses on the
 * screen, he is being informed of the building, room and the floor level using text-to-speech engine.
 * 10) (EXTRA FEATURE) Additionally from the mentioned behaviour if user selects "Debug!" mode, then sliders and some text views
 * become visible. A developer would usually use it. It allows to tweak several parameters to optimise the
 * algorithm to work better or improve the step counter algorithm.
 */

public class PositioningActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener, GoogleMap.OnCameraMoveListener, WifiCollector.OnWifiCollectorListener,
        MotionSensorManager.OnMotionSensorManagerListener, CompoundButton.OnCheckedChangeListener,
        SeekBar.OnSeekBarChangeListener, RadioGroup.OnCheckedChangeListener {

    private GoogleMap mMap;                             // Google maps instance
    private WifiCollector mWifiCollector;               // Wifi collector is used to collect and parse wifi data
    private MotionSensorManager mMotionSensorManager;   // Motion sensor manager collects sensor information and gives it in a readable form
    private TextView stepAlgoInfoText;                  // Text views to display some debug information
    private TextView knnInfoText;                       // For step and KNN algorithms
    private TextToSpeech textToSpeech;                  // Text to speech is used to tell the location
    private boolean isDebug;                            // if Debug was selected this becomes positive and starts showing debug windows
    private IndoorMapManager mIndoorMapManager;         // Used to control indoor map view - create ground overlays, markers and the red dot
    private Map<IndoorLocation, List<ProcessedSignal>> processedMap = new HashMap<>();  // Processed database is stored here
    private ProcessedLocation currentLocation;          // The closest location, which determines where the user is at the moment

    // Values being used when processing using KNN algorithm
    private List<Marker> markerList = new ArrayList<>();// All of the added markers are put here in order to easily be able to remove them from the map
    private boolean isUserIndoors = false;          //
    private int knnNumber = 4;
    private double maxDistance = 10;
    private int algorithmOption = 2;                    // Algorithm option can be changed in debug mode

    private int currentFloor = 0;

    // Values used when performing step ocunter algorithm
    private boolean hasCrossedTop = false;                      // Have we crossed the top bar or not
    double previousRisingTime = System.currentTimeMillis();     // previous time when we crossed the defined accelerometer value
    double maxTopAcceleration = 0;                              // max top acceleration value to check if phone is not being shaken
    private double maxFreq = 9;            // Max walking frequency is defined here
    private double minFreq = 3;             // Min walking frequency is is defined
    private double maxTop = 10;             // Maximum top peak value is defined

    private GraphManager mGraphManager;     // graph manager is used to draw acceleration graph

    private float degrees = 0;              // degrees from gyroscope
    private float cameraOffset = 0;         // if map is rotated, this rotation is being saved here
    private float pitch = 0;                // pitch of the phone (tilt)
    private float magnetometerOffset = 0;   // magnetometer offset to the gyroscope
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_positioning);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);     //screen always portrait
        Constants.setStatusBarTranslucent(true, getWindow());                       // make status bar translucent
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);       // keep the screen always on

        isDebug = getIntent().getBooleanExtra("POSITION_MODE_KEY", false);          // Check if debug or not debug mode

        setupViews();           // Initialise all of the used views within the activity
        setupDatabase();        // Read the database and parse the information form it

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Initialises text to speech engine. Being called in onResume
     */
    private void setupTextToSpeech (){
        // Create new instance of text to speech
        textToSpeech= new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // If successfully initialised, then setup the language to be the default - UK in UK and so on.
                if(status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });
    }

    /**
     * Database is being setup here.
     */
    private void setupDatabase (){
        // Get database instance
        final IndoorDatabaseHelper databaseHelper = IndoorDatabaseHelper.getInstance(this);
        // Get all saved signals from the database
        List<IndoorSignal> signals = databaseHelper.getAllSignals();
        // Process signals (for all of the locations calculates the average and median) and sort them by average
        processedMap = WifiCollector.sortByAverage(WifiCollector.getProcessed(signals));
    }

    /**
     * Setups the basic views such as buttons, seekbars etc.
     */
    private void setupViews(){
        int status = View.GONE;
        if (isDebug){               // Make most of the views visible if debug option is selected
            status = View.VISIBLE;
        }

        // setup graphs to be drawn
        mGraphManager = new GraphManager((GraphView) findViewById(R.id.graph_acceleration));
        mGraphManager.setupGraphs();
        mGraphManager.setVisibility(status);

        // Find the camera lock switch from the xlm. Set the listener for it and enable by default.
        // Switch used to lock the camera to show the red dot in the center
        Switch cameraLockSwitch = (Switch) findViewById(R.id.lock_camera_switch);
        cameraLockSwitch.setOnCheckedChangeListener(this);
        cameraLockSwitch.setChecked(true);

        Switch graphViewSwitch = (Switch) findViewById(R.id.graph_switch);
        graphViewSwitch.setOnCheckedChangeListener(this);
        if (isDebug) {
            graphViewSwitch.setChecked(true);
        }
        graphViewSwitch.setVisibility(status);


        // Create wifi collector instance responsible for collecting wifi signals and processing them
        mWifiCollector = new WifiCollector(this);
        mWifiCollector.setOnWifiCollectorListener(this);

        // Motino snsor manager is responsible for reading and processing sensor data which can then be easily used
        mMotionSensorManager = new MotionSensorManager(this);
        mMotionSensorManager.setOnMotionSensorManagerListener(this);

        // Radio buttons are used to change between different algorithms, which can all be tested to find the best one.
        RadioGroup algoRadioGroup = (RadioGroup) findViewById(R.id.algorithm_radio_group);
        algoRadioGroup.setOnCheckedChangeListener(this);
        algoRadioGroup.setVisibility(status);

        // Different seekbars are used to optimise different constants
        // Step counter constants can be optimised with these:
        SeekBar maxFreqSeekBar = (SeekBar) findViewById(R.id.max_freq_seek_bar);
        SeekBar minFreqSeekBar = (SeekBar) findViewById(R.id.min_freq_seek_bar);
        SeekBar maxTopSeekBar = (SeekBar) findViewById(R.id.max_top_seek_bar);

        maxFreqSeekBar.setProgress((int)maxFreq);
        minFreqSeekBar.setProgress((int)minFreq);
        maxTopSeekBar.setProgress((int)maxTop);


        // KNN algorithm constants can be optimised using seek bars:
        SeekBar knnNumberSeekBar = (SeekBar) findViewById(R.id.knn_number_seek_bar);
        SeekBar maxDistanceSeekBar = (SeekBar) findViewById(R.id.max_distant_seek_bar);

        // Optimisation information is displayed in the following text views:
        stepAlgoInfoText = (TextView) findViewById(R.id.info_text_view);
        knnInfoText = (TextView) findViewById(R.id.knn_info_text_view);

        // Set visibility of all the views according to "isDebug" value. If debug, then visible and otherwise
        maxFreqSeekBar.setVisibility(status);
        minFreqSeekBar.setVisibility(status);
        maxTopSeekBar.setVisibility(status);
        knnNumberSeekBar.setVisibility(status);
        maxDistanceSeekBar.setVisibility(status);
        stepAlgoInfoText.setVisibility(status);
        knnInfoText.setVisibility(status);

        // Set initial text for the text views
        stepAlgoInfoText.setText("MaxFreq: " + maxFreq + " MinFreq: " + minFreq +
                " MaxTop: " + maxTop);
        knnInfoText.setText("MaxDist: " + maxDistance + " KNN Nr: " + knnNumber +
                " Used markers: ?");

        // Set seek bar change listeners to detect the changes
        maxFreqSeekBar.setOnSeekBarChangeListener(this);
        minFreqSeekBar.setOnSeekBarChangeListener(this);
        maxTopSeekBar.setOnSeekBarChangeListener(this);
        knnNumberSeekBar.setOnSeekBarChangeListener(this);
        maxDistanceSeekBar.setOnSeekBarChangeListener(this);
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
        mMap = googleMap;
        // Move the camera to the beginning location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(55.922082, -3.172315), 18.5f));
        // Set padding which will shift default elements down a bit as after making the status bar
        // translucent, these elements were shifted up
        mMap.setPadding(0, 70, 0, 0);
        // When pressing on  marker an options menu is opened. This feature is disabled.
        mMap.getUiSettings().setMapToolbarEnabled(false);

        // Setup the listeners for the map
        mMap.setOnMapClickListener(this);               // When clicking on the map
        mMap.setOnCameraMoveListener(this);             // Move camera is being moved

        // Creating indoor map instance for managing indoor maps, markers etc
        mIndoorMapManager = new IndoorMapManager(mMap, this);
        // Firstly setup the ground overlays for the Fleeming Jekins Building
        mIndoorMapManager.setupMapOverlays();
        // Then create the marker showing the users location. Place it next to Kings buildings.
        mIndoorMapManager.createCurrentPositionMarker(10, new LatLng(55.922082, -3.172315));
        // Make it invisible as it becomes visible only when user enters the building
        mIndoorMapManager.setCurrentPositionVisible(false);
        // lock the camera to the red dot to begin with
        mIndoorMapManager.setLockCamera(true);
        // Add 5 pikachus to the map
        mIndoorMapManager.addAnimalsToMap();

        // If location permission is given, then allow displaying google maps location, else skip this step
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    /**
     * When map is clicked this method is being called/
     * @param latLng
     */
    @Override
    public void onMapClick(LatLng latLng) {
        // Determine the floor number in english language
        String floor = "ground";
        if (currentFloor == 1)
        {
            floor = "first";
        }

        // In debug do not use text to speech but instead allow moving the marker to the location where
        // The user pressed
        if (isDebug) {
            mIndoorMapManager.moveCurrentPosition(latLng);
        } else {    // In positioning mode say out-loud the location using text-to speech engine
            if (currentLocation != null) {
                String text = "You are in " + currentLocation.room + ", " + floor + " floor. " +
                        currentLocation.building + " Building.";
                // From lollipop new text to speech engine is being used
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ttsGreater21(textToSpeech, text);
                } else {
                    ttsUnder21(textToSpeech, text);
                }
            }
        }
    }

    /**
     * Older versions of android use this method.
     * @param text
     */
    @SuppressWarnings("deprecation")
    private void ttsUnder21(TextToSpeech textToSpeech, String text) {
        HashMap<String, String> map = new HashMap<>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessageId");
        // simply speak out loud the location
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, map);
    }

    /**
     * new versinos of android use this version.
     * @param text
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void ttsGreater21(TextToSpeech textToSpeech, String text) {
        String utteranceId=this.hashCode() + "";
        // simply speak out loud the location
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    /**
     * When camera is being moved, e.g. rotated, zoomed, this method is being called
     */
    @Override
    public void onCameraMove() {
        CameraPosition cameraPosition = mMap.getCameraPosition();
        // If the previous camera offset is different from the current camera bearing, update the offset
        if (cameraOffset != cameraPosition.bearing){
            // The offset is being used for the compass to display the correct direction when map is rotated
            cameraOffset = cameraPosition.bearing;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // On pause unregister receivers to save battery
        // Wifi receiver is unregistered
        mWifiCollector.unregisterWifiReceiver();
        // Motion sensors such as accelerometer and gyro
        mMotionSensorManager.unregisterMotionSensors();
        // Finally stop the periodic wifi updates
        mWifiCollector.stopRepeatingUpdates();

        // Need to turn off text to speech. We can turn it back on in onResume
        if(textToSpeech != null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LocationUpdater.getGPSStatus(this)){
            Toast.makeText(this, "Firstly Enable GPS!", Toast.LENGTH_SHORT).show();
            finish();
        }
        // Enable wifi updates
        mWifiCollector.registerWifiReceiver();
        // Start motion sensors updates
        mMotionSensorManager.registerMotionSensors();
        // setup text to speech engine
        setupTextToSpeech();
//        mWifiCollector.startWifiScan();
        mWifiCollector.startRepeatedUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Runtime.getRuntime().gc();
        mMap.clear();
    }

    /**
     * method is automatically called every time the wifi signals are collected from the nearby routers.
     * The signals then have to be processed by computing KNN and calculating the predicted location
     * of the user.
     * @param spots
     */
    @Override
    public void onWifiCollected(List<ScanResult> spots) {
        // If nothing to process skip this
        if (processedMap.size() == 0){
            return;
        }

        // Otherwise process the spots into IndoorLocation List for later processing
        List<IndoorSignal> hotSpots = new ArrayList<>();
        for (ScanResult ap : spots){
            hotSpots.add(new IndoorSignal(ap.BSSID, ap.SSID, ap.level));
        }

        // print some log messages
        Log.i("onWifiCollected", "Received " + hotSpots.size() + " signals");

        // Complex function which performs KNN algorithm with several various settings
        // Go into the function to find out what are these parameters. It returns an ordered
        // list from the smallest euclidian distance to the largest one
        List<ProcessedLocation> orderedLocations = WifiCollector.getKnn(processedMap, hotSpots,
                false, knnNumber, true, mIndoorMapManager.getCurrentPosition(), currentFloor,
                !isUserIndoors, maxDistance);

        // We receive a list, which is sorted, check that it is a valid list, meaning it's not null and size > 0
        if (orderedLocations != null && orderedLocations.size() > 0) {
            int locationCount = orderedLocations.size();

            // remove all previously drawn markers from the map and then clear the list
            for (Marker m : markerList){
                m.remove();
            }
            markerList.clear();

            // set location accuracy (red transparent circle). The accuracy will depend on the
            // first euclidian distance
            mIndoorMapManager.setLocationAccuracy((float) (orderedLocations.get(0).euclidian / 2.0));

            // this is the current location of the user. When pressing the screen this will be told out-loud
            currentLocation = orderedLocations.get(0);

            // set debug text, which is shown when in debug activity
            knnInfoText.setText("MaxDist: " + maxDistance + " KNN Nr: " + knnNumber +
                    " Used markers: " + locationCount);

            // Triangulate 3 points ***********************
            List<LatLng> centroidsList = new ArrayList<>();
            if (locationCount > 2) {
                for (int i = 0; i < 3; i++) {
                    ProcessedLocation p = orderedLocations.get(i);
                    centroidsList.add(new LatLng(p.lat, p.lng));
                    if (isDebug) {  // in debug show some red points telling which points were used for triangulation
                        markerList.add(mIndoorMapManager.addMarker(new LatLng(p.lat, p.lng), BitmapLoader.getInstance().redMarker));
                    }
                }
            }

            // Additional 2 debug points
            if (isDebug && locationCount > 4) {
                for (int i = 3; i < 5; i++){
                    ProcessedLocation p = orderedLocations.get(i);
                    // two more location markers are added to the map for info purposes in debug mode
                    markerList.add(mIndoorMapManager.addMarker(new LatLng(p.lat, p.lng), BitmapLoader.getInstance().greenMarker));
                }
            }

            // initialise the predicted location. This is mostly used in debug mode as sometimes the
            // triangulation couldn't be used (not enough points), thus the app would stop working...
            LatLng predictedPosition = new LatLng(0, 0);

            // depending on the algorithm type use different approaches
            if (algorithmOption == 0){      // this one uses the most likely location (smallest euclidian distance)
                predictedPosition = new LatLng(orderedLocations.get(0).lat,
                        orderedLocations.get(0).lng);
            } else if (algorithmOption == 1){   //triangulates to find the centroid in 3 circles
                predictedPosition = WifiCollector.getCentroid(centroidsList);
            } else if (algorithmOption == 2){   // applies weighted algorithm, which works best when full building is scanned
                predictedPosition = WifiCollector.getAlgorithmLocation(orderedLocations);
            }

            // apply weighting on wifi. If user is indoors the weight is 20%, otherwise if it's the first time using
            // the app or if the marker is lost, use 100% wifi
            float weight = isUserIndoors ? 0.2f : 1.0f;
            final LatLng  adjustedPosition = getWeightedPosition(mIndoorMapManager.getCurrentPosition(),
                    predictedPosition, weight);//

            // the marker using the motion sensors and wifi cannot be moved both at the same time.
            // If this is done, glitches will appear, thus need to ensure that motion animation is not
            // running before we can move the locatino due to Wifi
            if (!MarkerAnimation.valueAnimator.isRunning()) {
                mIndoorMapManager.moveCurrentPosition(adjustedPosition);
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (MarkerAnimation.valueAnimator.isRunning()){} // wait for the animatino to stop

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // move the marker to the next location
                                mIndoorMapManager.moveCurrentPosition(adjustedPosition);
                            }
                        });
                    }
                }).start();
            }

            // if we are here, then we are indoors for sure, make the marker visible
            isUserIndoors = true;
            mIndoorMapManager.setCurrentPositionVisible(true);
        } else {
            // otherwise we are not indoors and make sure the next time we scan all of the database
            // because maybe the marker got lost
            isUserIndoors = false;
            mIndoorMapManager.setCurrentPositionVisible(false);
        }
    }

    /**
     * We don't want big jumps to appear on the map thus we need to specify the weight of how much
     * big the jumps we want to be.
     * @param currPosition
     * @param predictedPosition
     * @param weight
     * @return
     */
    private LatLng getWeightedPosition (LatLng currPosition, LatLng predictedPosition, double weight){
        // This is very similar to latitude longitude interpolator (look for the class)
        // Just this one is happening a lot slower thus weight is big
        return new LatLng(currPosition.latitude + (predictedPosition.latitude - currPosition.latitude)*weight,
                currPosition.longitude + (predictedPosition.longitude - currPosition.longitude)*weight);
    }

    float overallDistance = 0;
    float stepDistance = 0;
    boolean isAccelerationInvalid = false;

    /**
     * When accelerometer sensor value changes, it is being processed using a low pass filter
     * and then the value is being passed here.
     * @param acceleration
     */
    @Override
    public void onLinearAccValueUpdated(float[] acceleration) {
        if (!isUserIndoors){
            return;
        }
        
        // acceleration in x axis - this points along the user walking path. 
        // It is summed from two vectors of the phone accelerometer X & Z
        // It is used to evaluate the walked distance
        double totalAccelX = Math.sin(Math.toRadians(pitch)) * acceleration[2] -
                Math.cos(Math.toRadians(pitch)) * acceleration[1];

        // acceleration in z axis - this points along gravity axis (downwards)
        // It is summed from two vectors of the phone accelerometer X & Z
        // It is used to detect steps. When step is detected, we can start calculating the
        // distance from the acceleration in X axis
        double totalAccelZ = Math.sin(Math.toRadians(pitch)) * acceleration[1] +
                Math.cos(Math.toRadians(pitch)) * acceleration[2];

        // This is used to integrate over acceleration in X direction
        if ((totalAccelX > 0.5 || totalAccelX < -0.5) && hasCrossedTop){
            stepDistance += totalAccelX/15.0;
            // if threshold is reached, this is shaking, not walking
            if (totalAccelX > 4){
                isAccelerationInvalid = true;
            }
        }

        // this is used to update the values to be drawn on the graph
        mGraphManager.updateValues(new float[]{(float) totalAccelZ, overallDistance, (float) totalAccelX});

        if (totalAccelZ > 4){                //rising
            // check what was the largest acceleration value in recent history
            if (totalAccelZ > maxTopAcceleration){
                maxTopAcceleration = totalAccelZ;
            }
            // Get the time value of when the 10 was crossed
            if (!hasCrossedTop){
                previousRisingTime = System.currentTimeMillis();      // get the timestamp of then it happened
                hasCrossedTop = true;                           // set the value which will be sed later
            }
        } else if (totalAccelZ < 0){          //falling
            // so if we already crossed value of 10
            if (hasCrossedTop){
                // get the time difference
                double dT = System.currentTimeMillis() - previousRisingTime;
                // calculate the frequency
                double freq = 1000.0/dT; // unit: ms

                // ensure that the conditions are correct
                // frequency must be within limits and the max acceleration
                // mustn't be too high, which would basically mean that the phone was shaken
                if (freq < maxFreq && maxTopAcceleration < maxTop && freq > minFreq){       //phoned is being shaken
                    // One step size is the scale
                    if (isAccelerationInvalid){
                        stepDistance = 0;
                        isAccelerationInvalid = false;
                    }
                    overallDistance += stepDistance;
                    final double scale = stepDistance;
                    stepDistance = 0;
                    Log.i("accelerometer", "Step!");
                    // Update the location of the red dot by calculating the new location
                    // from the rotation angle of the user, etc
                    if (mIndoorMapManager != null) {
                        if (!MarkerAnimation.valueAnimator.isRunning()) {
                            mIndoorMapManager.moveCurrentPosition(MotionSensorManager.getLatLngFromMotion(
                                    degrees - magnetometerOffset, mIndoorMapManager.getCurrentPosition(), scale));
                        } else {
                            // the marker using the wifi sensors and thus cannot be moved both at the same time.
                            // If this is done, glitches will appear, thus need to ensure that animation is not
                            // running before we can move the location again
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    while (MarkerAnimation.valueAnimator.isRunning()){}

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // function is being applied from the motino sensor manager which adjust the location
                                            mIndoorMapManager.moveCurrentPosition(MotionSensorManager.getLatLngFromMotion(
                                                    degrees - magnetometerOffset, mIndoorMapManager.getCurrentPosition(), scale));
                                        }
                                    });
                                }
                            }).start();
                        }
                    }
                }

                // maxTop is set back to 0 for the next evaluation
                maxTopAcceleration = 0;
                // we now haven't crossed the top anymore
                hasCrossedTop = false;
            }
        }
    }

    /**
     * The method is called whenever the gyro info is being updated. At the moment
     * the function doesn't do anything interesting but in the future it could be used to calibrate
     * the magnetometer sensor or predict the true north.
     * @param orientation
     */
    @Override
    public void onGyroOrientationCalculated(float[] orientation) {
        // we required degrees to know where the user is facing to
        degrees = orientation[0];
        // and pitch to calculate the accelerometer projections to X and Y axis
        pitch = orientation[2];

        // Sensors work faster that map in the realm state, thus map will be null at first
        //45 degrees comes from the fact that initial image is rotated 45 degrees to the left
        if (mIndoorMapManager != null) {
            mIndoorMapManager.setCurrentPositionDirection(degrees + 45 - cameraOffset - magnetometerOffset);
        }
    }

    /**
     * whenever rotation vector sensor is being updated, we calculate
     * the orientation (pitch, roll and yaw) and sent it back to the activity
     * @param orientation
     */
    @Override
    public void onRotationOrientationCalculated(float[] orientation) {
        // before user gets indoors, it's facing direction when compared to gyroscope is calculated
        // using magnetometer sensor
        if (!isUserIndoors){
            // this offset will be used later when deciding where the user is facing to
            magnetometerOffset = degrees - orientation[0];
        }
    }

    /**
     * Whenever the floor is being changed (user moved up or down) the callback is provided
     * with the offset value which is either true if user moced up or false if user moved down
     * @param offset
     */
    @Override
    public void onFloorChange(boolean offset) {
        // Later when we have more floors we have to do this
//        currentFloor = offset ? currentFloor + 1 : currentFloor - 1;

        // However when only 2 floors are available we can slightly change it
        if (!isUserIndoors){
            return;
        }

        // if offset is true, we moved up and if false, we moved down
        if (offset){
            // change to first floor map
            mIndoorMapManager.animateGroundOverlay(mIndoorMapManager.fleemingJenkinsGroundOverlay,
                    mIndoorMapManager.fleemingJenkinsFirstOverlay);
            currentFloor = 1;
        } else {
            // change back to ground floor map
            mIndoorMapManager.animateGroundOverlay(mIndoorMapManager.fleemingJenkinsFirstOverlay,
                    mIndoorMapManager.fleemingJenkinsGroundOverlay);
            currentFloor = 0;
        }

        Toast.makeText(this, "Moved " + (offset ? "UP" : "DOWN"), Toast.LENGTH_SHORT).show();
    }

    /**
     * Check button allows locking the camera and unlocking from the red dot
     * @param buttonView
     * @param isChecked
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()){
            // switch used to lock the camera
            case R.id.lock_camera_switch:
                if (mIndoorMapManager != null) {
                    mIndoorMapManager.setLockCamera(isChecked);
                }
                break;
            // switch used to show and hide the accelerometer graph
            case R.id.graph_switch:
                if (isChecked && mGraphManager != null){
                    // start drawing again and of course make the graph visible
                    mGraphManager.startDrawing();
                    mGraphManager.setVisibility(View.VISIBLE);
                } else {
                    // firstly stop drawing the graphs in the background to save resources
                    // then hide the graph
                    mGraphManager.stopDrawing();
                    mGraphManager.setVisibility(View.GONE);
                }
                break;
        }
    }

    /**
     *
     * @param seekBar
     * @param progress
     * @param fromUser
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // check which of the seek bars were just used
        switch (seekBar.getId()){
            case R.id.max_freq_seek_bar:
                maxFreq = progress;
                break;
            case R.id.min_freq_seek_bar:
                minFreq = progress;
                break;
            case R.id.max_top_seek_bar:
                maxTop = progress;
                break;
            case R.id.knn_number_seek_bar:
                if (progress == 0){
                    knnNumber = 1;
                } else {
                    knnNumber = progress;
                }
                break;
            case R.id.max_distant_seek_bar:
                maxDistance = progress;
                break;
        }

        // Set some text for information purposes, to see which slide bars and how they were changed
        knnInfoText.setText("MaxDist: " + maxDistance + " KNN Nr: " + knnNumber +
                " Used markers: ?");
        stepAlgoInfoText.setText("MaxFreq: " + maxFreq + " MinFreq: " + minFreq +
                " MaxTop: " + maxTop);
    }

    // Unused methods for the seek bar
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    /**
     * Different algorithm can be tested in the debug mode, which allows to experiment to get the best
     * results. 3 algorithms were provided one being single, then triangle and the algorithm.
     *
     * @param group
     * @param checkedId
     */
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId){
            // single (the top highest value is being used to calculate the location using wifi)
            case R.id.single_radio_button:
                algorithmOption = 0;
                break;
            // triangle (the center of top-3 highest location is used as the predicted location)
            case R.id.triangle_radio_button:
                algorithmOption = 1;
                break;
            // algorithm (uses all collected locations and adjust the position depending on their
            // calculated euclidian distance)
            case R.id.algorithm_radio_button:
                algorithmOption = 2;
                break;
        }
    }
}
