package com.monte.indoorpositioning.database;
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
import java.util.Objects;

/**
 * Created by monte on 28/03/2017.
 *
 * IndoorSignal stores info about a single router's signal level in one specific IndoorLocation.
 * The information is being stored in IndoorSignal database table.
 */
public class IndoorSignal {
    public IndoorLocation location;         // A reference to the indoor location.
    public long timestamp;                  // Timestamp of when the signal was taken
    public String bssid;                    // MAC address of the router
    public String ssid;                     // Router name
    public double level;                    // RSSI signal level in dBm

    /**
     * Constructor to easier create the object.
     * @param bssid
     * @param ssid
     * @param level
     */
    public IndoorSignal (IndoorLocation location, String bssid, String ssid, double level, long timestamp) {
        this.location = location;   // For references look at the top of the class
        this.bssid = bssid;
        this.ssid = ssid;
        this.level = level;
        this.timestamp = timestamp;
    }

    /**
     * Another constructor.
     * @param bssid
     * @param ssid
     * @param level
     */
    public IndoorSignal(String bssid, String ssid, double level) {
        this.bssid = bssid;
        this.ssid = ssid;
        this.level = level;
    }

    public IndoorSignal() {
    }

    /**
     * One indoorSignal is treated equal to another if they both are of instance of IndoorSignal
     * and then only their BSSID's are the same. This is used when proccesing the info in a list and map.
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        // Only if both of instance of IndoorSignal and their BSSID's are the same
        // they are equal object instances
        if ((o instanceof IndoorSignal) &&
                (((IndoorSignal) o).bssid == this.bssid)) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Similarly a hashcode is generated using bssid only.
     * @return
     */
    @Override
    public int hashCode() {
        return Objects.hash(bssid);
    }
}
