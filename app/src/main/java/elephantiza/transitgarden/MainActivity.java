package elephantiza.transitgarden;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBufferResponse;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.ConnectionResult;

import org.w3c.dom.Text;


public class MainActivity extends AppCompatActivity implements FetchAddressTask.OnTaskCompleted{

    public int score;
    public long lastStillTime;
    public boolean stillInVehicle = false;
    private ActivityRecognitionClient arclient;
    private PendingIntent pIntent;
    private BroadcastReceiver receiver;
    private TextView tvActivity;
    private PlaceDetectionClient pdClient;
    private String mLastPlaceName;
    public TextView scoreView;
    public TextView stillInVehicleView;

    // Constants
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final String TRACKING_LOCATION_KEY = "tracking_location";

    // Views
    private Button mLocationButton;
    private TextView mLocationTextView;
    private ImageView mAndroidImageView;

    // Location classes
    private boolean mTrackingLocation;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lastStillTime = 0;
        stillInVehicleView = (TextView) findViewById(R.id.stillInVehicle);
        scoreView = (TextView) findViewById(R.id.score);
        scoreView.setText("0 Points");
        stillInVehicleView.setText("NOT In Vehicle");

        mLocationButton = (Button) findViewById(R.id.button_location);
        mLocationTextView = (TextView) findViewById(R.id.textview_location);

        tvActivity = findViewById(R.id.tvActivity);

        // Initialize the FusedLocationClient.
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Restore the state if the activity is recreated.
        if (savedInstanceState != null) {
            mTrackingLocation = savedInstanceState.getBoolean(
                    TRACKING_LOCATION_KEY);
        }

        // Set the listener for the location button.
        mLocationButton.setOnClickListener(new View.OnClickListener() {
            /**
             * Toggle the tracking state.
             * @param v The track location button.
             */
            @Override
            public void onClick(View v) {
                if (!mTrackingLocation) {
                    startTrackingLocation();
                } else {
                    stopTrackingLocation();
                }
            }
        });

        // Initialize the location callbacks.
        mLocationCallback = new LocationCallback() {
            /**
             * This is the callback that is triggered when the
             * FusedLocationClient updates your location.
             * @param locationResult The result containing the device location.
             */
            @Override
            public void onLocationResult(LocationResult locationResult) {
                // If tracking is turned on, reverse geocode into an address
                if (mTrackingLocation) {
                    new FetchAddressTask(MainActivity.this, MainActivity.this)
                            .execute(locationResult.getLastLocation());
                }
            }
        };

        int resp = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (resp == ConnectionResult.SUCCESS) {
            arclient = ActivityRecognition.getClient(getApplicationContext());
            Toast.makeText(this, "Connection Succesful", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ActivityRecognitionService.class);
            pIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            arclient.requestActivityUpdates(1, pIntent);

            pdClient = Places.getPlaceDetectionClient(this, null);

        } else {
            Toast.makeText(this, "Please install Google Play Service.", Toast.LENGTH_SHORT).show();
        }

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String v = "Activity :" + intent.getStringExtra("Activity") + " " + "Confidence : " + intent.getExtras().getInt("Confidence") + "\n";
                v += tvActivity.getText();
                tvActivity.setText(v);

                if (intent.getStringExtra("Activity").equals("On Foot")) {
                    stillInVehicle = true;
                    stillInVehicleView.setText("In Vehicle");
                    //startTrackingLocation();
                } else if(stillInVehicle && !intent.getStringExtra("Activity").equals("On Foot")){
                    try {
                        Task<PlaceLikelihoodBufferResponse> placeResult = pdClient.getCurrentPlace(null);

                        placeResult.addOnCompleteListener(new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<PlaceLikelihoodBufferResponse> task) {
                                if (task.isSuccessful()) {
                                    PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();
                                    float maxLikelihood = 0;
                                    Place currentPlace = null;

                                    for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                                        if (maxLikelihood < placeLikelihood.getLikelihood()) {
                                            maxLikelihood = placeLikelihood.getLikelihood();
                                            currentPlace = placeLikelihood.getPlace();
                                        }
                                    }

                                    if (currentPlace != null) {
                                        mLocationTextView.setText(
                                                getString(R.string.address_text,
                                                        currentPlace.getName(),
                                                        System.currentTimeMillis()));
                                    }

                                    for (Integer placeType : currentPlace.getPlaceTypes()) {
                                        Toast.makeText(getApplicationContext(), "ACTUAL TYPE: " + placeType, Toast.LENGTH_SHORT).show();
                                        switch (placeType) {
                                            case Place.TYPE_BUS_STATION:
                                                score = score + 1000;
                                                lastStillTime = System.currentTimeMillis();
                                                Toast.makeText(getApplicationContext(), "Bus Station", Toast.LENGTH_SHORT).show();
                                                break;
                                            case Place.TYPE_SUBWAY_STATION:
                                                score = score + 1000;
                                                lastStillTime = System.currentTimeMillis();
                                                Toast.makeText(getApplicationContext(), "Subway Station", Toast.LENGTH_SHORT).show();
                                                break;
                                            case Place.TYPE_TRAIN_STATION:
                                                score = score + 1000;
                                                lastStillTime = System.currentTimeMillis();
                                                Toast.makeText(getApplicationContext(), "Train Station", Toast.LENGTH_SHORT).show();
                                                break;
                                            case Place.TYPE_POINT_OF_INTEREST:
                                                score = score + 1000;
                                                lastStillTime = System.currentTimeMillis();
                                                scoreView.setText(Integer.toString(score));
                                                Toast.makeText(getApplicationContext(), "Point of Interest", Toast.LENGTH_SHORT).show();
                                                break;
                                            case 34:
                                                score = score + 1000;
                                                lastStillTime = System.currentTimeMillis();
                                                scoreView.setText(Integer.toString(score));
                                                Toast.makeText(getApplicationContext(), "Point of Interest", Toast.LENGTH_SHORT).show();
                                                break;
                                        }
                                        scoreView.setText(Integer.toString(score));
                                    }

                                    likelyPlaces.release();

                                } else {
                                    mLocationTextView.setText(
                                            getString(R.string.address_text,
                                                    "No Place name found!",
                                                    System.currentTimeMillis()));
                                }
                            }
                        });
                    } catch(SecurityException e) {
                        Toast.makeText(getApplicationContext(), "SecurityException", Toast.LENGTH_SHORT).show();
                    }

                }
                if (System.currentTimeMillis() - lastStillTime > 5*60*1000 && !intent.getStringExtra("Activity").equals("On Foot"))
                {
                    stillInVehicle = false;
                    stillInVehicleView.setText("NOT In Vehicle");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("elephantiza.transitgarden.ACTIVITY_RECOGNITION_DATA");
        registerReceiver(receiver, filter);


    }


    /**
     * Sets up the location request.
     *
     * @return The LocationRequest object containing the desired parameters.
     */
    private LocationRequest getLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }


    /**
     * Saves the last location on configuration change
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(TRACKING_LOCATION_KEY, mTrackingLocation);
        super.onSaveInstanceState(outState);
    }


    /**
     * Callback that is invoked when the user responds to the permissions
     * dialog.
     *
     * @param requestCode  Request code representing the permission request
     *                     issued by the app.
     * @param permissions  An array that contains the permissions that were
     *                     requested.
     * @param grantResults An array with the results of the request for each
     *                     permission requested.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:

                // If the permission is granted, get the location, otherwise,
                // show a Toast
                if (grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
                    startTrackingLocation();
                } else {
                    Toast.makeText(this,
                            R.string.location_permission_denied,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


    /**
     * Starts tracking the device. Checks for
     * permissions, and requests them if they aren't present. If they are,
     * requests periodic location updates, sets a loading text and starts the
     * animation.
     */
    private void startTrackingLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]
                            {Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            mTrackingLocation = true;

            mFusedLocationClient.requestLocationUpdates
                    (getLocationRequest(),
                            mLocationCallback,
                            null /* Looper */);

            // Set a loading text while you wait for the address to be
            // returned
            mLocationTextView.setText(getString(R.string.address_text,
                    getString(R.string.loading),
                    System.currentTimeMillis()));
            mLocationButton.setText(R.string.stop_tracking_location);
        }
    }


    /**
     * Stops tracking the device. Removes the location
     * updates, stops the animation, and resets the UI.
     */
    private void stopTrackingLocation() {
            if (mTrackingLocation) {
            mTrackingLocation = false;
            mLocationButton.setText(R.string.start_tracking_location);
            mLocationTextView.setText(R.string.textview_hint);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (arclient != null) {
            arclient.removeActivityUpdates(pIntent);
        }
        unregisterReceiver(receiver);
    }

    @Override
    public void onTaskCompleted(String result) {
        if (mTrackingLocation) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                        LocationService.MY_PERMISSION_ACCESS_COURSE_LOCATION);
            } else {
                Task<PlaceLikelihoodBufferResponse> placeResult = pdClient.getCurrentPlace(null);

                placeResult.addOnCompleteListener(new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<PlaceLikelihoodBufferResponse> task) {
                        if (task.isSuccessful()) {
                            PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();
                            float maxLikelihood = 0;
                            Place currentPlace = null;

                            for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                                if (maxLikelihood < placeLikelihood.getLikelihood()) {
                                    maxLikelihood = placeLikelihood.getLikelihood();
                                    currentPlace = placeLikelihood.getPlace();
                                }
                            }

                            if (currentPlace != null) {
                                mLocationTextView.setText(
                                        getString(R.string.address_text,
                                                currentPlace.getName(),
                                                System.currentTimeMillis()));
                            }

                            for (Integer placeType : currentPlace.getPlaceTypes()) {
                                Toast.makeText(getApplicationContext(), "ACTUAL TYPE: " + placeType, Toast.LENGTH_SHORT).show();

                                switch (placeType) {
                                    case Place.TYPE_BUS_STATION:
                                        Toast.makeText(getApplicationContext(), "Bus Station", Toast.LENGTH_SHORT).show();
                                        break;
                                    case Place.TYPE_SUBWAY_STATION:
                                        Toast.makeText(getApplicationContext(), "Subway Station", Toast.LENGTH_SHORT).show();
                                        break;
                                    case Place.TYPE_TRAIN_STATION:
                                        Toast.makeText(getApplicationContext(), "Train Station", Toast.LENGTH_SHORT).show();
                                        break;
                                    case Place.TYPE_UNIVERSITY:
                                        Toast.makeText(getApplicationContext(), "University", Toast.LENGTH_SHORT).show();
                                        break;
                                }
                            }

                            likelyPlaces.release();

                        } else {
                            mLocationTextView.setText(
                                    getString(R.string.address_text,
                                            "No Place name found!",
                                            System.currentTimeMillis()));
                        }
                    }
                });
            }
        }

    }

    @Override
    protected void onPause() {
        if (mTrackingLocation) {
            stopTrackingLocation();
            mTrackingLocation = true;
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mTrackingLocation) {
            startTrackingLocation();
        }
        super.onResume();
    }
}