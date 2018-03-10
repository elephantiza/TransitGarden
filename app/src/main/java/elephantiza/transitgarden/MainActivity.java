package elephantiza.transitgarden;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.ActivityRecognitionClient;


public class MainActivity extends AppCompatActivity {

    private ActivityRecognitionClient arclient;
    private PendingIntent pIntent;
    private BroadcastReceiver receiver;
    private TextView tvActivity;

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