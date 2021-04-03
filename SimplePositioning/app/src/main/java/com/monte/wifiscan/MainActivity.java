package com.monte.wifiscan;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 1) KNN weight reference points.
 * 2) Weight signals of the APs.
 * 3) Count visible APs.
 * 4) Take 5 measurements and average them out. Allow selecting how many measurements to take.
 * 5) Get Point in centre for tree closest locations
 */

public class MainActivity extends AppCompatActivity {
    public WifiManager wifi;
    public String wifis[];
    public WifiScanReceiver wifiReciever;
    int MY_PERMISSIONS_REQUEST_ACCESS_WIFI_STATE = 0;
    int MY_PERMISSIONS_REQUEST_CHANGE_WIFI_STATE = 1;
    int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 2;
    int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 3;

    private EditText locationText;
    private TextView resultTestKnn;
    private TextView resultTestCorr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        locationText = (EditText) findViewById(R.id.location_text);
        resultTestKnn = (TextView) findViewById(R.id.result_text_knn);
        resultTestCorr = (TextView) findViewById(R.id.result_text_corr);

        Log.e("onCreate", "Started App");


        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, MY_PERMISSIONS_REQUEST_ACCESS_WIFI_STATE);
        }
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CHANGE_WIFI_STATE}, MY_PERMISSIONS_REQUEST_CHANGE_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        wifi=(WifiManager)getSystemService(Context.WIFI_SERVICE);

        wifiReciever = new WifiScanReceiver();
    }

    boolean isSaving = false;
    private Map<String, List<WifiSpot>> locationMap = new HashMap<>();
    private String saveText = "";
    public void onLocationSave (View v){
        saveText = locationText.getText().toString();
        wifi.startScan();
        isSaving = true;
    }

    private boolean isWifiScanning = false;
    public void onLocate (View v){
        Log.e("onLocate", "Wifi started");
        wifiScanCount = 5;
        isWifiScanning = true;
        wifi.startScan();
    }

    public void onLocationsClear (View v){
        if (!locationMap.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Cleared " + locationMap.size(), Toast.LENGTH_SHORT).show();
            locationMap.clear();
        }
    }



    protected void onPause() {
        unregisterReceiver(wifiReciever);
        super.onPause();
    }

    protected void onResume() {
        registerReceiver(wifiReciever, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class WifiSpot {
        public String SSID;
        public String BSSID;
        public Integer level;

        public WifiSpot(String SSID, String BSSID, int level) {
            this.SSID = SSID;
            this.BSSID = BSSID;
            this.level = level;
        }

        public String getSSID() {
            return SSID;
        }

        public String getBSSID() {
            return BSSID;
        }

        public Integer getLevel() {
            return level;
        }
    }

    public class LocationPoint {
        private String locationName;
        private Double euclidian;

        public LocationPoint(String locationName, Double euclidian) {
            this.locationName = locationName;
            this.euclidian = euclidian;
        }

        public String getLocationName() {
            return locationName;
        }

        public Double getEuclidian() {
            return euclidian;
        }

        public void setEuclidian(Double euclidian) {
            this.euclidian = euclidian;
        }
    }

    private void exportValues (){
        String exported = "{";
        for (Map.Entry<String, List<WifiSpot>> entry : locationMap.entrySet()) {
            exported += "\'" + entry.getKey() + "\': [";

            for (WifiSpot ws : entry.getValue()){
                exported += "[\'"+ws.getBSSID()+"\',"+ws.getLevel()+"],";
            }

            exported = exported.substring(0, exported.length()-1) + "],";
        }
        exported = exported.substring(0, exported.length()-1) + "}";

        Log.e("exported", exported);
    }

    private void doCorrelation(List<WifiSpot> hotSpots){
        int size;

        PriorityQueue<LocationPoint> corrQueue= new PriorityQueue<>(10, new LocationComparator());

        for (Map.Entry<String, List<WifiSpot>> entry : locationMap.entrySet()) {
            LocationPoint maxCorr = new LocationPoint(entry.getKey(), 0.0);
            if (entry.getValue().size() > hotSpots.size()) {
                size = hotSpots.size();
            } else {
                size = entry.getValue().size();
            }

            int[] train = new int[size];
            for (int i = 0; i < size; i++) {
                train[i] = entry.getValue().get(i).getLevel();
            }

            int[] test = new int[size];
            for (int i = 0; i < size; i++) {
                test[i] = hotSpots.get(i).getLevel();
            }

            double corr = correlation(train, test);
            Log.e("correlation", corr + "");

            if (maxCorr.getEuclidian() < corr) {
                maxCorr.setEuclidian(corr);
            }
            corrQueue.add(maxCorr);
        }
        String toastText = "Correlation:\n";
        while(!corrQueue.isEmpty()){
            LocationPoint lp = corrQueue.poll();
            toastText += String.format("%s %.4f\n", lp.getLocationName(), lp.getEuclidian());
        }
        resultTestCorr.setText(toastText);
//        Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
    }

    private void doKNN(List<WifiSpot> hotSpots){
        PriorityQueue<LocationPoint> pq= new PriorityQueue<>(10, new LocationComparator());

        for (Map.Entry<String, List<WifiSpot>> entry : locationMap.entrySet()) {
            double sum = 0;
            int usedSpots = 0;

            double concatWeight = 1.0;
            for (WifiSpot ws : entry.getValue()){
                for (WifiSpot hs : hotSpots){
                    double weight = Math.abs(1.0 / hs.level);
                    if (hs.BSSID.equals(ws.BSSID)){
                        sum += Math.pow(hs.level - ws.level, 2)*weight;//*concatWeight;
                        usedSpots++;
                        break;
                    }
                }
                concatWeight -= 0.1;
            }

            sum = Math.sqrt(sum);
//                    if (sum < minimum){
//                        minimum = sum;
//                        location = entry.getKey();
//                    }
            if (usedSpots == entry.getValue().size()) {
                pq.add(new LocationPoint(entry.getKey(), sum));
            }
        }

        String temp = "KNN:\n";
        while (!pq.isEmpty()){
            LocationPoint lp = pq.poll();
            temp += String.format("%s %.3f\n", lp.getLocationName(), lp.getEuclidian());
            Log.e("loc", lp.getLocationName() + " " + lp.getEuclidian());
        }
        resultTestKnn.setText(temp);
//        Toast.makeText(getApplicationContext(), temp, Toast.LENGTH_LONG).show();
    }

    private void doKnnCorr(List<WifiSpot> hotSpots){
        PriorityQueue<LocationPoint> pq= new PriorityQueue<>(10, new LocationComparator());

        for (Map.Entry<String, List<WifiSpot>> entry : locationMap.entrySet()) {
            double multiplication = 0;
            int usedSpots = 0;

            for (WifiSpot ws : entry.getValue()){
                for (WifiSpot hs : hotSpots){
                    double weight = Math.abs(1.0 / hs.level);
                    if (hs.BSSID.equals(ws.BSSID)){

                        multiplication += Math.abs(1.0/hs.level * 1.0/ws.level)*weight*1000;
                        usedSpots++;
                        break;
                    }
                }
            }

            multiplication = Math.sqrt(multiplication);
//                    if (sum < minimum){
//                        minimum = sum;
//                        location = entry.getKey();
//                    }
//                    if (usedSpots == entry.getValue().size()) {
            pq.add(new LocationPoint(entry.getKey(), multiplication));
//                    }
        }

        String temp = "KNN+Corr:\n";
        while (!pq.isEmpty()){
            LocationPoint lp = pq.poll();
            temp += String.format("%s %.4f\n", lp.getLocationName(), lp.getEuclidian());
            Log.e("loc", lp.getLocationName() + " " + lp.getEuclidian());
        }
        resultTestCorr.setText(temp);
    }

    private int wifiScanCount = 5;
    private class WifiScanReceiver extends BroadcastReceiver{
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceive(Context c, Intent intent) {
            Log.e("onReceive", "Wifi received");
            List<ScanResult> wifiScanList = wifi.getScanResults();
//            Log.e("is device to AP supported?", String.valueOf(wifi.isDeviceToApRttSupported()));

            List<WifiSpot> hotSpots = new ArrayList<>();
            List<ScanResult> subList;
            if (wifiScanList.size() >= 5){
                subList = wifiScanList.subList(0, 6);
            } else {
                subList = wifiScanList;
            }
            for (ScanResult ap : subList){
                hotSpots.add(new WifiSpot(ap.SSID, ap.BSSID, ap.level));
            }

            Collections.sort(hotSpots, new Comparator<WifiSpot>() {
                @Override
                public int compare(WifiSpot o1, WifiSpot o2) {
                    return o2.getLevel().compareTo(o1.getLevel());
                }
            });
            if (isSaving) {
                locationMap.put(saveText, hotSpots);
                Toast.makeText(getApplicationContext(), saveText + " Saved!", Toast.LENGTH_SHORT).show();
                isSaving = false;
            } else {
//                doCorrelation(hotSpots);
                doKNN(hotSpots);
                doKnnCorr(hotSpots);
            }

            String loc = "[";
            for (WifiSpot ws : hotSpots){
                loc += "[\'" + ws.getBSSID()+"\',"+ws.getLevel()+"],";
                System.out.println(ws.SSID + " " + ws.BSSID + " " + ws.getLevel());
            }
            loc = loc.substring(0, loc.length()-1)+"]";
            Log.e("myTest", loc);

            if (isWifiScanning){
                wifiScanCount--;
                if (wifiScanCount == 0){
                    isWifiScanning = false;
                } else {
                    wifi.startScan();
                }
            }

        }
    }

    public class LocationComparator implements Comparator<LocationPoint>
    {
        @Override
        public int compare(LocationPoint x, LocationPoint y)
        {
            if (x.getEuclidian() < y.getEuclidian())
            {
                return -1;
            }
            if (x.getEuclidian() > y.getEuclidian())
            {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_WIFI_STATE
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Do something with granted permission
            return;
        } else if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_WIFI_STATE
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Do something with granted permission
            return;
        } else if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Do something with granted permission
            return;
        }else if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Do something with granted permission
            return;
        }

    }

    public static double correlation(int[] xs, int[] ys) {
        //TODO: check here that arrays are not null, of the same length etc

        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double syy = 0.0;
        double sxy = 0.0;

        int n = xs.length;

        for(int i = 0; i < n; ++i) {
            double x = xs[i];
            double y = ys[i];

            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
        }

        // covariation
        double cov = sxy / n - sx * sy / n / n;
        // standard error of x
        double sigmax = Math.sqrt(sxx / n - sx * sx / n / n);
        // standard error of y
        double sigmay = Math.sqrt(syy / n -  sy * sy / n / n);

        // correlation is just a normalized covariation
        return cov / sigmax / sigmay;
    }

    public static Pair<Double, Double> getCentroid (double x1, double x2, double x3,
                                                    double y1, double y2, double y3 ){
        return new Pair<>((x1+x2+x3)/3, (y1+y2+y3)/3);
    }
}
