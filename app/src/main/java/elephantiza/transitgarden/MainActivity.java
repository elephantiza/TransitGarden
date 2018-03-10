package elephantiza.transitgarden;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBufferResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.ActivityRecognitionClient;


public class MainActivity extends AppCompatActivity {

    private ActivityRecognitionClient arclient;
    private PendingIntent pIntent;
    private BroadcastReceiver receiver;
    private TextView tvActivity;
    private FusedLocationProviderClient mFusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvActivity = findViewById(R.id.tvActivity);

        int resp = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (resp == ConnectionResult.SUCCESS) {
            arclient = ActivityRecognition.getClient(getApplicationContext());
            Toast.makeText(this, "Connection Succesful", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ActivityRecognitionService.class);
            pIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            arclient.requestActivityUpdates(1000, pIntent);

        } else {
            Toast.makeText(this, "Please install Google Play Service.", Toast.LENGTH_SHORT).show();
        }

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String v = "Activity :" + intent.getStringExtra("Activity") + " " + "Confidence : " + intent.getExtras().getInt("Confidence") + "\n";
                v += tvActivity.getText();
                tvActivity.setText(v);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("elephantiza.transitgarden.ACTIVITY_RECOGNITION_DATA");
        registerReceiver(receiver, filter);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions( this, new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    LocationService.MY_PERMISSION_ACCESS_COURSE_LOCATION );
        } else {
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            // Logic to handle location object
                        }
                    }
                });
        }

        PlaceDetectionClient mPlaceDetectionClient = PlaceDetectionClient();
        Task<PlaceLikelihoodBufferResponse> placeResult = mPlaceDetectionClient.getCurrentPlace(null);
        placeResult.addOnCompleteListener(new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
            @Override
            public void onComplete(@NonNull Task<PlaceLikelihoodBufferResponse> task) {
                PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();
                for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                    Log.i("PLACES", String.format("Place '%s' has likelihood: %g",
                            placeLikelihood.getPlace().getName(),
                            placeLikelihood.getLikelihood()));
                }
                likelyPlaces.release();
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (arclient != null) {
            arclient.removeActivityUpdates(pIntent);
        }
        unregisterReceiver(receiver);
    }
}