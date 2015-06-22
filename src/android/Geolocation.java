package com.castr.cordova.plugin;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by freddy on 05/06/15.
 */
public class Geolocation extends CordovaPlugin implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "CdvGeolocationPlugin";

    /**
     * Google play services api.
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * Built-in location manager.
     */
    private LocationManager mLocationManager;

    /**
     * Last known location.
     */
    private Location mLastLocation;

    /**
     * Check if the Google api is connected.
     */
    private volatile boolean mIsApiConnected;


    @Override
    protected void pluginInitialize() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                    // Optionally, add additional APIs and scopes if required.
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        mGoogleApiClient.connect();
        mLocationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        final CallbackContext fCallbackContext = callbackContext;

        if ( ! mGoogleApiClient.isConnected() && ! mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }

        if (action.equals("getLocation")) {

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {

                    // crappy
                    while ( !mIsApiConnected) {
                        Log.i(TAG, "Waiting for services to connect.");
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException e) {
                            fCallbackContext.error(e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    if (LocationServices.FusedLocationApi.getLocationAvailability(mGoogleApiClient).isLocationAvailable()) {
                        // Get location from google
                        Log.i(TAG, "Getting location from Google services.");
                        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                                mGoogleApiClient);
                    }
                    else {
                        // Test with built-in location API
                        Log.i(TAG, "Getting location from location manager.");
                        mLastLocation = getLastBestLocation();
                    }

                    JSONObject position = new JSONObject();

                    if (mLastLocation == null) {
                        Log.i(TAG, "No last known positions.");
                        fCallbackContext.error("No last known positions.");
                    }
                    else {
                        try {
                            Log.i(TAG, "Adding locations attributes to callback object.");
                            position.put("latitude", mLastLocation.getLatitude());
                            position.put("longitude", mLastLocation.getLongitude());
                        } catch (JSONException e) {
                            fCallbackContext.error(e.getMessage());
                            e.printStackTrace();
                        }

                        // Sending location to the webview
                        fCallbackContext.success(position);
                    }

                }
            });

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
    }

    // -------------------------------------------------------------------------------------------
    // Google service connection
    // -------------------------------------------------------------------------------------------
    @Override
    public void onConnected(Bundle bundle) {
        mIsApiConnected = true;
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (result.getErrorCode() == ConnectionResult.API_UNAVAILABLE) {
            Log.i(TAG, "Location API is unavailable.");
            return;
        }
        retryConnecting();
    }

    private void retryConnecting() {
        if ( ! mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
    }

    // -------------------------------------------------------------------------------------------
    // Built-in location service
    // -------------------------------------------------------------------------------------------
    public Location getLastBestLocation() {
        // int minDistance = mStationaryRadius;
        // long minTime    = System.currentTimeMillis() - (mLocationTimeout * 1000);

        Location bestResult = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MIN_VALUE;

        // Iterate through all the providers on the system, keeping
        // note of the most accurate result within the acceptable time limit.
        // If no result is found within maxTime, return the newest Location.
        List<String> matchingProviders = mLocationManager.getAllProviders();
        for (String provider: matchingProviders) {
            Location location = mLocationManager.getLastKnownLocation(provider);
            if (location != null) {
                float accuracy = location.getAccuracy();
                long time = location.getTime();
                if (accuracy < bestAccuracy) {
                    Log.d(TAG, "Better provider: " + provider + " - location: " + location.getLatitude() + ", " + location.getLongitude() + ", " + location.getAccuracy() + ", " + location.getSpeed() + "m/s");
                    bestResult = location;
                    bestAccuracy = accuracy;
                    bestTime = time;
                }
            }
        }
        return bestResult;
    }
}
