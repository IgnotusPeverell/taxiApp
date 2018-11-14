/*
 * Copyright (c) 2011-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.here.android.example.basicpositioningsolution;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.JsonReader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapFragment;
import com.here.android.mpa.mapping.MapState;
import com.here.android.positioning.StatusListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

public class BasicPositioningActivity extends Activity implements PositioningManager.OnPositionChangedListener, Map.OnTransformListener {

    // permissions request code
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;

    // map embedded in the map fragment
    private Map map;

    // map fragment embedded in this activity
    private MapFragment mapFragment;

    // positioning manager instance
    private PositioningManager mPositioningManager;

    // HERE location data source instance
    private LocationDataSourceHERE mHereLocation;

    // flag that indicates whether maps is being transformed
    private boolean mTransforming;

    // callback that is called when transforming ends
    private Runnable mPendingUpdate;

    // text view instance for showing location information
    private TextView mLocationInfo;

    private MapFragmentView m_mapFragmentView;
    private static GeoCoordinate coordinateForNavi;
    private static Button findMeButton;

    private static ArrayList<String> suggestions = new ArrayList<>();
    private static MyAdapter adapter;
    private static AutoCompleteTextView textView;
    private static String results = "";
    private static String resultsPlaces = "";
    private static LinkedList<JSONObject> resourceList = new LinkedList<>();
    private static String mappSuggestions = "";
    private static String placesSuggestion = "";
    public static GeoCoordinate destionationCoordinate;

    // permissions that need to be explicitly requested from end user.
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();

        adapter = new MyAdapter(this,
                android.R.layout.simple_dropdown_item_1line, suggestions);

        textView = (AutoCompleteTextView)
                findViewById(R.id.autoCompleteTextView);
        textView.setAdapter(adapter);

        textView.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                retrieveData(s);
            }

            @Override
            public void afterTextChanged(final Editable s) {
            }
        });

    }

    private final void retrieveData(CharSequence s) {
        ArrayList<String> listOfSuggestions = new ArrayList<>();
        s = s.toString().replaceAll(" ","+");
        JSONObject jsonResults = null;
        resourceList.clear();

        new RetrieveSuggestions().execute(
                "https://places.cit.api.here.com/places/v1/autosuggest?at=45.8150108,15.9819188&q="+s+"&app_id=pLCLPwCWaHC0UeB0BjyS&app_code=yVc4Gm7TWEjDmzSLA_k06w"
        );

        jsonResults = null;
        try {
            jsonResults = new JSONObject(results);
            JSONArray jsonArray = (JSONArray)jsonResults.get("results");
            jsonArray.length();

            for (int i = 0; i<jsonArray.length(); i++){
                JSONObject suggestion = (JSONObject)jsonArray.get(i);
                try {
                    listOfSuggestions.add(suggestion.get("title").toString()+","+suggestion.get("vicinity").toString().replaceAll("<br/>", ","));
                    resourceList.add(suggestion);
                    if(listOfSuggestions.size() > 5)
                        break;
                } catch (Exception e) {

                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        results = "";
        adapter.clear();
        adapter.addAll(listOfSuggestions);
        adapter.notifyDataSetChanged();

    }

    public static GeoCoordinate getPlaceGeoCordinate(String placeName) {
        GeoCoordinate returnCoordinate = null;
        String position = "";
        for(JSONObject place : resourceList){
            try {
                if((place.get("title").toString()+","+place.get("vicinity").toString().replaceAll("<br/>", ",")).toLowerCase().equals(placeName.toLowerCase())){
                    position = place.get("position").toString();
                    break;
                }
            } catch (Exception e) {

            }
        }

        if(position.equals("")){
            for(JSONObject place : resourceList){
                try {
                    if((place.get("title").toString()+","+place.get("vicinity").toString().replaceAll("<br/>", ",")).toLowerCase().contains(placeName.toLowerCase())){
                        position = place.get("position").toString();
                        break;
                    }
                } catch (Exception e) {

                }
            }
        }

        if(!position.equals("")){
            position = position.replace("[", "");
            position = position.replace("]", "");
            returnCoordinate = new GeoCoordinate(Double.parseDouble(position.split(",")[0]),Double.parseDouble(position.split(",")[1]));
        }
        return returnCoordinate;
    }

    public void initializeFindMeButton(){
        findMeButton = BasicPositioningActivity.this.findViewById(R.id.findMe);
        findMeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findMe(coordinateForNavi);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPositioningManager != null) {
            mPositioningManager.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPositioningManager != null) {
            mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR);
        }
    }

    @Override
    public void onPositionUpdated(final PositioningManager.LocationMethod locationMethod, final GeoPosition geoPosition, final boolean mapMatched) {
        final GeoCoordinate coordinate = geoPosition.getCoordinate();
        coordinateForNavi = coordinate;
        m_mapFragmentView.setCoordinate(coordinate);
    }

    public void findMe(final GeoCoordinate coordinate){
        if (mTransforming) {
            mPendingUpdate = new Runnable() {
                @Override
                public void run() {
                    findMe(coordinate);
                }
            };
        } else {
            map.setCenter(coordinate, Map.Animation.BOW);
            map.setZoomLevel(map.getMaxZoomLevel() - 1, Map.Animation.LINEAR);
        }
    }

    @Override
    public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {
        // ignored
    }

    @Override
    public void onMapTransformStart() {
        mTransforming = true;
    }

    @Override
    public void onMapTransformEnd(MapState mapState) {
        mTransforming = false;
        if (mPendingUpdate != null) {
            mPendingUpdate.run();
            mPendingUpdate = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
        case REQUEST_CODE_ASK_PERMISSIONS:
            for (int index = permissions.length - 1; index >= 0; --index) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Required permission '" + permissions[index] + "' not granted, exiting", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            initializeMapsAndPositioning();
            break;
        }
    }

    /**
     * Checks the dynamically controlled permissions and requests missing
     * permissions from end user.
     */
    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<>();
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.isEmpty()) {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                grantResults);
        } else {
            final String[] permissions = missingPermissions.toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        }
    }

    private MapFragment getMapFragment() {
        return (MapFragment)getFragmentManager().findFragmentById(R.id.mapfragment);
    }

    /**
     * Initializes HERE Maps and HERE Positioning. Called after permission check.
     */
    private void initializeMapsAndPositioning() {
        //mLocationInfo = (TextView) findViewById(R.id.textViewLocationInfo);
        mapFragment = getMapFragment();
        mapFragment.setRetainInstance(false);

        // Set path of isolated disk cache
        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath()
                + File.separator + ".isolated-here-maps";
        // Retrieve intent name from manifest
        String intentName = "";
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            intentName = bundle.getString("INTENT_NAME");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(this.getClass().toString(), "Failed to find intent name, NameNotFound: " + e.getMessage());
        }

        boolean success = com.here.android.mpa.common.MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot, intentName);
        if (!success) {
            // Setting the isolated disk cache was not successful, please check if the path is valid and
            // ensure that it does not match the default location
            // (getExternalStorageDirectory()/.here-maps).
            // Also, ensure the provided intent name does not match the default intent name.
        } else {
            mapFragment.init(new OnEngineInitListener() {
                @Override
                public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {
                    if (error == OnEngineInitListener.Error.NONE) {
                        map = mapFragment.getMap();
                        map.setCenter(new GeoCoordinate(45.81314250960471, 15.977296829223633, 0.0), Map.Animation.NONE);
                        map.setZoomLevel(map.getMaxZoomLevel() - 1);
                        map.addTransformListener(BasicPositioningActivity.this);
                        mPositioningManager = PositioningManager.getInstance();
                        mHereLocation = LocationDataSourceHERE.getInstance(
                                new StatusListener() {
                                    @Override
                                    public void onOfflineModeChanged(boolean offline) {
                                        // called when offline mode changes
                                    }

                                    @Override
                                    public void onAirplaneModeEnabled() {
                                        // called when airplane mode is enabled
                                    }

                                    @Override
                                    public void onWifiScansDisabled() {
                                        // called when Wi-Fi scans are disabled
                                    }

                                    @Override
                                    public void onBluetoothDisabled() {
                                        // called when Bluetooth is disabled
                                    }

                                    @Override
                                    public void onCellDisabled() {
                                        // called when Cell radios are switch off
                                    }

                                    @Override
                                    public void onGnssLocationDisabled() {
                                        // called when GPS positioning is disabled
                                    }

                                    @Override
                                    public void onNetworkLocationDisabled() {
                                        // called when network positioning is disabled
                                    }

                                    @Override
                                    public void onServiceError(ServiceError serviceError) {
                                        // called on HERE service error
                                    }

                                    @Override
                                    public void onPositioningError(PositioningError positioningError) {
                                        // called when positioning fails
                                    }

                                    @Override
                                    public void onWifiIndoorPositioningNotAvailable() {
                                        // called when running on Android 9.0 (Pie) or newer
                                    }
                                });
                        if (mHereLocation == null) {
                            Toast.makeText(BasicPositioningActivity.this, "LocationDataSourceHERE.getInstance(): failed, exiting", Toast.LENGTH_LONG).show();
                            finish();
                        }
                        mPositioningManager.setDataSource(mHereLocation);
                        mPositioningManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(
                                BasicPositioningActivity.this));
                        // start position updates, accepting GPS, network or indoor positions
                        if (mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)) {
                            mapFragment.getPositionIndicator().setVisible(true);
                        } else {
                            Toast.makeText(BasicPositioningActivity.this, "PositioningManager.start: failed, exiting", Toast.LENGTH_LONG).show();
                            finish();
                        }
                        initializeFindMeButton();
                        m_mapFragmentView = new MapFragmentView(BasicPositioningActivity.this, coordinateForNavi);
                    } else {
                        Toast.makeText(BasicPositioningActivity.this, "onEngineInitializationCompleted: error: " + error + ", exiting", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            });
        }
    }

    private static class RetrieveSuggestions extends AsyncTask<String, Void, String> {

        private Exception exception;

        protected String doInBackground(String... urls) {
            try {
                String ret = "";

                URL url;
                try {
                    HttpURLConnection con;
                    url = new URL(urls[0]);
                    con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    ret = response.toString();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                return ret;
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }

        protected void onPostExecute(String result) {
            results = result;
        }
    }


}
