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
 * A class, which information is being stored in the database IndoorLocation table. It stores
 * all important information about the location where the signals were captured including the building,
 * room name and the floor level together with geographical coordinates (latitude and longitude).
 */
public class IndoorLocation {
    public long floor;              // Floor level of the user
    public String room;             // Room name e.g. TLG
    public String building;         // Building name e.g. Fleeming Jenkin
    public double lat;              // Latitude
    public double lng;              // Longitude


    /**
     * Constructor for easy initialisation of the instance
     * @param floor
     * @param room
     * @param building
     * @param lat
     * @param lng
     */
    public IndoorLocation(long floor, String room, String building, double lat, double lng) {
        this.floor = floor;     // look at top of the class for reference what each thing does
        this.room = room;
        this.building = building;
        this.lat = lat;
        this.lng = lng;
    }

    /**
     * A constructor with no info
     */
    public IndoorLocation() {
    }

    /**
     * Because it is a custom class, several methods have to be overridden to ensure good behaviour.
     * Equals is being used in a List<IndoorLocation> to check if the value exists in there or not yet etc.
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        // For two objects to be equal they have to both be IndoorLocation type,
        // their latitude, longitude, floor level and the room number has to be
        // identical. Otherwise this is a different location.
        if ((o instanceof IndoorLocation) &&
                (((IndoorLocation) o).lat == this.lat) &&
                (((IndoorLocation) o).lng == this.lng) &&
                (((IndoorLocation) o).floor == this.floor) &&
                Objects.equals(this.room, ((IndoorLocation) o).room)) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Hashcode is used within a map to create hash tables.
     * @return
     */
    @Override
    public int hashCode() {
        // Hash is generated from the same values which were used to tell
        // if object instances are equal.
        return Objects.hash(floor, lat, lng, room);
    }
}
