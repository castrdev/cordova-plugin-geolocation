package com.castr.cordova.plugin;

import android.location.Location;
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
    * Last know location.
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
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        final CallbackContext fCallbackContext = callbackContext;

        if (action.equals("getLocation")) {

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {

                    // crappy
                    while ( !mIsApiConnected) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            fCallbackContext.error(e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
                    JSONObject position = new JSONObject();

                    try {
                        position.put("latitude", mLastLocation.getLatitude());
                        position.put("longitude", mLastLocation.getLongitude());
                    } catch (JSONException e) {
                        fCallbackContext.error(e.getMessage());
                        e.printStackTrace();
                    }

                    Log.i(TAG, "Sending location to the webview.");
                    fCallbackContext.success(position);

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

    //*********************************************************************************************
    // Google service connection
    //*********************************************************************************************
    @Override
    public void onConnected(Bundle bundle) {
        mIsApiConnected = true;
    }

    @Override
    public void onConnectionSuspended(int i) {
        retryConnecting();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        retryConnecting();
    }

    private void retryConnecting() {
        if ( ! mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
    }
}
