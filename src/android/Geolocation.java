package com.castr.cordova.plugin;

import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
     * Used to wait for the Google play services to be connected.
     */
    private CountDownLatch mGoogleApiConnection;

    /**
     * Built-in geolocation.
     */
    private BuiltInGeolocation mBuiltInGeolocation;

    /**
     * Check if Google play services are available.
     */
    private boolean mServiceAvailable;

    /**
     * Plugin options
     */
    private boolean mHighAccuracyEnabled;
    private long mMaximumAge;


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
        mGoogleApiConnection = new CountDownLatch(1);
        mGoogleApiClient.connect();
        mBuiltInGeolocation = new BuiltInGeolocation(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        try {
            mHighAccuracyEnabled = data.getBoolean(0);
            mMaximumAge = data.getLong(1);
        }
        catch (Exception e) {
            mHighAccuracyEnabled = true;
            mMaximumAge = 60 * 1000;
        }

        Log.v(TAG, "high accuracy enabled? " + String.valueOf(mHighAccuracyEnabled) + ", maximum age? " + String.valueOf(mMaximumAge));

        // TODO return correct error code
        final CallbackContext fCallbackContext = callbackContext;

        if ( ! mGoogleApiClient.isConnected() && ! mGoogleApiClient.isConnecting()) {
            mGoogleApiConnection = new CountDownLatch(1);
            mGoogleApiClient.connect();
        }

        if (action.equals("getLocation")) {

            // Wait for connection
            try {
                mGoogleApiConnection.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mServiceAvailable = LocationServices.FusedLocationApi.getLocationAvailability(mGoogleApiClient).isLocationAvailable();
            Log.i(TAG, "Location available? " + String.valueOf(mServiceAvailable));

            if (mServiceAvailable && mGoogleApiClient.isConnected()) {
                Log.i(TAG, "Getting location from Google services.");
                getLocation(new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        JSONObject position = new JSONObject();
                        if (location != null) {
                            Log.d(TAG, "Location: " + location.toString());
                            try {
                                position.put("latitude", location.getLatitude());
                                position.put("longitude", location.getLongitude());
                            } catch (JSONException e) {
                                fCallbackContext.error(e.getMessage());
                                e.printStackTrace();
                            }
                            // Sending location to the webview
                            fCallbackContext.success(position);
                        }
                        else {
                            Log.w(TAG, "No known position.");
                            fCallbackContext.error("No known position.");
                        }
                        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                    }
                });
            }
            else {
                getBuiltInLocation(fCallbackContext);
            }

//            cordova.getThreadPool().execute(new Runnable() {
//                @Override
//                public void run() {
//
//
//                }
//            });

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
        mGoogleApiConnection.countDown();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (result.getErrorCode() == ConnectionResult.API_UNAVAILABLE) {
            Log.w(TAG, "Location API is unavailable.");
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
    // Google service location finder
    // -------------------------------------------------------------------------------------------
    public void getLocation(LocationListener listener) {
        long minTime = System.currentTimeMillis() - (mMaximumAge);
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (location != null && location.getTime() >= minTime) {
            Log.i(TAG, "Last known location is fresh enough.");
            listener.onLocationChanged(location);
            return;
        }

        LocationRequest req = new LocationRequest();
        req.setInterval(0);
        req.setNumUpdates(1);

        if (mHighAccuracyEnabled) {
            Log.i(TAG, "Requesting with high accuracy.");
            req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
        else {
            Log.i(TAG, "Requesting with balanced accuracy.");
            req.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, req, listener, Looper.getMainLooper());

    }

    // -------------------------------------------------------------------------------------------
    // Built-in location finder
    // -------------------------------------------------------------------------------------------
    public void getBuiltInLocation(final CallbackContext fCallbackContext) {
        Log.i(TAG, "Getting location from built-in location.");
        mBuiltInGeolocation.setHighAccuracyEnabled(mHighAccuracyEnabled);
        mBuiltInGeolocation.setMaximumAge(mMaximumAge);
        mBuiltInGeolocation.getLocation(new BuiltInGeolocation.BuiltInLocationCallback() {
            @Override
            public void onSuccess(Location location) {
                JSONObject position = new JSONObject();
                if (location != null) {
                    Log.i(TAG, "Location: " + location.toString());
                    try {
                        position.put("latitude", location.getLatitude());
                        position.put("longitude", location.getLongitude());
                    } catch (JSONException e) {
                        fCallbackContext.error(e.getMessage());
                        e.printStackTrace();
                    }
                    // Sending location to the webview
                    fCallbackContext.success(position);
                }
                else {
                    Log.i(TAG, "No known position.");
                    fCallbackContext.error("No known position.");
                    // TODO : Prompt for gps
//                    Intent gpsOptionsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//                    cordova.getActivity().startActivity(gpsOptionsIntent);
                }


            }

            @Override
            public void onError(String message) {
                fCallbackContext.error(message);
            }
        });
    }

}
