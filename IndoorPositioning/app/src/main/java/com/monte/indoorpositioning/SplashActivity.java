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
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.google.android.gms.maps.MapsInitializer;
import com.monte.indoorpositioning.map.BitmapLoader;

/**
 * Created by monte on 26/03/2017.
 *
 * Activity displayed at the beginning of the App. It allows to choose to which activity to go next.
 * We have an option of choosing the training or positioning. A switch is provided to choose debug mode
 * in positioning activity to play around with different constants to find the best accuracy.
 *
 * The activity also pre-loads the images as this takes some time and processing. This also
 * ensures that images are only being loaded once as opposed to when each new activity is called
 *
 * The current activity also manages permission checks and makes sure that GPS is enabled before
 * calling another activity.
 */
public class SplashActivity extends Activity implements CompoundButton.OnCheckedChangeListener {
    private Switch debugSwitch;             // Switch which allows choosing debug option
    private Button positionButton;          // button for doing positioning. It will change text in side, thus needed.
    private boolean isDebug = false;        // Value to be passed to the next activity.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); //screen always portrait
        Constants.setStatusBarTranslucent(true, getWindow());                   // Make status bar translucent for nicer look

        setupViews();                                                   //Setup vies such as buttons and switches

        // We firstly load all of the images used within the google maps.
        // The images are loaded only once, which saves memory as opposed to loaded
        // when each of the activities start. It also makes other activity loading faster
        MapsInitializer.initialize(getApplicationContext());            // Needed for using BitmapDescriptorFactory

        BitmapLoader.getInstance().setContext(getApplicationContext()); // Set the context of a singleton
        BitmapLoader.getInstance().loadMarkers();                       // load markers (reg, green and blue)
        BitmapLoader.getInstance().loadPikachu(160);                    // load pikachu with the specified size
        BitmapLoader.getInstance().loadBuildings();                     // load building images used as ground overlay
        BitmapLoader.getInstance().loadLocationMarker(70, 80);          // load the custom red dot with the specified size
    }

    /**
     * function for setting up views.
     */
    private void setupViews(){
        debugSwitch = (Switch) findViewById(R.id.debug_switch);                 // Find debug switch in xml
        debugSwitch.setOnCheckedChangeListener(this);                           // set the change listener
        positionButton = (Button) findViewById(R.id.position_button);           // find the button in xml
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Handle permissions
        checkPermissions();

    }

    private boolean checkGpsStatus() {
        if (!LocationUpdater.getGPSStatus(this)) {
            //Enable GPS
            Toast.makeText(this, "Firstly Enable GPS!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }


    /**
     * button for training wifi spots is pressed, a new activity is started.
     * @param v
     */
    public void onClickTrain(View v){
        // check permissions again to ensure that these are given
        if (!checkPermissions()) {
            return;
        }
        // also check if the gps is turned on or not. Wifi requires gps permissions
        if (!checkGpsStatus()){
            return;
        }
        // start training activity to collect wifi signals
        Intent intent = new Intent(SplashActivity.this, TrainingActivity.class);
        startActivity(intent);
    }

    /**
     * Function used to check if all required permission are granted.
     * @return
     */
    private boolean checkPermissions(){
        boolean isPermissionsOk = true;
        // the only required permission is to access fine location. This is required on Android 6 and more
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Constants.MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            isPermissionsOk = false;
        }
        return isPermissionsOk;
    }

    /**
     * Button for starting positioning is pressed, start a new activity.
     * @param v
     */
    public void onClickPosition(View v){
        // check permissions again to ensure that these are given
        if (!checkPermissions()) {
            return;
        }
        // also check if the gps is turned on or not. Wifi requires gps permissions
        if (!checkGpsStatus()){
            return;
        }
        //start another activity
        Intent intent = new Intent(SplashActivity.this, PositioningActivity.class);
        // Passing debug value which comes from the switch
        intent.putExtra("POSITION_MODE_KEY", isDebug);
        startActivity(intent);
    }

    /**
     * Switch is being pressed, values need to be updated.
     * @param buttonView
     * @param isChecked
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Depending on the switch position set the text
        positionButton.setText(isChecked ? "Debug!" : "Position!");
        // Set the global variable which will be passed to the next activity
        isDebug = isChecked;
    }
}
