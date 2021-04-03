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

import android.location.Location;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by monte on 05/04/2017.
 *
 * Class used to manage pokemon appearances on the map. It adds the pokemons to the map, manages
 * presses, and ensures that when the pokemon is found, it is being removed from the map.
 */
public class AnimalManager {
    private IndoorMapManager mIndoorMapManager;                 // Indoor map is used when adding the pokemons to the map
    private List<LatLng> mPikachuLocations = new ArrayList<>(); // Array list for storing pokemon LatLngs
    private Map<Marker, Location> mPikachuMap = new HashMap<>();// Map for storing pokemons with their locations

    /**
     * constructor requires indoor manager
     * @param mapManager
     */
    public AnimalManager(IndoorMapManager mapManager) {
        this.mIndoorMapManager = mapManager;
    }

    /**
     * Add pokemons to the map. This requires populating the pokemon list and pokemon map with
     * pre-defined coordinates for simplicity. at the moment pokemons ignore the floor level as it is beta only.
     */
    public void addPikachu(){
        // Add all locations of pikachu to the array list for easy initialisation
        mPikachuLocations.add(new LatLng(55.922462, -3.172624));
        mPikachuLocations.add(new LatLng(55.922568, -3.172727));
        mPikachuLocations.add(new LatLng(55.922637, -3.172547));
        mPikachuLocations.add(new LatLng(55.922455, -3.172335));
        mPikachuLocations.add(new LatLng(55.922184, -3.171965));

        // go through all coordinates and create markers for the on the map
        for (LatLng latLng : mPikachuLocations){
            // marker created
            Marker pikachuMarker  = mIndoorMapManager.addMarker(latLng, BitmapLoader.getInstance().pikachu);
            pikachuMarker.setDraggable(false);      //ensure that pokemon is not dragable
            pikachuMarker.setVisible(false);        // at first make the pokemon invisible

            Location pikachuLocation = new Location("pikachu"); // new location instance of the pokemon
            pikachuLocation.setLatitude(latLng.latitude);       // Latitude of the pokemon
            pikachuLocation.setLongitude(latLng.longitude);     // longitude

            mPikachuMap.put(pikachuMarker, pikachuLocation);    // put the pokemon into the pokemon map
        }
    }

    /**
     * Check that the pokemon is selected. If it is, remove the pokemon from the map thus marking it
     * as captured.
     * @param marker
     * @return
     */
    public boolean isPikachuSelected(Marker marker){
        // go through all of the map of pokemons
        for (Map.Entry<Marker, Location> entry : mPikachuMap.entrySet()) {
            // if pokemon is found
            if (marker.equals(entry.getKey())) {
                // remove it from the map and from the hashMap
                marker.remove();
                mPikachuMap.remove(marker);
                return true;
            }
        }
        return false;
    }


    /**
     * Used to hide and make the pokemon visible again when the user gets close to it.
     * @param location
     */
    public void setPikachuVisibleIfClose(Location location){
        // go through all pokemon map
        for (Map.Entry<Marker, Location> entry : mPikachuMap.entrySet()){
            // if pokemon found 5m in distance, make it visible, otherwise hide it
            boolean isVisible = location.distanceTo(entry.getValue()) < 5;
            entry.getKey().setVisible(isVisible);
        }
    }
}
