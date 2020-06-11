package com.fric.outdoorsmanswaypointplotter;

import  com.fric.outdoorsmanswaypointplotter.R;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MapsActivity extends FragmentActivity implements SensorEventListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    static final LatLng HAMBURG = new LatLng(53.558, 9.927);
    static final LatLng KIEL = new LatLng(53.551, 9.993);

    static final double EQUATOR_LENGTH_METERS = 40075004;

    double circumference_at_latitude;

    double metersPerPixel = 2;

    AlertDialog dialogNewMarker;
    AlertDialog dialogDeleteMarker;
    AlertDialog alertDialog;

    private LocationManager locationManager;

    Marker markerGlobal;
    Marker markerTemp;

    List<Marker> markerList = new ArrayList<>();

    SensorManager mSensorManager;
    Sensor accelerometer;
    Sensor magnetometer;

    String provider;

    Circle circle;
    Polyline lineBearing;
    Polyline lineMeasure;

    List<LatLng> lineBearingPoints;
    List<LatLng> lineMeasurePoints;

    LatLng latLngLongClick;

    static final int VOICE_RECOG_LONGLICK = 876;
    static final int VOICE_RECOG_CURRENTLOC = 963;

    double bearing;

    float[] mGravity;
    float[] mGeomagnetic;
    Float azimut;

    enum RangeUnits { METERS, FEET, KILOMETERS, MILES }
    RangeUnits rangeUnits;
    float rangeRadius;
    boolean showRange;

    public static final String MyPREFERENCES = "MyPrefs" ;
    public static final String MapTypePref = "mapTypeKey";
    public static final String RangeUnitsPref = "rangeUnitsKey";
    public static final String RangeRadiusPref = "rangeRadiusKey";
    public static final String ShowRangePref = "showRangeKey";
    public static final String CurrentFilePref = "currentFileKey";

    public static final String EULA_Agreed = "EULA_AgreedKey";

    final static float METERS_PER_FOOT = .3048f;
    final static float METERS_PER_MILE = 1609.34f;
    final static float METERS_PER_KILOMETER = 1000;

    ToggleButton toggleButtonCenter;
    ToggleButton toggleButtonMeasure;
    TextView textViewMeasure;
    TextView textViewRangeRadius;

    private boolean locationKnown;

    boolean autoCenter;

    String currentFile;

    final static String APP_NAME = "Outdoorsman's Waypoint Plotter";

    Handler handlerSavePeriodic = new Handler();

    Runnable runnableSavePeriodic = new Runnable() {
        @Override
        public void run() {
            if (markerList.size() > 0)
                saveMarkers(null);

            handlerSavePeriodic.postDelayed(runnableSavePeriodic, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        //final AdRequest adRequest = new AdRequest.Builder().build();
        final AdRequest adRequest = new AdRequest.Builder().addTestDevice("ABCDEFG1234567890").build();
        mAdView.loadAd(adRequest);


        mAdView.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                super.onAdClosed();
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();

                if (adRequest.getContentUrl() != null)
                    Log.i("mAdView.setAdListener", adRequest.getContentUrl());
                else
                    Log.i("mAdView.setAdListener", "Ad Content is null.");
            }
        });


        locationKnown = false;

        toggleButtonCenter = (ToggleButton)findViewById(R.id.toggleButtonCenter);
        toggleButtonMeasure = (ToggleButton)findViewById(R.id.toggleButtonMeasure);
        textViewMeasure = (TextView)findViewById(R.id.textViewDistance);
        textViewRangeRadius = (TextView)findViewById(R.id.textViewRangeRadius);

        textViewMeasure.setText("");
        textViewRangeRadius.setText("");

        setUpMapIfNeeded();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        showEULADialog();

        if (locationManager == null) {
            Log.i("onCreate", "Location Manager is null.");
            Toast.makeText(getApplicationContext(),"Error! Location not found.  Is GPS enabled?", Toast.LENGTH_LONG).show();
            //finish();

            /*
            String filename = "myfile.txt";
            String string = "Hello world Again!";
            FileOutputStream outputStream;

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append("44.1");
            stringBuilder.append(",");
            stringBuilder.append("-93.1");
            stringBuilder.append(",");
            stringBuilder.append( "Marker 1");
            stringBuilder.append(System.getProperty("line.separator"));
            stringBuilder.append("44.2");
            stringBuilder.append(",");
            stringBuilder.append("-93.2");
            stringBuilder.append(",");
            stringBuilder.append( "Marker 2");
            stringBuilder.append(System.getProperty("line.separator"));
            stringBuilder.append("44.3");
            stringBuilder.append(",");
            stringBuilder.append("-93.3");
            stringBuilder.append(",");
            stringBuilder.append( "Marker 3");
            stringBuilder.append(System.getProperty("line.separator"));

            Log.v("onCreate", "Appending: " + stringBuilder.toString());

            saveMarkers(null);
            loadMarkers(filename);
            */

            //try {
                /**
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(stringBuilder.toString().getBytes());
                outputStream.close();

                File file = new File(filename);

                InputStream inputStream = new FileInputStream(getFilesDir() + "/" + file);
                BufferedReader r = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder total = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    total.append(line);
                }
                Log.v("onCreate", "FILE CONTENTS: " + total.toString());
                **/

                /**

                Toast.makeText(getApplicationContext(),"FILE CONTENTS: " + total.toString(), Toast.LENGTH_LONG).show();



                String fileName2 = "anotherfile.txt";
                outputStream = openFileOutput(fileName2, Context.MODE_PRIVATE);
                outputStream.write(total.toString().getBytes());
                outputStream.close();
                **/

            /*
                saveMarkers(null);
                loadMarkers(filename);

            } catch (Exception e) {
                e.printStackTrace();
            }
            */

            return;
        }


    }

    private void showEULADialog()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);

        if (sharedPreferences.contains(EULA_Agreed))
        {
            if (sharedPreferences.getBoolean(EULA_Agreed, false))
                return;
        }

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_eula, null);

        final CheckBox checkBoxEULA_Agreed = (CheckBox)view.findViewById(R.id.checkBoxAcceptEULA);

        final SharedPreferences.Editor editor = sharedPreferences.edit();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("User Agreement")
                .setView(view)
                .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (checkBoxEULA_Agreed.isChecked()) {
                            editor.putBoolean(EULA_Agreed, true);

                            editor.commit();
                        } else {
                            finish();
                        }

                    }
                })
                .setNegativeButton("Decline", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });

        alertDialog = builder.create();

        alertDialog.show();

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);

        checkBoxEULA_Agreed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(checkBoxEULA_Agreed.isChecked());
            }
        });
    }

    private void showEditMarkerDialog(final Marker marker) {

        markerTemp = marker;

        //Toast.makeText(getApplicationContext(), "You Clicked Me Twice!", Toast.LENGTH_SHORT).show();

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_enterpoint, null);

        final EditText editTextLatitude = (EditText)view.findViewById(R.id.editText_latitude);
        final EditText editTextLongitude = (EditText)view.findViewById(R.id.editText_longitude);
        final EditText editTextNotes = (EditText)view.findViewById(R.id.editText_pointNotes);

        editTextLatitude.setText(String.valueOf(marker.getPosition().latitude));
        editTextLongitude.setText(String.valueOf(marker.getPosition().longitude));
        editTextNotes.setText(marker.getTitle());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Edit Point")
                .setView(view)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (int i = 0; i < markerList.size(); i++) {
                            if (markerList.get(i).getPosition().latitude == markerTemp.getPosition().latitude
                                    && markerList.get(i).getPosition().longitude == markerTemp.getPosition().longitude) {

                                LatLng latLng = new LatLng(Double.parseDouble(editTextLatitude.getText().toString()), Double.parseDouble(editTextLongitude.getText().toString()));

                                if (latLng.latitude < 90 && latLng.latitude > -90) {

                                    markerTemp.setPosition(latLng);
                                    markerTemp.setTitle(editTextNotes.getText().toString());

                                    markerList.set(i, markerTemp);

                                    float zoom = calculateZoomLevel(0, getRangeInMeters() * 8);

                                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

                                    mMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));

                                    saveMarkers(null);
                                } else
                                    Toast.makeText(getApplicationContext(), "Invalid coordinates.", Toast.LENGTH_SHORT).show();

                                //Log.i("prickly_ash_locator","Marker deleted.  Markers left: " + String.valueOf(markerList.size()));

                                break;
                            }
                        }
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        for (int i = 0; i < markerList.size(); i++) {
                            if (markerList.get(i).getPosition().latitude == markerTemp.getPosition().latitude
                                    && markerList.get(i).getPosition().longitude == markerTemp.getPosition().longitude) {
                                markerList.remove(i);

                                //Log.i("prickly_ash_locator","Marker deleted.  Markers left: " + String.valueOf(markerList.size()));

                                break;
                            }
                        }

                        markerTemp.remove();

                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        dialogDeleteMarker = builder.create();

        dialogDeleteMarker.show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu1) {

        try {
            getMenuInflater().inflate(R.menu.maps_menu, menu1);
        } catch (Exception e){
            e.printStackTrace();
        }


        return true;
    }




    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();

        handlerSavePeriodic.postDelayed(runnableSavePeriodic, 10000);


        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);

        /*mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
                */

        Log.i("onPause", "MapsActivity RESUMED!!!");

    }

    @Override
    protected void onPause() {
        super.onPause();

        /*
        if (markerList.size() > 0)
            saveMarkers(null);
        */

        handlerSavePeriodic.removeCallbacksAndMessages(null);

        mSensorManager.unregisterListener(this);
        Log.i("onPause","MapsActivity PAUSED!!!");
    }

    //Add a colony at current location.  Called from XML Layout.
    public void addAtCurrentLocation(View view)
    {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, true);

        // Get Current Location
        //Location myLocation = locationManager.getLastKnownLocation(provider);
        if (locationManager != null) {
            /*
            double latitude = myLocation.getLatitude();
            double longitude = myLocation.getLongitude();
            LatLng latLng = new LatLng(latitude, longitude);
            */

            //showAddLocationDialog(null);

            showAddLocationVoiceDialog(null);
        } else
        {
            Toast.makeText(getApplicationContext(),"Searching for location, please wait...", Toast.LENGTH_SHORT).show();
        }
    }

    public void showAddLocationVoiceDialog(final LatLng latLng)
    {
        Intent intent = new Intent(this, VoiceActivity.class);

        if (latLng == null)
            startActivityForResult(intent, VOICE_RECOG_CURRENTLOC);
        else {
            latLngLongClick = latLng;
            startActivityForResult(intent, VOICE_RECOG_LONGLICK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //Toast.makeText(getApplicationContext(),"Got result from activity: " + String.valueOf(requestCode), Toast.LENGTH_SHORT).show();

        if (resultCode == RESULT_CANCELED)
            return;

        if (data != null)
            Toast.makeText(getApplicationContext(),"Location Notes Added: " + data.getStringExtra("VoiceString"),Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(getApplicationContext(),"Data was null.",Toast.LENGTH_SHORT).show();

        LatLng latLngNewMarker = null;

        if (requestCode == VOICE_RECOG_LONGLICK)
        {
            latLngNewMarker = latLngLongClick;
        }

        if (requestCode == VOICE_RECOG_CURRENTLOC && locationManager != null)
        {
            latLngNewMarker = getLatLng();
        }

        if (latLngNewMarker != null && locationManager != null)
        {
            String titleString;

            try{
                titleString = data.getStringExtra("VoiceString");
            } catch (NullPointerException e) {
                titleString = "";
            }

            if (titleString == null)
                titleString = "";

            Marker myMarker = mMap.addMarker(new MarkerOptions().position(latLngNewMarker).title(titleString));

            markerList.add(myMarker);

            saveMarkers(null);

        }

    }

    public void showAddLocationDialog(final LatLng latLng)
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_descrption, null);

        final RadioGroup radioGroupCount = (RadioGroup) view.findViewById(R.id.radioGroupPlantCount);
        final RadioGroup radioGroupSize = (RadioGroup) view.findViewById(R.id.radioGroupPlantSize);
        final RadioGroup radioGroupAge = (RadioGroup) view.findViewById(R.id.radioGroupPlantAge);

        builder.setTitle("Enter Description")
                .setView(view)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        String count = "";
                        String size = "";
                        String age = "";

                        if (radioGroupCount.getCheckedRadioButtonId() != -1)
                            count = ((RadioButton)view.findViewById(radioGroupCount.getCheckedRadioButtonId())).getText().toString();
                        if (radioGroupSize.getCheckedRadioButtonId() != -1)
                            size = ((RadioButton)view.findViewById(radioGroupSize.getCheckedRadioButtonId())).getText().toString();
                        if (radioGroupAge.getCheckedRadioButtonId() != -1)
                            age = ((RadioButton)view.findViewById(radioGroupAge.getCheckedRadioButtonId())).getText().toString();

                        LatLng latLngNew;

                        if (latLng == null)
                            latLngNew = getLatLng();
                        else
                            latLngNew = latLng;

                        String latitude = String.valueOf(latLngNew.latitude);

                        Marker myMarker = mMap.addMarker(new MarkerOptions().position(latLngNew).title(count + ", " + size + ", " + age));

                        markerList.add(myMarker);

                        saveMarkers(null);

                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        dialogNewMarker = builder.create();

        dialogNewMarker.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (dialogNewMarker != null)
            if (dialogNewMarker.isShowing())
                dialogNewMarker.dismiss();

        if (dialogDeleteMarker != null)
            if (dialogDeleteMarker.isShowing())
                dialogDeleteMarker.dismiss();

        if (alertDialog != null)
            if (alertDialog.isShowing())
                alertDialog.dismiss();

        dialogNewMarker = null;
        dialogDeleteMarker = null;
        alertDialog = null;

        Log.i("onPause","MapsActivity DESTROYED!!!");
    }



    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    /*
    private void setUpMap() {
        mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }
    */

    private void setUpMap() {
        // Enable MyLocation Layer of Google Map
        mMap.setMyLocationEnabled(true);

        // Get LocationManager object from System Service LOCATION_SERVICE
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Create a criteria object to retrieve provider
        Criteria criteria = new Criteria();

        // Get the name of the best provider
        provider = locationManager.getBestProvider(criteria, true);

        //set map type
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setAllGesturesEnabled(true);

        //Load Shared Preferences here.
        {
            SharedPreferences sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);

            if (sharedPreferences.contains(MapTypePref)) {
                switch (sharedPreferences.getString(MapTypePref, "Normal")) {
                    case "Normal":
                        setMapTypeNormal(null);
                        break;
                    case "Terrain":
                        setMapTypeTerrain(null);
                        break;
                    case "Hybrid":
                        setMapTypeHybrid(null);
                        break;
                    case "Satellite":
                        setMapTypeSatellite(null);
                        break;
                    default:
                        setMapTypeNormal(null);
                        break;

                }
            } else
                setMapTypeNormal(null);

            if (sharedPreferences.contains(RangeUnitsPref)) {
                switch (sharedPreferences.getString(RangeUnitsPref, "Meters")) {
                    case "Meters":
                        rangeUnits = RangeUnits.METERS;
                        break;
                    case "Feet":
                        rangeUnits = RangeUnits.FEET;
                        break;
                    case "Miles":
                        rangeUnits = RangeUnits.MILES;
                        break;
                    case "Kilometers":
                        rangeUnits = RangeUnits.KILOMETERS;
                        break;
                    default:
                        rangeUnits = RangeUnits.METERS;
                        break;

                }
            } else
                rangeUnits = RangeUnits.METERS;

            if (sharedPreferences.contains(RangeRadiusPref)) {
                rangeRadius = sharedPreferences.getFloat(RangeRadiusPref, 100);
            }
            else
                rangeRadius = 100;

            if (sharedPreferences.contains(ShowRangePref)) {
                showRange = sharedPreferences.getBoolean(ShowRangePref, true);
            }
            else
                showRange = true;

            if (sharedPreferences.contains(CurrentFilePref)) {
                currentFile = sharedPreferences.getString(CurrentFilePref, "Waypoint_Output.txt");
            }
            else
                currentFile = "Waypoint_Output.txt";

            Log.i("Load Preferences","Shared Preferences currentFile: " + sharedPreferences.getString(CurrentFilePref,"Some stuff"));
        }

        // Get Current Location
        final Location myLocation = locationManager.getLastKnownLocation(provider);

        if (myLocation != null)
        {
            locationKnown = true;
            setUpMapFuctionsThatRequireLocation(myLocation);
        } else
        {
            Toast.makeText(getApplicationContext(),"Searching for location... Please wait", Toast.LENGTH_SHORT).show();
        }

        lineBearingPoints = new ArrayList<LatLng>();

        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {

                if (location == null)
                {
                    Toast.makeText(getApplicationContext(),"No Location found... Searching...", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (locationKnown == false)
                {
                    setUpMapFuctionsThatRequireLocation(location);
                    Toast.makeText(getApplicationContext(),"Location found!", Toast.LENGTH_SHORT).show();
                    locationKnown = true;
                }

                /*
                Log.i("OnMyLocationChange","Location: " + location.toString());
                Log.i("OnMyLocationChange","Contents: " + String.valueOf(location.describeContents()));
                Log.i("OnMyLocationChange","Provider: " + String.valueOf(location.getProvider().toString()));
                */

                LatLng latLng;

                try {
                    latLng = getLatLng();
                } catch (Exception e)
                {
                    Log.w("OnMyLocationChange","Trouble getting location... Attemping alternate: ");
                    String locationString = String.valueOf(location.toString());

                    final CharSequence input = locationString;
                    final StringBuilder sb = new StringBuilder(input.length());
                    for(int i = 0; i < input.length(); i++){
                        final char c = input.charAt(i);
                        if(c > 47 && c < 58 || c == 44 || c == 46){
                            sb.append(c);
                        }
                    }

                    locationString = sb.toString();

                    Log.w("OnMyLocationChange","Alternate Results: " + locationString);

                    double latitude = Double.parseDouble(locationString.split(",")[0]);
                    double longitude = Double.parseDouble(locationString.split(",")[1]);

                    latLng = new LatLng(latitude,longitude);

                    Log.w("OnMyLocationChange","Skipping the rest of listener.");




                    return;


                }

                provider = locationManager.getBestProvider(new Criteria(), true);

                //Bearing from Compass
                double relativeBearing = bearing ;

                //double relativeBearing = locationManager.getLastKnownLocation(provider).getBearing();


                //double azimuth = relativeBearing; // get azimuth from the orientation sensor (it's quite simple)
                Location currentLoc = locationManager.getLastKnownLocation(provider);// get location from GPS or network
                // convert radians to degrees
                //azimuth = Math.toDegrees(azimuth);
                GeomagneticField geoField = new GeomagneticField(
                        (float) currentLoc.getLatitude(),
                        (float) currentLoc.getLongitude(),
                        (float) currentLoc.getAltitude(),
                        System.currentTimeMillis());
                float declination = geoField.getDeclination();

                if (locationManager == null || azimut == null || azimut.isNaN() )
                    return;

                double trueBearing = 180 * azimut / Math.PI + declination;

                //bearing += geoField.getDeclination(); // converts magnetic north into true north
                //float bearing = currentLoc.bearingTo(target); // (it's already in degrees)
                //double direction = azimuth - bearing;

                /*
                Log.v("OnMyLocationChangedListener","Bearing Physical: " + String.valueOf(bearing));
                Log.v("OnMyLocationChangedListener","Bearing Map     : " + String.valueOf(mMap.getCameraPosition().bearing));
                Log.v("OnMyLocationChangedListener","True Bearing    : " + String.valueOf(trueBearing));
                */

                if (toggleButtonCenter.isChecked())
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(getLatLng()));

                if (showRange)
                {
                    createRangeRadius(latLng);

                }
                else {
                    if (circle != null) {
                        circle.remove();
                        circle = null;
                    }
                    if (lineBearing != null) {
                        lineBearing.remove();
                        lineBearing = null;
                    }
                }

                if (mMap != null) {
                    if (circle != null) {
                        circle.setCenter(latLng);
                        circle.setRadius(getRangeInMeters());
                    }

                    if (lineBearing != null) {
                        lineBearingPoints.clear();
                        lineBearingPoints.add(latLng);

                        circumference_at_latitude = Math.cos(latLng.latitude)*EQUATOR_LENGTH_METERS;

                        double meters_per_degree_longitude = circumference_at_latitude / 360;
                        double meters_per_degree_latitude = 111131.745;

                        double delta_latitude = getRangeInMeters() / meters_per_degree_latitude * Math.cos(trueBearing * Math.PI / 180);
                        double delta_longitude = getRangeInMeters() / meters_per_degree_longitude * Math.sin(trueBearing * Math.PI / 180);

                        lineBearingPoints.add(new LatLng(latLng.latitude + delta_latitude, latLng.longitude + delta_longitude));

                        lineBearing.setPoints(lineBearingPoints);
                    }
                }

            }
        });



        String units_readable = getUnitsReadable();


        BigDecimal bigDecimal_rangeRadius = new BigDecimal(rangeRadius);
        bigDecimal_rangeRadius = bigDecimal_rangeRadius.round(new MathContext(5, RoundingMode.HALF_UP)).stripTrailingZeros();

        textViewRangeRadius.setText("Range: " + bigDecimal_rangeRadius.toPlainString() + " " + units_readable);

        float zoom;

        /*
        int screenWidth;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            screenWidth = size.x;
        }
        else
        {
            screenWidth = getWindowManager().getDefaultDisplay().getWidth();
        }

        zoom = calculateZoomLevel(screenWidth, getRangeInMeters());
        */

        zoom = calculateZoomLevel(0, getRangeInMeters() * 4);

        //zoom = calculateZoomLevel(1000);

        if (locationManager != null)
            mMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));

    }

    private void setUpMapFuctionsThatRequireLocation(Location myLocation) {

        //FragmentManager fm = getFragmentManager();

        Criteria criteria = new Criteria();

        provider = locationManager.getBestProvider(criteria, true);

        if (provider == null)
            Log.i("onCreate", "Provider is null.");


        lineMeasurePoints = new ArrayList<LatLng>();

        toggleButtonCenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleButtonCenter.isChecked())
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(getLatLng()));
            }
        });

        toggleButtonMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleButtonMeasure.isChecked()) {


                } else {
                    if (lineMeasure != null)
                    {
                        lineMeasure.remove();
                        lineMeasure = null;
                    }
                    if (lineMeasurePoints.size() > 0)
                        lineMeasurePoints.clear();
                }
            }
        });

        android.support.v4.app.FragmentManager myFragmentManager = getSupportFragmentManager();
        SupportMapFragment mySupportMapFragment
                = (SupportMapFragment)myFragmentManager.findFragmentById(R.id.map);

        mMap = mySupportMapFragment.getMap();

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {

                latLngLongClick = latLng;

                //showAddLocationDialog(latLng);
                showAddLocationVoiceDialog(latLng);
            }
        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {

                markerTemp = marker;

                if (markerGlobal != null && marker.getPosition().latitude == markerGlobal.getPosition().latitude &&
                        marker.getPosition().longitude == markerGlobal.getPosition().longitude )
                {
                    showEditMarkerDialog(marker);

                }

                markerGlobal = marker;

                return false;
            }


        });

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {

                //Possibly include code for displaying map distance in meters later.

                metersPerPixel = EQUATOR_LENGTH_METERS / Math.pow(2,mMap.getCameraPosition().zoom);

            }
        });

        //On Location Change First Time

        // Get latitude of the current location
        double latitude = myLocation.getLatitude();

        // Get longitude of the current location
        double longitude = myLocation.getLongitude();

        // Create a LatLng object for the current location
        LatLng latLng = new LatLng(latitude, longitude);

        // Show the current location in Google Map
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        // Zoom in the Google Map
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
        //mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("You are here!"));

        /*
        // Instantiates a new CircleOptions object and defines the center and radius
        final CircleOptions circleOptions = new CircleOptions()
                .strokeWidth(1)
                .center(latLng)
                .radius(getRangeInMeters()); // In meters

        // Get back the mutable Circle
        circle = mMap.addCircle(circleOptions);

        lineBearing = mMap.addPolyline(new PolylineOptions()
                .add(latLng, latLng)
                .width(1)
                .color(Color.BLACK)
                .geodesic(true));
        */



        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                Log.i("setOnMapClickListener","Map Clicked!");
                if (toggleButtonMeasure.isChecked())
                {
                    int strokeWidth = 5;

                    if (lineMeasurePoints.size() == 0) {
                        double pixelsAcrossEquator = 256 * Math.pow(2, mMap.getCameraPosition().zoom);
                        double pixelsPerDegree = pixelsAcrossEquator / 360;

                        double deltaLong = strokeWidth / pixelsPerDegree;


                        LatLng latLng2 = new LatLng(latLng.latitude, latLng.longitude + deltaLong / 2);

                        if (lineMeasure != null)
                        {
                            lineMeasure.remove();
                            lineMeasure = null;
                            lineMeasurePoints.clear();
                        }

                        lineMeasure = mMap.addPolyline(new PolylineOptions()
                                .add(latLng, latLng2)
                                .width(strokeWidth)
                                .color(R.color.BLUE_TRANSPARENT)
                                .geodesic(true));

                        lineMeasurePoints.add(latLng);
                    } else if (lineMeasurePoints.size() == 1) {
                        lineMeasurePoints.add(latLng);

                        if (lineMeasure != null) {
                            lineMeasure.setPoints(lineMeasurePoints);
                        }

                        circumference_at_latitude = Math.cos(latLng.latitude)*EQUATOR_LENGTH_METERS;

                        double meters_per_degree_longitude = circumference_at_latitude / 360;
                        double meters_per_degree_latitude = 111131.745;

                        double deltaLatitude_Meters = meters_per_degree_latitude * (lineMeasurePoints.get(1).latitude - lineMeasurePoints.get(0).latitude);
                        double deltaLongitude_Meters = meters_per_degree_longitude * (lineMeasurePoints.get(1).longitude - lineMeasurePoints.get(0).longitude);

                        double distance_Meters = Math.sqrt(deltaLatitude_Meters * deltaLatitude_Meters + deltaLongitude_Meters * deltaLongitude_Meters);

                        double distance_Converted;
                        String units_readable;

                        switch (rangeUnits) {
                            case METERS:
                                distance_Converted = distance_Meters;
                                units_readable = "m";
                                break;
                            case FEET:
                                distance_Converted = distance_Meters / METERS_PER_FOOT;
                                units_readable = "ft";
                                break;
                            case MILES:
                                distance_Converted = distance_Meters / METERS_PER_MILE;
                                units_readable = "mi";
                                break;
                            case KILOMETERS:
                                distance_Converted = distance_Meters / METERS_PER_KILOMETER;
                                units_readable = "km";
                                break;
                            default:
                                distance_Converted = distance_Meters;
                                units_readable = "m";
                                break;
                        }

                        BigDecimal bigDecimal_distance_Converted = new BigDecimal(distance_Converted);
                        bigDecimal_distance_Converted = bigDecimal_distance_Converted.round(new MathContext(4, RoundingMode.HALF_UP));

                        textViewMeasure.setText("Distance: " + bigDecimal_distance_Converted.toPlainString() + " " + units_readable);



                    } else
                    {
                        lineMeasure.remove();
                        lineMeasure = null;
                        lineMeasurePoints.clear();
                        textViewMeasure.setText("");

                    }

                }
            }
        });

        Log.i("setUpMapFuctionsThatRequireLocation","Loading file: " + currentFile);

        loadMarkers(currentFile);
    }

    private String getUnitsReadable() {

        String units_readable;

        switch (rangeUnits) {
            case METERS:
                units_readable = "m";
                break;
            case FEET:
                units_readable = "ft";
                break;
            case MILES:
                units_readable = "mi";
                break;
            case KILOMETERS:
                units_readable = "km";
                break;
            default:
                units_readable = "m";
                break;
        }
        return units_readable;
    }

    public void createRangeRadius(LatLng latLng)
    {
        if (circle == null) {
            final CircleOptions circleOptions = new CircleOptions()
                    .strokeWidth(1)
                    .center(latLng)
                    .radius(getRangeInMeters()); // In meters
            circle = mMap.addCircle(circleOptions);
        }

        if (lineBearing == null) {
            lineBearing = mMap.addPolyline(new PolylineOptions()
                    .add(latLng, latLng)
                    .width(1)
                    .color(Color.BLACK)
                    .geodesic(true));

        }
    }

    public void setMapTypeNormal(MenuItem item)
    {
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    }

    public void setMapTypeTerrain(MenuItem item)
    {
        mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
    }

    public void setMapTypeHybrid(MenuItem item)
    {
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    }

    public void setMapTypeSatellite(MenuItem item)
    {
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
    }

    public void showEnterPoint(MenuItem item)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_enterpoint, null);

        //Views in Layout file go here (e.g. checkbox, radiobutton etc)

        final EditText editTextLatitude = (EditText) view.findViewById(R.id.editText_latitude);
        final EditText editTextLongitude = (EditText) view.findViewById(R.id.editText_longitude);
        final EditText editTextPointNotes = (EditText) view.findViewById(R.id.editText_pointNotes);

        editTextLatitude.setText(String.valueOf(locationManager.getLastKnownLocation(provider).getLatitude()));
        editTextLongitude.setText(String.valueOf(locationManager.getLastKnownLocation(provider).getLongitude()));

        builder.setTitle("Enter Point")
                .setView(view)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LatLng latLng = new LatLng(Double.parseDouble(editTextLatitude.getText().toString()), Double.parseDouble(editTextLongitude.getText().toString()));

                        if (latLng.latitude < 90 && latLng.latitude > -90) {

                            Marker myMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(editTextPointNotes.getText().toString()));

                            markerList.add(myMarker);

                            float zoom = calculateZoomLevel(0, getRangeInMeters() * 8);

                            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

                            mMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));

                            saveMarkers(null);
                        }
                        else
                            Toast.makeText(getApplicationContext(), "Invalid coordinates.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        alertDialog = builder.create();

        alertDialog.show();
    }

    public void showMapOptions(MenuItem item)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_rangeoptions, null);

        //Views in Layout file go here (e.g. checkbox, radiobutton etc)
        final RadioGroup radioGroupMapType = (RadioGroup) view.findViewById(R.id.radioGroup_maptype);
        final RadioButton radioButtonNormal = (RadioButton) view.findViewById(R.id.radioButton_maptype_normal);
        final RadioButton radioButtonTerrain = (RadioButton) view.findViewById(R.id.radioButton_maptype_terrain);
        final RadioButton radioButtonHybrid = (RadioButton) view.findViewById(R.id.radioButton_maptype_hybrid);
        final RadioButton radioButtonSatellite = (RadioButton) view.findViewById(R.id.radioButton_maptype_satellite);

        final RadioGroup radioGroupRangeUnits = (RadioGroup) view.findViewById(R.id.radioGroup_rangeUnits);
        final RadioButton radioButtonMeters = (RadioButton) view.findViewById(R.id.radioButton_units_meters);
        final RadioButton radioButtonFeet = (RadioButton) view.findViewById(R.id.radioButton_units_feet);
        final RadioButton radioButtonKilometers = (RadioButton) view.findViewById(R.id.radioButton_units_km);
        final RadioButton radioButtonMiles = (RadioButton) view.findViewById(R.id.radioButton_units_miles);

        final EditText editTextRangeRadius = (EditText) view.findViewById(R.id.editText_range_radius);
        final CheckBox checkBoxShowRangeRadius = (CheckBox) view.findViewById(R.id.checkBox_showRangeRadius);

        switch (mMap.getMapType()) {
            case GoogleMap.MAP_TYPE_NORMAL:
                radioGroupMapType.check(R.id.radioButton_maptype_normal);
                break;
            case GoogleMap.MAP_TYPE_TERRAIN:
                radioGroupMapType.check(R.id.radioButton_maptype_terrain);
                break;
            case GoogleMap.MAP_TYPE_HYBRID:
                radioGroupMapType.check(R.id.radioButton_maptype_hybrid);
                break;
            case GoogleMap.MAP_TYPE_SATELLITE:
                radioGroupMapType.check(R.id.radioButton_maptype_satellite);
                break;
        }

        switch (rangeUnits) {
            case FEET:
                radioGroupRangeUnits.check(R.id.radioButton_units_feet);
                break;
            case METERS:
                radioGroupRangeUnits.check(R.id.radioButton_units_meters);
                break;
            case MILES:
                radioGroupRangeUnits.check(R.id.radioButton_units_miles);
                break;
            case KILOMETERS:
                radioGroupRangeUnits.check(R.id.radioButton_units_km);
                break;
        }

        editTextRangeRadius.setText(String.valueOf(rangeRadius));
        checkBoxShowRangeRadius.setChecked(showRange);

        builder.setTitle("Map Options")
                .setView(view)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        SharedPreferences sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);

                        SharedPreferences.Editor editor = sharedPreferences.edit();

                        switch (radioGroupMapType.getCheckedRadioButtonId()) {
                            case R.id.radioButton_maptype_normal:
                                setMapTypeNormal(null);
                                editor.putString(MapTypePref, "Normal");
                                break;
                            case R.id.radioButton_maptype_terrain:
                                setMapTypeTerrain(null);
                                editor.putString(MapTypePref, "Terrain");
                                break;
                            case R.id.radioButton_maptype_hybrid:
                                setMapTypeHybrid(null);
                                editor.putString(MapTypePref, "Hybrid");
                                break;
                            case R.id.radioButton_maptype_satellite:
                                setMapTypeSatellite(null);
                                editor.putString(MapTypePref, "Satellite");
                                break;
                            default:
                                setMapTypeNormal(null);
                                editor.putString(MapTypePref, "Normal");
                                break;
                        }

                        switch (radioGroupRangeUnits.getCheckedRadioButtonId()) {
                            case R.id.radioButton_units_meters:
                                rangeUnits = RangeUnits.METERS;
                                editor.putString(RangeUnitsPref, "Meters");
                                break;
                            case R.id.radioButton_units_feet:
                                rangeUnits = RangeUnits.FEET;
                                editor.putString(RangeUnitsPref, "Feet");
                                break;
                            case R.id.radioButton_units_miles:
                                rangeUnits = RangeUnits.MILES;
                                editor.putString(RangeUnitsPref, "Miles");
                                break;
                            case R.id.radioButton_units_km:
                                rangeUnits = RangeUnits.KILOMETERS;
                                editor.putString(RangeUnitsPref, "Kilometers");
                                break;
                            default:
                                rangeUnits = RangeUnits.METERS;
                                editor.putString(RangeUnitsPref, "Meters");
                                break;
                        }

                        rangeRadius = Float.parseFloat(editTextRangeRadius.getText().toString());
                        editor.putFloat(RangeRadiusPref, rangeRadius);

                        showRange = checkBoxShowRangeRadius.isChecked();
                        editor.putBoolean(ShowRangePref, checkBoxShowRangeRadius.isChecked());

                        editor.commit();

                        BigDecimal bigDecimal_rangeRadius = new BigDecimal(rangeRadius);
                        bigDecimal_rangeRadius = bigDecimal_rangeRadius.round(new MathContext(5, RoundingMode.HALF_UP)).stripTrailingZeros();

                        textViewRangeRadius.setText("Range: " + bigDecimal_rangeRadius.toPlainString() + " " + getUnitsReadable());


                        if (showRange) {
                            if (circle == null || lineBearing == null) {
                                Location location = locationManager.getLastKnownLocation(provider);
                                if (location != null)
                                    createRangeRadius(new LatLng(location.getLatitude(), location.getLongitude()));
                                else
                                    Toast.makeText(getApplicationContext(),"Searching for location, please wait...", Toast.LENGTH_SHORT).show();
                            }
                        }
                        else
                        {
                            if (circle != null) {
                                circle.remove();
                                circle = null;
                            }
                            if (lineBearing != null) {
                                lineBearing.remove();
                                lineBearing = null;
                            }
                        }

                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        alertDialog = builder.create();

        alertDialog.show();
    }

    public void showFileOptions(MenuItem item)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final View viewAlertDialog = getLayoutInflater().inflate(R.layout.alertdialog_fileoptions, null);

        final EditText editTextOutputFile = (EditText)viewAlertDialog.findViewById((R.id.editTextOutputFile));
        final TextView textViewCurrentOutputFile = (TextView)viewAlertDialog.findViewById(R.id.textViewCurrentOutputFile);

        textViewCurrentOutputFile.setText(currentFile);

        final ListView listViewFiles = (ListView)viewAlertDialog.findViewById(R.id.listViewFileList);

        List<String> filesList = new ArrayList<String>();

        /*
        filesList.add("String 1");
        filesList.add("String 2");
        filesList.add("String 3");
        filesList.add("More Strings 4");
        filesList.add("More Strings 5");
        filesList.add("More Strings 6");
        filesList.add("More Strings 7");
        filesList.add("More Strings 8");
        filesList.add("More Strings 9");
        filesList.add("String 10");
        */

        String[] filesString;
        final String packageName = this.getPackageName();
        File[] filesTemp;
        List<Date> datesModifiedTemp = new ArrayList<Date>();
        long[] fileSizesTemp;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";

            File filesDir = new File(path);

            filesString = filesDir.list();

            filesTemp = filesDir.listFiles();

        } else
        {
            filesString = getFilesDir().list();
            filesTemp = getFilesDir().listFiles();
        }

        //NULL POINTER THROWN HERE (Is 'filesString' null?)
        if (filesString != null)
            for (int i = 0; i < filesString.length; i++)
            {
                filesList.add(filesString[i]);
            }
        else
        {
            Toast.makeText(getApplicationContext(), "Could not open storage directory.  Plese try again.", Toast.LENGTH_SHORT).show();

        }

        fileSizesTemp = new long[filesTemp.length];

        for (int i = 0; i < filesTemp.length; i++)
        {
            datesModifiedTemp.add(new Date(filesTemp[i].lastModified()));
            fileSizesTemp[i] = filesTemp[i].length();
        }

        final File[] files = filesTemp;
        final List<Date> datesModified = datesModifiedTemp;
        final long[] fileSizes = fileSizesTemp;


        //ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, R.layout.listitem_files, filesList);

        //listViewFiles.setAdapter(arrayAdapter);

        class filesListAdapter extends BaseAdapter {

            List<String> fileNames;

            public filesListAdapter(List<String> fileNamesIn)
            {
                fileNames = fileNamesIn;
            }

            @Override
            public int getCount() {
                return fileNames.size();
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View itemView = getLayoutInflater().inflate(R.layout.listitem_files, null);

                TextView textViewFileName = (TextView)itemView.findViewById(R.id.textViewFileName);
                TextView textViewDateModified = (TextView)itemView.findViewById(R.id.textViewDateModified);
                TextView textViewFileSize = (TextView)itemView.findViewById(R.id.textViewFileSize);

                textViewFileName.setText(fileNames.get(position));
                textViewDateModified.setText("Modified: " + String.valueOf(datesModified.get(position)));
                textViewFileSize.setText("Size: " + String.valueOf(fileSizes[position]) + " bytes");

                return itemView;
            }
        }

        listViewFiles.setAdapter(new filesListAdapter(filesList));

        listViewFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TextView textViewFileName = (TextView)view.findViewById(R.id.textViewFileName);

                editTextOutputFile.setText(textViewFileName.getText().toString());

            }
        });


        builder.setTitle("Select Output File")
                .setView(viewAlertDialog)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (!editTextOutputFile.getText().toString().equals(currentFile)) {


                            if (locationManager != null) {
                                saveMarkers(null);

                                markerList.clear();
                                mMap.clear();
                                lineMeasurePoints.clear();

                                circle = null;
                                lineBearing = null;
                                //lineMeasurePoints = null;
                            }

                            currentFile = editTextOutputFile.getText().toString();

                            Log.i("showFileOptions.setPositiveButton", "Setting File: " + currentFile);

                            SharedPreferences sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);

                            SharedPreferences.Editor editor = sharedPreferences.edit();

                            editor.putString(CurrentFilePref, currentFile);

                            editor.commit();

                            if (locationManager != null) {
                                loadMarkers(currentFile);

                                saveMarkers(null);
                            }


                            Toast.makeText(getApplicationContext(), "File selected: " + currentFile, Toast.LENGTH_SHORT).show();

                        } else {
                            Toast.makeText(getApplicationContext(), "Current file already selected!", Toast.LENGTH_SHORT).show();
                            ;
                        }


                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!editTextOutputFile.getText().toString().equals(currentFile)) {

                            File file;

                            String fileNameToDelete = editTextOutputFile.getText().toString();

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + packageName + "/files/";

                                file = new File(path + fileNameToDelete);


                            } else {
                                file = new File(getFilesDir() + "/" + fileNameToDelete);
                            }

                            if (file.exists()) {
                                file.delete();
                                Toast.makeText(getApplicationContext(), "File deleted: " + fileNameToDelete, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "File does not exist!", Toast.LENGTH_SHORT).show();
                            }


                        } else {
                            Toast.makeText(getApplicationContext(), "Cannot delete currently selected file!", Toast.LENGTH_SHORT).show();
                            ;
                        }


                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        alertDialog = builder.create();

        alertDialog.show();

    }

    public void clearMarkers(MenuItem item)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Delete All Points?")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        markerList.clear();
                        mMap.clear();
                        lineMeasurePoints.clear();

                        circle = null;
                        lineBearing = null;
                        //lineMeasurePoints = null;
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        alertDialog = builder.create();

        alertDialog.show();

        System.gc();

    }

    public void showInstructions(MenuItem item)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_instructions, null);

        //Views in Layout file go here (e.g. checkbox, radiobutton etc)

        builder.setTitle("Instructions")
                .setView(view)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {


                    }
                });

        alertDialog = builder.create();

        alertDialog.show();

    }

    public void showAbout(MenuItem item)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final View view = getLayoutInflater().inflate(R.layout.alertdialog_about, null);

        //Views in Layout file go here (e.g. checkbox, radiobutton etc)

        String version = "Outdoorsman's Waypoint Plotter v" + BuildConfig.VERSION_NAME;

        if (BuildConfig.DEBUG)
            version += " Debug";
        else
            version += "Release";

        Log.i("showAbout", version);

        TextView textView = ((TextView)view.findViewById(R.id.textViewVersion));

        textView.setText(version);

        builder.setTitle("About")
                .setView(view)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {


                    }
                });

        alertDialog = builder.create();

        alertDialog.show();
    }

    public void showRateApp(MenuItem item)
    {
        /*
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + APP_NAME));

        PackageManager packageManager = getPackageManager();

        if (packageManager.queryIntentActivities(intent, 0) != null)
            this.startActivity(intent);
            */

        final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        } catch (Exception e) {
            e.printStackTrace();

            Toast.makeText(getApplicationContext(), "Problem opening app in market.  Is the app released yet?", Toast.LENGTH_SHORT).show();
        }

    }



    public void writeToExternalStoragePublic(String filename, String content, boolean append) {
        String packageName = this.getPackageName();
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";

        //Log.i("writeToExternalStoragePublic","Writing to: " + path);

        if (true) {
            try {
                boolean exists = (new File(path)).exists();
                if (!exists) {
                    new File(path).mkdirs();
                }
                // Open output stream
                FileOutputStream fOut = new FileOutputStream(path + filename, append);
                // write integers as separated ascii's
                //fOut.write((Integer.valueOf(content).toString() + " ").getBytes());
                //fOut.write((Integer.valueOf(content).toString() + " ").getBytes());
                fOut.write(content.getBytes());
                // Close output stream
                fOut.flush();
                fOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeToExternalStorage_Pre_API_19(String fileName, String content) {
        try {

            Log.i("write_Pre_API_19", "Attempting to write...");
            FileOutputStream outputStream;

            outputStream = openFileOutput(fileName, Context.MODE_PRIVATE);
            outputStream.write(content.getBytes());
            outputStream.close();
            Log.i("write_Pre_API_19", "Write complete!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public void saveMarkers(MenuItem item)
    {
        String outputFileName = currentFile;
        //String outputFileName = "myfile.txt";

        String packageName = this.getPackageName();
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";
        String path2 = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/Documents";

        File outputFile = new File(path + outputFileName);

        StringBuilder stringBuilder = new StringBuilder();


        /*
        stringBuilder.append("44.1");
        stringBuilder.append(",");
        stringBuilder.append("-93.1");
        stringBuilder.append(",");
        stringBuilder.append( "Marker 1");
        stringBuilder.append(System.getProperty("line.separator"));
        stringBuilder.append("44.2");
        stringBuilder.append(",");
        stringBuilder.append("-93.2");
        stringBuilder.append(",");
        stringBuilder.append( "Marker 2");
        stringBuilder.append(System.getProperty("line.separator"));
        stringBuilder.append("44.3");
        stringBuilder.append(",");
        stringBuilder.append("-93.3");
        stringBuilder.append(",");
        stringBuilder.append( "Marker 3");
        stringBuilder.append(System.getProperty("line.separator"));
        */


        for (Marker m : markerList)
        {
            stringBuilder.append(m.getPosition().latitude);
            stringBuilder.append(",");
            stringBuilder.append(m.getPosition().longitude);
            stringBuilder.append(",");
            stringBuilder.append( m.getTitle());
            stringBuilder.append(System.getProperty("line.separator"));

        }

        String outputString = stringBuilder.toString();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            writeToExternalStoragePublic(outputFileName, outputString, false);
        else
            writeToExternalStorage_Pre_API_19(outputFileName, outputString);


        Log.v("saveMarkers","Markers Saved: " + String.valueOf(markerList.size()));

        if (item != null)
            Toast.makeText(getApplicationContext(),"Waypoints Saved!", Toast.LENGTH_SHORT).show();




        // snippet taken from question
        //File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        //File scannerFile = new File(path, "SubDirName");
        //path.mkdirs();

        /*
        // initiate media scan and put the new things into the path array to
        // make the scanner aware of the location and the files you want to see
        MediaScannerConnection.scanFile(this, new String[]{path, path + outputFileName, path2}, null, null);

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(outputFile);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
        */
    }

    public void loadMarkers(String inputFileName)
    {

        //String inputFileName = "prickly_ash_locations.txt";

        String path;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String packageName = this.getPackageName();
            path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + packageName + "/files/";
        }
        else
        {
            path = getFilesDir() + "/";
        }

        File inputFile = new File(path + inputFileName);

        FileInputStream is;
        BufferedReader reader;

        try {
            if (inputFile.exists() && inputFile.length() > 0) {
                is = new FileInputStream(inputFile);
                reader = new BufferedReader(new InputStreamReader(is));
                String line = reader.readLine();
                while (line != null && line.contains(",")) {

                    Log.i("MapsActivity.loadMarkers", line);

                    String tempLine = line;

                    String latString = tempLine.split(",",2)[0];
                    tempLine = tempLine.split(",",2)[1];

                    Log.i("MapsActivity.loadMarkers", "LatString: " + latString);
                    Log.i("MapsActivity.loadMarkers", "TempLine: " + tempLine);

                    String longString = tempLine.split(",",2)[0];
                    tempLine = tempLine.split(",",2)[1];

                    Log.i("MapsActivity.loadMarkers", "LongString: " + latString);
                    Log.i("MapsActivity.loadMarkers", "TempLine: " + tempLine);


                    double latitude = Double.parseDouble(latString);
                    double longitude = Double.parseDouble(longString);
                    LatLng latLng = new LatLng(latitude, longitude);
                    String title = tempLine;



                    Marker myMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(title));

                    markerList.add(myMarker);


                    Log.i("MapsActivity.loadMarkers", line);
                    line = reader.readLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Error loading file! Is the file properly formatted?", Toast.LENGTH_SHORT).show();
        }
    }


    private LatLng getLatLng()
    {
        double latitude = locationManager.getLastKnownLocation(provider).getLatitude();
        double longitude = locationManager.getLastKnownLocation(provider).getLongitude();

        return new LatLng(latitude, longitude);
    }

    public float getRangeInMeters()
    {
        switch (rangeUnits) {
            case METERS:
                return rangeRadius;
            case FEET:
                return rangeRadius * METERS_PER_FOOT;
            case MILES:
                return rangeRadius * METERS_PER_MILE;
            case KILOMETERS:
                return rangeRadius * METERS_PER_KILOMETER;
            default:
                return rangeRadius;
        }


    }


    private float calculateZoomLevel(int screenWidth, float viewSizeMeters) {

        double scale = EQUATOR_LENGTH_METERS / viewSizeMeters;

        float zoomLevel = 0;

        while (Math.pow(2,zoomLevel) < scale)
        {
            zoomLevel += .05;
        }

        /*
        double equatorLength = 40075004; // in meters
        double widthInPixels = screenWidth;
        double metersPerPixel = equatorLength / 256;
        int zoomLevel = 1;
        while ((metersPerPixel * widthInPixels) > 2000) {
            metersPerPixel /= 2;
            ++zoomLevel;
        }
        Log.i("ADNAN", "zoom level = " + zoomLevel);
        */
        return zoomLevel;
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile (String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }



    @Override
    public void onSensorChanged(SensorEvent event) {
        //bearing = event.values[0];

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;

        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {        float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimut = orientation[0]; // orientation contains: azimut, pitch and roll
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
