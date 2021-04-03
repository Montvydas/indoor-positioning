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
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.monte.indoorpositioning.database.IndoorLocation;
import com.monte.indoorpositioning.database.IndoorSignal;
import com.monte.indoorpositioning.database.ProcessedLocation;
import com.monte.indoorpositioning.database.ProcessedSignal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by monte on 23/03/2017.
 *
 * Wifi Collectors is responsible for :
 * 1) Performing Wifi scans and sending them back to the activity
 * 2) Processing the wifi scan data coming from the database
 * 3) Performing positioning algorithms such as KNN to tell where the user is.
 *
 */
public class WifiCollector {
    public WifiManager wifi;                            // Wifi manager instance
    public WifiScanReceiver wifiReceiver;               // Broadcast received instance
    private OnWifiCollectorListener collectorListener;  // Wifi receive listener is being called when wifi is received
    private Context context;                            // Activity context
    private static int INTERVAL = 1000;                 // Describes how frequent wifi scans have to be performed in ms

    /**
     * Constructor.
     * @param context
     */
    public WifiCollector(Context context) {
        this.context = context;                                             // define context
        wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);   // Create instance of wifi manager
        wifiReceiver = new WifiScanReceiver();                              // Create broadcast received instance
    }

    /**
     * Function is called whenever a wifi scan is to be performed.
     */
    public void startWifiScan(){
        // Simply calls startScan on wifi manager
        if (!LocationUpdater.getGPSStatus(context)){
            Toast.makeText(context, "Firstly Enable GPS!", Toast.LENGTH_SHORT).show();
            ((Activity) context).finish();
        }
        wifi.startScan();
    }

    /**
     * Function called when we no longer want to perform
     * wifi scans thus we can unregister that usually in onPuase() method
     */
    public void unregisterWifiReceiver() {
        context.unregisterReceiver(wifiReceiver);
    }

    /**
     * Function has to be called whenever we want to later perform wifi scans.
     * Usually it is being called in onResume() method
     */
    public void registerWifiReceiver() {
        context.registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    /**
     * Function used to process data coming from the database. the only thing passed is a list of
     * IndoorSignals and the function return a Map<IndoorLocation, List<ProcessedSignal>>,
     * where Processed location has already averages out signals.
     * @param signals
     * @return
     */
    public static Map<IndoorLocation, List<ProcessedSignal>> getProcessed (List<IndoorSignal> signals){
        // Create a map to return to
        Map<IndoorLocation, List<ProcessedSignal>> returnMap = new HashMap<>();
        // Create another map used for processing purposes
        // IndoorLocation is the location of the marker, String is the bssid of the router
        // and IndoorSignal is a single response from a single router
        Map<IndoorLocation, Map<String, List<IndoorSignal>>> locationMap = new HashMap<>();

        // Go through all signals and then group them into the given map structure
        for (IndoorSignal s : signals) {
            // Firstly need to add the IndoorLocation to the map if it doesn't contain one
            // or else add the map of bssid's if it doesn't have one as well.

            // So if we already contain the location in the map, proccess from here
            if (locationMap.containsKey(s.location)) {
                // check if we have the bssid of the signal. If we do, we get
                // the list the map is holding and then add a new value to the list
                if (locationMap.get(s.location).containsKey(s.bssid)){
                    locationMap.get(s.location).get(s.bssid).add(s);
                    // else means we don't have the list. Create one, append it
                    // with a new value and then put the value back into the location map
                } else {
                    List<IndoorSignal> tmpList = new ArrayList<>();
                    tmpList.add(s);
                    locationMap.get(s.location).put(s.bssid, tmpList);
                }
                // means we don't have the location nor the map of signals etc.
            } else {
                // create a list to store signals and append the new signal to it
                List<IndoorSignal> tmpList = new ArrayList<>();
                tmpList.add(s);
                // Create a new map and put the newly created list inside the map
                Map<String, List<IndoorSignal>> tmpMap = new HashMap<>();
                tmpMap.put(s.bssid, tmpList);
                // finally put the map inside the location map
                locationMap.put(s.location, tmpMap);
            }
        }

        // now that we have the processed information we need to calculate the averages and medians
        // for all of the bssids thus go through all location in the locationMap
        for (Map.Entry<IndoorLocation, Map<String, List<IndoorSignal>>> trainLocation : locationMap.entrySet()) {
            Map<String, List<IndoorSignal>> value = trainLocation.getValue();   // map value is another map
            IndoorLocation key = trainLocation.getKey();                        // key is the location

            // We wil now start to populate our return map
            // If we don't yet have the location inside the returnMap, we create a new
            // List and then insert that into the map
            if (!returnMap.containsKey(key)){
                List<ProcessedSignal> tmpList = new ArrayList<>();
                returnMap.put(key, tmpList);
            }

            // Go through all of the collected signals and calculate their averages and
            // medians each of the collected bssids
            for (Map.Entry<String, List<IndoorSignal>> trainSignals : value.entrySet()) {
                // new instance of processed Signal
                ProcessedSignal processedSignal = new ProcessedSignal();
                // calcualte averages and medians using external functions
                processedSignal.average = calculateAverage(trainSignals.getValue());
                processedSignal.median = calculateMedian(trainSignals.getValue());
                // Set the locations, bssid and ssid of the signal
                processedSignal.bssid = trainSignals.getValue().get(0).bssid;
                processedSignal.ssid = trainSignals.getValue().get(0).ssid;
                processedSignal.location = trainSignals.getValue().get(0).location;
                // append the List we have inside the returnMap
                returnMap.get(key).add(processedSignal);
            }
        }
        // finally return the processed map
        return returnMap;
    }

    /**
     * Function used to sort all of the processed values by their averages.
     * @param locationMap
     * @return
     */
    public static Map<IndoorLocation, List<ProcessedSignal>> sortByAverage (Map<IndoorLocation,
            List<ProcessedSignal>> locationMap){
        // Go through all of the locations in the map and sort them by the custom comparator
        for (Map.Entry<IndoorLocation, List<ProcessedSignal>> trainSignals : locationMap.entrySet()) {
            Collections.sort(trainSignals.getValue(), new Comparator<ProcessedSignal>() {
                @Override
                public int compare(ProcessedSignal o1, ProcessedSignal o2) {
                    return o2.average.compareTo(o1.average);
                }
            });
        }
        // finally return sorted by average map
        return locationMap;
    }

    /**
     * Function used to sort all of the processed values by their median.
     * @param locationMap
     * @return
     */
    public static Map<IndoorLocation, List<ProcessedSignal>> sortByMedian (Map<IndoorLocation,
            List<ProcessedSignal>> locationMap){
        // Go through all of the locations in the map and sort them by the custom comparator
        for (Map.Entry<IndoorLocation, List<ProcessedSignal>> trainSignals : locationMap.entrySet()) {
            Collections.sort(trainSignals.getValue(), new Comparator<ProcessedSignal>() {
                @Override
                public int compare(ProcessedSignal o1, ProcessedSignal o2) {
                    return o2.median.compareTo(o1.median);
                }
            });
        }
        // finally return sorted by median map
        return locationMap;
    }

    /**
     * The largest function. It is responsible for performing KNN algorithm using several of the parameters.
     * We pass in the locationMap, which has all of the information about the locations and then calculate
     * the euclidian distance for all of the locations.
     * @param locationMap
     * @param testSignals           - Signals which were received by the wifi scan at the current time
     * @param isWeighted            - value defining if the euclidian distance should be weighted or not
     * @param knnNumber             - number of routers being used to determine the location (basically KNN number)
     * @param isAverageOrMedian     - Tell if the average or median should be used to get the euclidian distance
     * @param currPosition          - Current position of the user
     * @param currFloor             - current floor of the user, which reduces computational power
     * @param isIgnoringDistance    - should we ignore maxDistance or not
     * @param maxDistance           - max distance to the training spots which are still considered to be plausible
     * @return
     */
    public static List<ProcessedLocation> getKnn (Map<IndoorLocation, List<ProcessedSignal>> locationMap,
                                                           List<IndoorSignal> testSignals, boolean isWeighted,
                                                           int knnNumber, boolean isAverageOrMedian, LatLng currPosition,
                                                           int currFloor, boolean isIgnoringDistance, double maxDistance){
        // A priority queue stores the Processed locations in the incrementing euclidian distance order
        List<ProcessedLocation> orderedLocations = new ArrayList<>();//PriorityQueue<>(10, new LocationComparator());

        // Firstly need to sort the test signals (collected signals in the incrementing signal order)
        Collections.sort(testSignals, new IndoorSignalComparator());

        // Use KNN number of signals if available and if not available, use
        // every signal which is available
        if (testSignals.size() >= knnNumber){
            testSignals = testSignals.subList(0, knnNumber);
        }

        // Current location instance has method require to find the
        // distance from one location to another in meters
        Location currLocation = new Location("currentLocation");
        currLocation.setLatitude(currPosition.latitude);
        currLocation.setLongitude(currPosition.longitude);

        // Go through all of the locations in the locationMap
        for (Map.Entry<IndoorLocation, List<ProcessedSignal>> trainLocation : locationMap.entrySet()) {
            IndoorLocation key = trainLocation.getKey();        // get the location, which is the key

            if (key.floor != currFloor){                        // Ignore the location if it is from the wrong floor
                continue;
            }

            // an instance of the location of the IndoorLocation
            Location loc = new Location("pointLocation");
            loc.setLatitude(key.lat);
            loc.setLongitude(key.lng);

            // make sure that we only consider signals within a certain range
            if (currLocation.distanceTo(loc) > maxDistance && !isIgnoringDistance){
                continue;
            }

            // weights for the distance. The further away the signal the bigger the weight is
            double distance = Math.pow(currLocation.distanceTo(loc), 2) / 100.0;
            double distanceWeight = isWeighted ? 1.0 / distance : 1;

            // euclidian distance
            double sum = 0;

            // number of used spots. If the number is not equal to test size, then wrong location
            int usedSpots = 0;

            // Go through all of the test signals
            for (IndoorSignal testSignal : testSignals){
                // Need to compare them with the data in the database
                for (ProcessedSignal trainSignal : trainLocation.getValue()){
                    // We can also weight the signal according to it's signal strength -
                    // the stringer the signal, the more trusful it is
                    double weight = isWeighted ? (Math.abs(1.0 / testSignal.level)) : 1.0;

                    // so if the test location BSSID is the same as train location BSSID
                    if (testSignal.bssid.equals(trainSignal.bssid)){
                        // get either averag or median value
                        double trainValue = isAverageOrMedian ? trainSignal.average : trainSignal.median;
                        // and add a value to the total sum of euclidian distance
                        sum += Math.pow(testSignal.level - trainValue, 2) * weight * distanceWeight;
                        // increment used spots. will use later
                        usedSpots++;
                        break;
                    }
                }
            }
            // square root as the distance...
            sum = Math.sqrt(sum);

            // only consider the locatinos which all had the processed signals.
            // This is important as let's say we are in a completely different
            // room thus not all signals are reachable and we oly reach a single out of 5
            // signals. Euclidian distance is then very small however the location is wrong!
            if (usedSpots == testSignals.size()) {
                // Processed location instance
                ProcessedLocation p = new ProcessedLocation();
                // need to set the correct value from the indoor location
                p.euclidian = sum;
                p.floor = key.floor;
                p.building = key.building;
                p.lat = key.lat;
                p.lng = key.lng;
                p.room = key.room;
                // add the instance to the priority queue, which automatically
                // sorts them in incrementing euclidian distance order
                orderedLocations.add(p);
            }
        }
        // sort the location before returning
        Collections.sort(orderedLocations, new LocationComparator());

        return  orderedLocations;
    }

    /**
     * Wifi broadcast received is called whenever we have available wifi signals
     * We then pass the collected signals back to the activity through the collectorListener
     */
    private class WifiScanReceiver extends BroadcastReceiver{
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceive(Context c, Intent intent) {
            List<ScanResult> wifiScanList = wifi.getScanResults();

            // passing back to the activity
            if (collectorListener != null){
                collectorListener.onWifiCollected(wifiScanList);
            }
        }
    }

    /**
     * A custom comparator to sort the ProcessedLocations according to their euclidian distance
     * This is used together with the PriorityQueue to sort the Locations in increasing
     * euclidian distance.
     */
    private static class LocationComparator implements Comparator<ProcessedLocation> {
        @Override
        public int compare(ProcessedLocation x, ProcessedLocation y) {
            // simply need to compare euclidian distances together
            if (x.euclidian < y.euclidian){
                return -1;
            } else if (x.euclidian > y.euclidian){
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * A custom comparator to sort the IndoorSignal according to their level in dBm
     * This is used together with the Collections.sort() method
     */
    private static class IndoorSignalComparator implements Comparator<IndoorSignal> {
        @Override
        public int compare(IndoorSignal o1, IndoorSignal o2) {
            // we simply need to compare signals together according to their levelw
            if (o1.level > o2.level){
                return -1;
            } else if (o1.level < o2.level){
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * Function which calculates the average of the given IndoorSignals list.
     * @param signals
     * @return
     */
    private static double calculateAverage(List <IndoorSignal> signals) {
        double sum = 0;
        // Go through all of the signals and then add their levels up
        if(!signals.isEmpty()) {
            for (IndoorSignal s : signals) {
                sum += s.level;
            }
            // return the sum of the signals divided by the number of the signals
            return sum / signals.size();
        }
        return sum;
    }

    /**
     * Function which calculates the median of the given IndoorSignals list.
     * @param signals
     * @return
     */
    private static double calculateMedian(List <IndoorSignal> signals) {
        // to find the median firstly need to sort the signals
        Collections.sort(signals, new IndoorSignalComparator());
        double median;
        // get the size of the collected signals
        int length = signals.size();

        // If we have even number of signals, then need to the two middle values and divide them by 2
        if (signals.size() % 2 == 0)
            median = (signals.get(length/2).level + signals.get(length/2-1).level)/2;
        else // else need to take the middle value and this is the median
            median = signals.get(length/2).level;
        return median;
    }


    /**
     * An algorithm which takes a list of ProcessedLocations (which has euclidian distance)
     * and then finds out the most likely location of the user from that.
     * @param locationList
     * @return
     */
    public static LatLng getAlgorithmLocation(List<ProcessedLocation> locationList){
        double x = 0;
        double y = 0;
        double sum = 0;
        // Go through all of the locations and then sum the 1/euclidian
        // additionally sum latitude/euclidian and longitude/euclidian.
        for (ProcessedLocation loc : locationList){
            sum += 1.0 / loc.euclidian;
            x += loc.lat / loc.euclidian;
            y += loc.lng / loc.euclidian;
        }
        // Invert summed euclidian distance
        sum = Math.pow(sum, -1);
        // summed latitude and longitude have to be multiplied by the summed inverted euclidian distance
        // to ensure that the result is weighted correctly
        return new LatLng (sum*x, sum*y);
    }

    /**
     * Task handler for repeating wifi scans.
     */
    Handler mHandler = new Handler();
    Runnable mHandlerTask = new Runnable()
    {
        @Override
        public void run() {
            try {
                startWifiScan();
            } finally {
                mHandler.postDelayed(mHandlerTask, INTERVAL);
            }
        }
    };

    /**
     * Function to start repeating wifi updates, which will repeat every second.
     */
    public void startRepeatedUpdates()
    {
        mHandlerTask.run();
    }

    /**
     * Function to stop repeating wifi updates to save battery.
     */
    public void stopRepeatingUpdates() {
        mHandler.removeCallbacks(mHandlerTask);
    }

    /**
     * Calculates the center point between 3 points in 2D space.
     * @param c
     * @return
     */
    public static LatLng getCentroid (List<LatLng> c){
        // This basically requires adding all latitudes and dividing them by 3
        // plus adding all longitudes and then dividing them by 3
        return new LatLng((c.get(0).latitude+c.get(1).latitude+c.get(2).latitude)/3,
                (c.get(0).longitude+c.get(1).longitude+c.get(2).longitude)/3);
    }

    /**
     * method called when wifi signals are being collected
     */
    public interface OnWifiCollectorListener{
        void onWifiCollected(List<ScanResult> spots);
    }

    /**
     * Fucntion to set the listener from the activity to listen to wifi signal collections
     * @param collectorListener
     */
    public void setOnWifiCollectorListener(OnWifiCollectorListener collectorListener){
        this.collectorListener = collectorListener;
    }
}
