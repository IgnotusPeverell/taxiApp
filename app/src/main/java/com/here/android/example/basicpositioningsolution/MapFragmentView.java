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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.guidance.NavigationManager;
import com.here.android.mpa.guidance.VoiceCatalog;
import com.here.android.mpa.guidance.VoiceGuidanceOptions;
import com.here.android.mpa.guidance.VoicePackage;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapFragment;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.Maneuver;
import com.here.android.mpa.routing.Route;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;

import java.io.File;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class encapsulates the properties and functionality of the Map view.It also triggers a
 * turn-by-turn navigation from HERE Burnaby office to Langley BC.There is a sample voice skin
 * bundled within the SDK package to be used out-of-box, please refer to the Developer's guide for
 * the usage.
 */
public class MapFragmentView {
    private MapFragment m_mapFragment;
    private Activity m_activity;
    private Button m_naviControlButton;
    private Button m_createRouteButton;
    private static Map m_map;
    private NavigationManager m_navigationManager;
    private static GeoBoundingBox m_geoBoundingBox;
    private static Route m_route;
    private boolean m_foregroundServiceStarted;
    private static GeoCoordinate m_coordinate;
    private static boolean routeCreated = false;
    private static GeoCoordinate m_destination = null;
    private static MapRoute oldRoute = null;
    private static long voiceId;
    private static AtomicBoolean isVoiceReady = new AtomicBoolean(false);
    private Switch m_voiceSwitch;

    MapFragmentView(Activity activity, GeoCoordinate myCoordinate) {
        m_activity = activity;
        m_coordinate = myCoordinate;
        initializeVoiceInstructions();
        initNaviControlButton();
        m_navigationManager = NavigationManager.getInstance();
        //new DownloadVoiceSkin().execute("");
    }

    public void setCoordinate(GeoCoordinate coordinate){
        m_coordinate = coordinate;
    }

    private MapFragment getMapFragment() {
        return (MapFragment) m_activity.getFragmentManager().findFragmentById(R.id.mapfragment);
    }

    private void initMapFragment() {
        /* Locate the mapFragment UI element */
        m_mapFragment = getMapFragment();

        // Set path of isolated disk cache
        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath()
                + File.separator + ".isolated-here-maps";
        // Retrieve intent name from manifest
        String intentName = "";
        try {
            ApplicationInfo ai = m_activity.getPackageManager().getApplicationInfo(m_activity.getPackageName(), PackageManager.GET_META_DATA);
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
            if (m_mapFragment != null) {
                /* Initialize the MapFragment, results will be given via the called back. */
                m_mapFragment.init(new OnEngineInitListener() {
                    @Override
                    public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {

                        if (error == Error.NONE) {
                            m_map = m_mapFragment.getMap();
                            /*m_map.setCenter(m_coordinate,
                                    Map.Animation.NONE);
                            //Put this call in Map.onTransformListener if the animation(Linear/Bow)
                            //is used in setCenter()
                            m_map.setZoomLevel(13.2);*/
                            /*
                             * Get the NavigationManager instance.It is responsible for providing voice
                             * and visual instructions while driving and walking
                             */
                        } else {
                            Toast.makeText(m_activity,
                                    "ERROR: Cannot initialize Map with error " + error,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }
    }

    private void createRoute() {

        if(m_destination != null && m_coordinate != null) {
            /* Initialize a CoreRouter */
            CoreRouter coreRouter = new CoreRouter();

            /* Initialize a RoutePlan */
            RoutePlan routePlan = new RoutePlan();

            /*
             * Initialize a RouteOption.HERE SDK allow users to define their own parameters for the
             * route calculation,including transport modes,route types and route restrictions etc.Please
             * refer to API doc for full list of APIs
             */
            RouteOptions routeOptions = new RouteOptions();
            /* Other transport modes are also available e.g Pedestrian */
            routeOptions.setTransportMode(RouteOptions.TransportMode.CAR);
            /* Disable highway in this route. */
            routeOptions.setHighwaysAllowed(true);
            /* Calculate the shortest route available. */
            routeOptions.setRouteType(RouteOptions.Type.SHORTEST);
            /* Calculate 1 route. */
            routeOptions.setRouteCount(1);
            routeOptions.setTollRoadsAllowed(true);
            routeOptions.setTunnelsAllowed(true);
            /* Finally set the route option */
            routePlan.setRouteOptions(routeOptions);

            /* Define waypoints for the route */
            RouteWaypoint startPoint = new RouteWaypoint(m_coordinate);
            /* END: Langley BC */
            RouteWaypoint destination = new RouteWaypoint(m_destination);

            /* Add both waypoints to the route plan */
            routePlan.addWaypoint(startPoint);
            routePlan.addWaypoint(destination);

            /* Trigger the route calculation,results will be called back via the listener */
            coreRouter.calculateRoute(routePlan,
                    new Router.Listener<List<RouteResult>, RoutingError>() {

                        @Override
                        public void onProgress(int i) {
                            /* The calculation progress can be retrieved in this callback. */
                        }

                        @Override
                        public void onCalculateRouteFinished(List<RouteResult> routeResults,
                                                             RoutingError routingError) {
                            /* Calculation is done.Let's handle the result */
                            if (routingError == RoutingError.NONE) {
                                if (routeResults.get(0).getRoute() != null) {

                                    m_route = routeResults.get(0).getRoute();
                                    /* Create a MapRoute so that it can be placed on the map */
                                    MapRoute mapRoute = new MapRoute(routeResults.get(0).getRoute());

                                    /* Show the maneuver number on top of the route */
                                    mapRoute.setManeuverNumberVisible(true);

                                    /* Add the MapRoute to the map */
                                    m_map.addMapObject(mapRoute);
                                    if(oldRoute != null)
                                        m_map.removeMapObject(oldRoute);
                                    oldRoute = mapRoute;
                                    /*
                                     * We may also want to make sure the map view is orientated properly
                                     * so the entire route can be easily seen.
                                     */
                                    m_geoBoundingBox = routeResults.get(0).getRoute().getBoundingBox();
                                    m_map.zoomTo(m_geoBoundingBox, Map.Animation.LINEAR,
                                            Map.MOVE_PRESERVE_ORIENTATION);
                                    routeCreated = true;
                                    EditText textview = (EditText) m_activity.findViewById(R.id.instructions);
                                    textview.setText("Lenght of this route is " + m_route.getLength() + "m");
                                } else {
                                    Toast.makeText(m_activity,
                                            "Error:route results returned is not valid",
                                            Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(m_activity,
                                        "Error:route calculation returned error code: " + routingError,
                                        Toast.LENGTH_LONG).show();

                            }
                        }
                    });
        }
        else if(m_destination == null){
            Toast.makeText(m_activity,
                    "Please chose destination first!",
                    Toast.LENGTH_LONG).show();

        }
        else {
            Toast.makeText(m_activity,
                    "You need to turn on navigation service!",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void initNaviControlButton() {
        m_naviControlButton = (Button) m_activity.findViewById(R.id.naviCtrlButton);
        m_createRouteButton = (Button)m_activity.findViewById(R.id.createRouteButton);
        m_createRouteButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                /*
                 * To start a turn-by-turn navigation, a concrete route object is required.We use
                 * the same steps from Routing sample app to create a route from 4350 Still Creek Dr
                 * to Langley BC without going on HWY.
                 *
                 * The route calculation requires local map data.Unless there is pre-downloaded map
                 * data on device by utilizing MapLoader APIs,it's not recommended to trigger the
                 * route calculation immediately after the MapEngine is initialized.The
                 * INSUFFICIENT_MAP_DATA error code may be returned by CoreRouter in this case.
                 *
                 */
                AutoCompleteTextView textView = (AutoCompleteTextView)m_activity.findViewById(R.id.autoCompleteTextView);
                m_destination = BasicPositioningActivity.getPlaceGeoCordinate(textView.getText().toString());
                initMapFragment();
                createRoute();

            }
        });

        m_naviControlButton.setOnClickListener(new View.OnClickListener() {
                                                   @Override
                                                   public void onClick(View v) {
                                                       if (oldRoute != null && isVoiceReady.get()) {
                                                           if (routeCreated) {
                                                               startNavigation();
                                                               routeCreated = false;
                                                               m_createRouteButton.setVisibility(View.INVISIBLE);
                                                           } else {
                                                               m_navigationManager.stop();
                                                               /*
                                                                * Restore the map orientation to show entire route on screen
                                                                */
                                                               m_map.zoomTo(m_geoBoundingBox, Map.Animation.LINEAR, 0f);
                                                               m_naviControlButton.setText(R.string.start_navi);
                                                               routeCreated = true;
                                                               m_createRouteButton.setVisibility(View.VISIBLE);
                                                           }
                                                       }
                                                   }
                                               }
        );

        m_voiceSwitch = (Switch) m_activity.findViewById(R.id.voiceSwitch);


        m_voiceSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(m_voiceSwitch.isChecked()){
                    m_navigationManager.getAudioPlayer().setVolume(NavigationManager.AudioPlayer.DEFAULT_AUDIO_VOLUME);
                    m_navigationManager.repeatVoiceCommand();
                }
                else {
                    m_navigationManager.getAudioPlayer().setVolume(0);
                }
            }
        });

    }

    /*
     * Android 8.0 (API level 26) limits how frequently background apps can retrieve the user's
     * current location. Apps can receive location updates only a few times each hour.
     * See href="https://developer.android.com/about/versions/oreo/background-location-limits.html
     * In order to retrieve location updates more frequently start a foreground service.
     * See https://developer.android.com/guide/components/services.html#Foreground
     */
    private void startForegroundService() {
        if (!m_foregroundServiceStarted) {
            m_foregroundServiceStarted = true;
            Intent startIntent = new Intent(m_activity, ForegroundService.class);
            startIntent.setAction(ForegroundService.START_ACTION);
            m_activity.getApplicationContext().startService(startIntent);
        }
    }

    private void stopForegroundService() {
        if (m_foregroundServiceStarted) {
            m_foregroundServiceStarted = false;
            Intent stopIntent = new Intent(m_activity, ForegroundService.class);
            stopIntent.setAction(ForegroundService.STOP_ACTION);
            m_activity.getApplicationContext().startService(stopIntent);
        }
        m_map.setTilt(m_map.getMinTilt());
    }

    private void startNavigation() {
        m_naviControlButton.setText(R.string.stop_navi);
        /* Configure Navigation manager to launch navigation on current map */
        m_navigationManager.setMap(m_map);


        /*
         * Start the turn-by-turn navigation.Please note if the transport mode of the passed-in
         * route is pedestrian, the NavigationManager automatically triggers the guidance which is
         * suitable for walking. Simulation and tracking modes can also be launched at this moment
         * by calling either simulate() or startTracking()
         */

        /* Choose navigation modes between real time navigation and simulation */
        /*AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(m_activity);
        alertDialogBuilder.setTitle("Navigation");
        alertDialogBuilder.setMessage("Choose Mode");
        alertDialogBuilder.setNegativeButton("Navigation",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int i) {*/
        m_navigationManager.startNavigation(m_route); //NAVIGATION!!
        m_map.setTilt(60);
        startForegroundService();
           /* };
        });
        alertDialogBuilder.setPositiveButton("Simulation",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int i) {*/
        //m_navigationManager.simulate(m_route, 25);//Simualtion speed is set to 25 m/s
        //m_map.setTilt(60);
        //startForegroundService();


            /*};
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();*/

        VoiceCatalog voiceCatalog = VoiceCatalog.getInstance();
        VoiceGuidanceOptions vgo = m_navigationManager.getVoiceGuidanceOptions();
        vgo.setVoiceSkin(voiceCatalog.getLocalVoiceSkin(voiceId));

        m_navigationManager.getAudioPlayer().setVolume(0);

        m_navigationManager.stopSpeedWarning();




        /*
         * Set the map update mode to ROADVIEW.This will enable the automatic map movement based on
         * the current location.If user gestures are expected during the navigation, it's
         * recommended to set the map update mode to NONE first. Other supported update mode can be
         * found in HERE Android SDK API doc
         */
        m_navigationManager.setMapUpdateMode(NavigationManager.MapUpdateMode.ROADVIEW);

        /*
         * NavigationManager contains a number of listeners which we can use to monitor the
         * navigation status and getting relevant instructions.In this example, we will add 2
         * listeners for demo purpose,please refer to HERE Android SDK API documentation for details
         */
        addNavigationListeners();
    }

    private void addNavigationListeners() {

        /*
         * Register a NavigationManagerEventListener to monitor the status change on
         * NavigationManager
         */
        m_navigationManager.addNavigationManagerEventListener(
                new WeakReference<NavigationManager.NavigationManagerEventListener>(
                        m_navigationManagerEventListener));

        /* Register a PositionListener to monitor the position updates */
        m_navigationManager.addPositionListener(
                new WeakReference<NavigationManager.PositionListener>(m_positionListener));

        m_navigationManager.addNewInstructionEventListener(
                new WeakReference<NavigationManager.NewInstructionEventListener>(m_instructionEventListener));
    }

    private NavigationManager.PositionListener m_positionListener = new NavigationManager.PositionListener() {
        @Override
        public void onPositionUpdated(GeoPosition geoPosition) {
            Maneuver maneuver = m_navigationManager.getNextManeuver();
            if (maneuver != null) {
                if (maneuver.getAction() != Maneuver.Action.END) {


                    String turn = (m_navigationManager.getNextManeuver().getTurn().name());
                    //turn = turn.split("_")[1].toLowerCase();

                    String roadName = (maneuver.getNextRoadName());

                    long distanceLong = m_navigationManager.getNextManeuverDistance();

                    Double toBeTruncated = (double) distanceLong;

                    if (toBeTruncated > 1000) {
                        toBeTruncated = toBeTruncated / 1000;
                    }
                    Double truncatedDouble = BigDecimal.valueOf(toBeTruncated)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();

                    String distance = String.valueOf(truncatedDouble);

                    EditText textview = (EditText) m_activity.findViewById(R.id.instructions);
                    if (distanceLong > 1000) {
                        textview.setText("In " + distance + "km turn " + turn + " on " + roadName);
                    } else {
                        textview.setText("In " + distance + "m turn " + turn + " on " + roadName);
                    }
                    String text3 = "debug point";
                }
                else {
                    long distance = m_navigationManager.getNextManeuverDistance();
                    EditText textview = (EditText) m_activity.findViewById(R.id.instructions);

                    if (distance > 10) {
                        textview.setText("Go for " + distance + "m to your destination");
                    }
                    else
                    {
                        textview.setText("You are at your destination!");
                    }
                }
            }
            geoPosition.getCoordinate();
            geoPosition.getHeading();
            geoPosition.getSpeed();

            // also remaining time and distance can be
            // fetched from navigation manager
            m_navigationManager.getTta(Route.TrafficPenaltyMode.DISABLED, true);
            m_navigationManager.getDestinationDistance();
        }
    };

    private NavigationManager.NavigationManagerEventListener m_navigationManagerEventListener = new NavigationManager.NavigationManagerEventListener() {
        @Override
        public void onRunningStateChanged() {
            Toast.makeText(m_activity, "Running state changed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onNavigationModeChanged() {
            Toast.makeText(m_activity, "Navigation mode changed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onEnded(NavigationManager.NavigationMode navigationMode) {
            Toast.makeText(m_activity, navigationMode + " was ended", Toast.LENGTH_SHORT).show();
            stopForegroundService();
        }

        @Override
        public void onMapUpdateModeChanged(NavigationManager.MapUpdateMode mapUpdateMode) {
            Toast.makeText(m_activity, "Map update mode is changed to " + mapUpdateMode,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRouteUpdated(Route route) {
            MapRoute newRoute = new MapRoute(route);
            m_map.addMapObject(newRoute);
            if(oldRoute != null)
                m_map.removeMapObject(oldRoute);
            oldRoute = newRoute;
            Toast.makeText(m_activity, "Route updated", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCountryInfo(String s, String s1) {
            Toast.makeText(m_activity, "Country info updated from " + s + " to " + s1,
                    Toast.LENGTH_SHORT).show();
        }
    };

    private NavigationManager.NewInstructionEventListener m_instructionEventListener = new NavigationManager.NewInstructionEventListener() {
        @Override
        public void onNewInstructionEvent() {
            m_navigationManager.getNextManeuver();
        }
    };

    public void onDestroy() {
        /* Stop the navigation when app is destroyed */
        if (m_navigationManager != null) {
            stopForegroundService();
            m_navigationManager.stop();
        }
    }

    private void initializeVoiceInstructions(){
        VoiceCatalog voiceCatalog = VoiceCatalog.getInstance();

        voiceCatalog.downloadCatalog(new VoiceCatalog.OnDownloadDoneListener() {
            @Override
            public void onDownloadDone(VoiceCatalog.Error error) {
                if (error == VoiceCatalog.Error.NONE) {

                    List<VoicePackage> voicePackages = VoiceCatalog.getInstance().getCatalogList();

                    long id = -1;

                    // select
                    for (VoicePackage vPackage : voicePackages) {
                        if (vPackage.getMarcCode().compareToIgnoreCase("eng") == 0) {
                            if (!vPackage.isTts() && vPackage.getGender().equals(VoicePackage.Gender.FEMALE)) {
                                id = vPackage.getId();
                                voiceId = id;
                                break;
                            }
                        }
                    }

                    voiceCatalog.setOnProgressEventListener(new VoiceCatalog.OnProgressListener() {
                        @Override
                        public void onProgress(int i) {
                            EditText textview = (EditText) m_activity.findViewById(R.id.instructions);
                            textview.setText("Downloaded: "+i+"%");
                        }
                    });

                    if (!voiceCatalog.isLocalVoiceSkin(id)) {
                        voiceCatalog.downloadVoice(id, new VoiceCatalog.OnDownloadDoneListener() {
                            @Override
                            public void onDownloadDone(VoiceCatalog.Error errorCode) {
                                if (errorCode == VoiceCatalog.Error.NONE) {
                                    Toast.makeText(m_activity, "English voice download success", Toast.LENGTH_SHORT).show();
                                    EditText textview = (EditText) m_activity.findViewById(R.id.instructions);
                                    textview.setText("Finished Downloading");
                                    isVoiceReady.set(true);
                                }
                                else {
                                    Toast.makeText(m_activity, "English voice fail", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                    else{
                        isVoiceReady.set(true);
                    }
                }
                else {
                    Toast.makeText(m_activity, "Catalog download fail", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
