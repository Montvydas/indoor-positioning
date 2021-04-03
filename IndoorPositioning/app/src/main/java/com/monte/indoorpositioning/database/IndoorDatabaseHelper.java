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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by monte on 23/03/2017.
 *
 * A singleton class responsible for managing the database.  Data base consists of two tables:
 * IndoorLocations and IndoorSignals. They structure looks like this:
 *
 * DATABASE NAME & VERSION
 *
 * IndoorLocations Database columns:
 * | LOCATION ID | FLOOR | ROOM | BUILDING | LATITUDE | LONGITUDE |
 * |      1      |   0   |  TLG |Fleeming..|55.921983 | -3.172379 |
 * ...
 *
 * IndoorSignals Database columns:
 * | SIGNAL ID | REFERENCED LOCATION ID | TIMESTAMP | BSSID | SSID | LEVEL |
 * |     1     |           23           | 1491131077|ad:ds..|cent..| -64   |
 * ...
 *
 * IndoorSignal is always referencing an Indoor Location database to see where it belongs to in
 * REFERENCED LOCATION ID column.
 */
public class IndoorDatabaseHelper extends SQLiteOpenHelper {
    // Database Info. Name of the database
//    private static final String DATABASE_NAME = "FleemingJenkins.db";
    private static String DATABASE_NAME = "testing4.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_INDOOR_SIGNALS = "indoorSignals";
    private static final String TABLE_INDOOR_LOCATIONS = "indoorLocations";

    // Signals Table Columns
    private static final String KEY_SIGNAL_ID = "id";
    private static final String KEY_SIGNAL_LOCATION_ID_FK = "locationId";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_BSSID = "bssid";
    private static final String KEY_SSID = "ssid";
    private static final String KEY_LEVEL = "level";

    // Locations Table Columns
    private static final String KEY_LOCATION_ID = "id";
    private static final String KEY_FLOOR = "floor";
    private static final String KEY_ROOM = "room";
    private static final String KEY_BUILDING = "building";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";

    // Tag for printing error Logs
    private final String TAG = "Database";

    // Instance of the class (singleton)
    private static IndoorDatabaseHelper sInstance;

    /**
     * Function to get the instance of the database.
     * @param context
     * @return
     */
    public static synchronized IndoorDatabaseHelper getInstance(Context context) {
        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new IndoorDatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Constructor should be private to prevent direct instantiation.
     * Make a call to the static method "getInstance()" instead.
     */
    private IndoorDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Called when the database connection is being configured.
    // Configure database settings for things like foreign key support, write-ahead logging, etc.
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // Called when the database is created for the FIRST time.
    // If a database already exists on disk with the same DATABASE_NAME, this method will NOT be called.
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_SIGNALS_TABLE = "CREATE TABLE " + TABLE_INDOOR_SIGNALS +
                "(" +
                KEY_SIGNAL_ID + " INTEGER PRIMARY KEY," +                                           // Define a primary key
                KEY_SIGNAL_LOCATION_ID_FK + " INTEGER REFERENCES " + TABLE_INDOOR_LOCATIONS + "," + // Define a foreign key
                KEY_TIMESTAMP + " INTEGER," +                                                       // ...
                KEY_BSSID + " TEXT," +
                KEY_SSID + " TEXT," +
                KEY_LEVEL + " REAL" +
                ")";

        String CREATE_LOCATIONS_TABLE = "CREATE TABLE " + TABLE_INDOOR_LOCATIONS +
                "(" +
                KEY_LOCATION_ID + " INTEGER PRIMARY KEY," + // Define a primary key
                KEY_FLOOR + " INTEGER," +                   // Define a foreign key
                KEY_ROOM + " TEXT," +                       // ...
                KEY_BUILDING + " TEXT," +
                KEY_LAT + " REAL," +
                KEY_LNG + " REAL" +
                ")";
        
        db.execSQL(CREATE_LOCATIONS_TABLE);                 // Perform SQL query
        db.execSQL(CREATE_SIGNALS_TABLE);
    }

    // Called when the database needs to be upgraded.
    // This method will only be called if a database already exists on disk with the same DATABASE_NAME,
    // but the DATABASE_VERSION is different than the version of the database that exists on disk.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            // Simplest implementation is to drop all old tables and recreate them
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_INDOOR_LOCATIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_INDOOR_LOCATIONS);
            onCreate(db);
        }
    }

    /**
     * Function to add a signal to databse.
     * @param signal
     */
    public void addSignal(IndoorSignal signal){
        // Create and/or open the database for writing
        SQLiteDatabase db = getWritableDatabase();
        // It's a good idea to wrap our insert in a transaction. This helps with performance and ensures
        // consistency of the database.
        db.beginTransaction();
        try {
            // Get the ID of the location from the indoor location table
            long locationId = addOrUpdateLocation(signal.location);

            // Put all values from the object into the database structure
            ContentValues values = new ContentValues();
            values.put(KEY_SIGNAL_LOCATION_ID_FK, locationId);
            values.put(KEY_TIMESTAMP, signal.timestamp);
            values.put(KEY_BSSID, signal.bssid);
            values.put(KEY_SSID, signal.ssid);
            values.put(KEY_LEVEL, signal.level);

            // Notice how we haven't specified the primary key. SQLite auto increments the primary key column.
            db.insertOrThrow(TABLE_INDOOR_SIGNALS, null, values);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Error while trying to add signal to database");
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Function add a location to a database if doesn't exist and return the id only if the
     * location already exists on a database.
     * Since SQLite doesn't support "upsert" we need to fall back on an attempt to UPDATE (in case the
     * user already exists) optionally followed by an INSERT (in case the user does not already exist).
     * Unfortunately, there is a bug with the insertOnConflict method
     * (https://code.google.com/p/android/issues/detail?id=13045) so we need to fall back to the more
     * verbose option of querying for the user's primary key if we did an update.
     * @param location
     * @return
     */
    public long addOrUpdateLocation(IndoorLocation location){
        // The database connection is cached so it's not expensive to call getWriteableDatabase() multiple times.
        SQLiteDatabase db = getWritableDatabase();
        long userId = -1;

        // It's a good idea to wrap our insert in a transaction. This helps with performance and ensures
        // consistency of the database.
        db.beginTransaction();
        try {
            // Put the location values into database structure
            ContentValues values = new ContentValues();
            values.put(KEY_FLOOR, location.floor);
            values.put(KEY_ROOM, location.room);
            values.put(KEY_BUILDING, location.building);
            values.put(KEY_LAT, location.lat);
            values.put(KEY_LNG, location.lng);

            // First try to update the user in case the user already exists in the database
            int rows = db.update(TABLE_INDOOR_LOCATIONS, values, KEY_LAT + "= ? AND " + KEY_LNG + "= ? AND " + KEY_FLOOR + "= ?",
                    new String[]{String.valueOf(location.lat), String.valueOf(location.lng), String.valueOf(location.floor)});

            // Check if update succeeded
            if (rows == 1) {
                // Get the primary key of the location we just updated
                String locationsSelectQuery = String.format("SELECT %s FROM %s WHERE %s = ? AND %s = ? AND %s = ?",
                        KEY_LOCATION_ID, TABLE_INDOOR_LOCATIONS, KEY_LAT, KEY_LNG, KEY_FLOOR);
                Cursor cursor = db.rawQuery(locationsSelectQuery,
                        new String[]{String.valueOf(location.lat), String.valueOf(location.lng), String.valueOf(location.floor)});
                try {
                    // A cursor allows to go through all of the selected data from the query
                    if (cursor.moveToFirst()) {
                        userId = cursor.getInt(0);
                        db.setTransactionSuccessful();
                    }
                } finally {
                    // Need to close the cursor when done
                    if (cursor != null && !cursor.isClosed()) {
                        cursor.close();
                    }
                }
            } else {
                // location with this these parameters did not already exist, so insert new user
                userId = db.insertOrThrow(TABLE_INDOOR_LOCATIONS, null, values);
                db.setTransactionSuccessful();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to add or update user");
        } finally {
            db.endTransaction();
        }
        return userId;
    }

    /**
     * Function for getting all of the signals in the database at once. This is being used to query
     * the database for the signals.
     * @return
     */
    public List<IndoorSignal> getAllSignals() {
        List<IndoorSignal> signals = new ArrayList<>();

        // SELECT * FROM SIGNALS
        // LEFT OUTER JOIN LOCATIONS
        // ON SIGNALS.KEY_SIGNAL_LOCATION_ID_FK = LOCATIONS.KEY_LOCATION_ID
        String SIGNALS_SELECT_QUERY =
                String.format("SELECT * FROM %s LEFT OUTER JOIN %s ON %s.%s = %s.%s",
                        TABLE_INDOOR_SIGNALS,
                        TABLE_INDOOR_LOCATIONS,
                        TABLE_INDOOR_SIGNALS, KEY_SIGNAL_LOCATION_ID_FK,
                        TABLE_INDOOR_LOCATIONS, KEY_LOCATION_ID);

        // "getReadableDatabase()" and "getWriteableDatabase()" return the same object (except under low
        // disk space scenarios)
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(SIGNALS_SELECT_QUERY, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    // Create IndoorLocation and IndoorSignal instances from the saved information in the database
                    IndoorLocation newLocation = new IndoorLocation();
                    newLocation.floor = cursor.getLong(cursor.getColumnIndex(KEY_FLOOR));
                    newLocation.room = cursor.getString(cursor.getColumnIndex(KEY_ROOM));
                    newLocation.building = cursor.getString(cursor.getColumnIndex(KEY_BUILDING));
                    newLocation.lat = cursor.getDouble(cursor.getColumnIndex(KEY_LAT));
                    newLocation.lng = cursor.getDouble(cursor.getColumnIndex(KEY_LNG));

                    IndoorSignal newSignal = new IndoorSignal();
                    newSignal.timestamp = cursor.getLong(cursor.getColumnIndex(KEY_TIMESTAMP));
                    newSignal.bssid = cursor.getString(cursor.getColumnIndex(KEY_BSSID));
                    newSignal.ssid = cursor.getString(cursor.getColumnIndex(KEY_SSID));
                    newSignal.level = cursor.getLong(cursor.getColumnIndex(KEY_LEVEL));
                    newSignal.location = newLocation;

                    // Add that to the returnable list
                    signals.add(newSignal);
                } while(cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to get posts from database");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return signals;
    }

    /**
     * Sometimes the room is entered incorrectly thus we can update that
     * @param location
     * @return
     */
    public int updateLocationRoom(IndoorLocation location) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_ROOM, location.room);

        // Updating profile picture url for user with that userName
        return db.update(TABLE_INDOOR_LOCATIONS, values, KEY_LAT + "= ? AND " + KEY_LNG + "= ? AND " + KEY_FLOOR + "= ?",
                new String[]{String.valueOf(location.lat), String.valueOf(location.lng), String.valueOf(location.floor)});
    }


    /**
     * Delete all locations and signals in the database. Be careful because
     * you won't be able to access ay of the deleted data anymore unless you backed up it
     *
     */
    public void deleteAllLocationsAndSignals() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Order of deletions is important when foreign key relationships exist.
            // Firstly siganls have to be delete, which hold the reference to the locations
            db.delete(TABLE_INDOOR_SIGNALS, null, null);
            db.delete(TABLE_INDOOR_LOCATIONS, null, null);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.d(TAG, "Error while trying to delete all signals and location");
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Used to get the database path. This is used when importing and exporting it.
     * @return
     */
    public String getDatabasePath (){
        SQLiteDatabase db = getWritableDatabase();
        return db.getPath();
    }

    /**
     * Used to import the database from the phone. You have to ensure that importPath
     * holds the path to the existing database otherwise exception will be called.
     * @param importPath
     * @return
     * @throws IOException
     */
    public boolean importDatabase(String importPath) throws IOException {
        // Close the SQLiteOpenHelper so it will commit the created empty
        // database to internal storage.
        close();
        File newDb = new File(importPath);
        File oldDb = new File(getDatabasePath());
        if (newDb.exists()) {
            FileUtils.copyFile(new FileInputStream(newDb), new FileOutputStream(oldDb));
            // Access the copied database so SQLiteHelper will cache it and mark
            // it as created.
            getWritableDatabase().close();
            DATABASE_NAME = newDb.getName();
            return true;
        }
        return false;
    }

    /**
     * Export the database to the phone. Ensure that the folder you selected and the name of
     * the database exists, otherwise wil through exceptino.
     * @param exportPath
     * @return
     * @throws IOException
     */
    public boolean exportDatabase(String exportPath) throws IOException {
        // Close the SQLiteOpenHelper so it will commit the created empty
        // database to internal storage.
        File newDb = new File(exportPath);
        File oldDb = new File(getDatabasePath());

        Log.e("export path", newDb.getAbsolutePath());
        if (!newDb.exists()) {
            FileUtils.copyFile(new FileInputStream(oldDb), new FileOutputStream(newDb));
            // Access the copied database so SQLiteHelper will cache it and mark
            // it as created.
            return true;
        }
        return false;
    }

    /**
     * Class for handling input - output streams of databases when importing
     * and exporting it from the file.
     */
    public static class FileUtils {
        /**
         * Creates the specified <code>toFile</code> as a byte for byte copy of the
         * <code>fromFile</code>. If <code>toFile</code> already exists, then it
         * will be replaced with a copy of <code>fromFile</code>. The name and path
         * of <code>toFile</code> will be that of <code>toFile</code>.<br/>
         * <br/>
         * <i> Note: <code>fromFile</code> and <code>toFile</code> will be closed by
         * this function.</i>
         *
         * @param fromFile
         *            - FileInputStream for the file to copy from.
         * @param toFile
         *            - FileInputStream for the file to copy to.
         */
        public static void copyFile(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
            FileChannel fromChannel = null;
            FileChannel toChannel = null;
            try {
                fromChannel = fromFile.getChannel();
                toChannel = toFile.getChannel();
                fromChannel.transferTo(0, fromChannel.size(), toChannel);
            } finally {
                try {
                    if (fromChannel != null) {
                        fromChannel.close();
                    }
                } finally {
                    if (toChannel != null) {
                        toChannel.close();
                    }
                }
            }
        }
    }

    /**
     * Used to delete a single location from the database, which also deletes all signals
     * related to that location.
     * @param location    
     */
    public void deleteSingleLocation(IndoorLocation location) {
        //Open the database
        SQLiteDatabase db = getWritableDatabase();

        // Begin the transaction
        db.beginTransaction();
        // Firstly need to find the location thus perform the query.
        String locationsSelectQuery = String.format("SELECT %s FROM %s WHERE %s = ? AND %s = ? AND %s = ?",
                KEY_LOCATION_ID, TABLE_INDOOR_LOCATIONS, KEY_LAT, KEY_LNG, KEY_FLOOR);
        Cursor cursor = db.rawQuery(locationsSelectQuery,
                new String[]{String.valueOf(location.lat), String.valueOf(location.lng), String.valueOf(location.floor)});
        // Cursor stores all of the selected location (there will only be onein practice)

//        //these are global so that you can access from anywhere
//        int rowCount = cursor.getCount();
//        String[] BSSIDs = new String[rowCount];
//        String[] signals = new String[rowCount];
//        double[] lats = new double[rowCount];
//        double[] lngs = new double[rowCount];
//
//        cursor.moveToFirst();
//        int i = 0;
//        do {
//            BSSIDs[i] = cursor.getString(0);
//            signals[i] = cursor.getString(1);
//            lats[i] = cursor.getDouble(2);
//            lngs[i] = cursor.getDouble(3);
//            i++;
//        } while (cursor.moveToNext());
//
//        // positioning stage stage
//        double minSum = 100;
//        double latitude;
//        double longitude;
//        for (int j = 0; j < rowCount; j++){
//            String[] trainBssids = BSSIDs[j].split(" ");
//            String[] splitSignals = signals[j].split(" ");
//            int signalsCount = splitSignals.length;
//            // need to parse signals as integers
//            int[] trainSignals = new int[signalsCount];
//            for (int k = 0; k < signalsCount; k++){
//                trainSignals[k] = Integer.parseInt(splitSignals[k]);
//            }
//
//            // perform comparison between test signals
//            String[] testBssids;        // these come from current wifi scan (top 5?)
//            int[] testSignals;
//            int testCount = testBssids.length;
//            double sum = 0;
//            for (int k = 0; k < testCount; k++){
//                if (testBssids[k].equals(trainBssids[k])){
//                    sum += Math.pow(testSignals[k] - trainSignals[k], 2);
//                }
//            }
//            sum = Math.sqrt(sum);
//            if (minSum > sum){
//                minSum = sum;
//                latitude = lats[j];
//                longitude = lngs[j];
//            }
//        }
//
//        // ur current detected location is in latitude and longitude

        try {
            if (cursor.moveToFirst()) {
                // Get the ID of the location
                int row = cursor.getInt(0);
                // Delete all of the signals from the signals table that have the
                // associated ID to the location equal to the row
                db.execSQL("DELETE FROM " + TABLE_INDOOR_SIGNALS + " WHERE " + KEY_SIGNAL_LOCATION_ID_FK + "= " + row);

                Log.e("row is", row + "");
                // Now delete the location, which has the same id as the row
                db.delete(TABLE_INDOOR_LOCATIONS, KEY_LOCATION_ID + "= ?",
                        new String[]{String.valueOf(row)});
                db.setTransactionSuccessful();
            }
        } finally {
            // Close the cursor and end transaction
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            db.endTransaction();
        }
    }

}