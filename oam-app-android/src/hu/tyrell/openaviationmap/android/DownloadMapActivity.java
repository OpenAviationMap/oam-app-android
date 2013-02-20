package hu.tyrell.openaviationmap.android;

import java.io.File;
import java.util.Random;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.codeslap.groundy.DetachableResultReceiver;
import com.codeslap.groundy.Groundy;
import com.codeslap.groundy.GroundyManger;
import com.codeslap.groundy.util.Bundler;

public class DownloadMapActivity extends Activity {

    private static String TAG = "DownloadMapActivity";

    private static int DOWNLOAD_NOTIFICATIONS = R.layout.activity_download;

    private static String PROGRESS_KEY = "progress";
    private static String RECEIVER_KEY = "receiver";
    private static String DOWNLOADING_KEY = "downloading";
    private static int GROUP = 1;
    private static String TOKEN = "download_token";

    private NotificationManager mNM;
    private NotificationCompat.Builder mBuilder;
    private DetachableResultReceiver mDetachableReceiver;
    private boolean downloading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        Log.i(TAG, "onCreate " + System.identityHashCode(this));

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);

        downloading = false;

        if (savedInstanceState != null) {
            updateMainProgress(savedInstanceState.getInt(PROGRESS_KEY));
            mDetachableReceiver = savedInstanceState.getParcelable(RECEIVER_KEY);
            downloading = savedInstanceState.getBoolean(DOWNLOADING_KEY);
        } else {
            mDetachableReceiver = new DetachableResultReceiver(new Handler());
        }
        mDetachableReceiver.setReceiver(mReceiver);

        Button downloadButton = (Button) findViewById(R.id.downloadButton);

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadButtonPressed();
            }
        });

        updateLabels();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.i(TAG, "onSaveInstanceState");

        ProgressBar bp = getProgressBar();
        outState.putInt(PROGRESS_KEY, bp.getProgress());
        outState.putParcelable(RECEIVER_KEY, mDetachableReceiver);
        outState.putBoolean(DOWNLOADING_KEY, true);
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        mDetachableReceiver.clearReceiver();
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed");

        moveTaskToBack(true);
    }

    private void downloadButtonPressed() {
        Log.i(TAG, "downloadButtonPressed " + downloading);

        if (downloading) {
            // cancel download
            GroundyManger.cancelTasks(this, GROUP);

            Button downloadButton = (Button) findViewById(R.id.downloadButton);
            downloadButton.setActivated(false);

            // labels will be set up when the process is canceled
        } else {
            startDownload();
            updateLabels();
        }

    }

    private void updateLabels() {
        Button downloadButton = (Button) findViewById(R.id.downloadButton);
        TextView note = (TextView) findViewById(R.id.textNote);

        if (downloading) {
            downloadButton.setActivated(true);
            downloadButton.setText(R.string.download_cancel);
            note.setText(R.string.download_inprogress);
        } else {
            if (MainActivity.mapsAvailableLocally()) {
                downloadButton.setActivated(true);
                downloadButton.setText(R.string.download_force_start);
                note.setText(R.string.download_force_notification);
            } else {
                downloadButton.setActivated(true);
                downloadButton.setText(R.string.download_start);
                note.setText(R.string.download_notification);
            }
        }

    }

    private void startDownload() {
        if (!downloading) {
            // remove the files
            File oamPath = MainActivity.getDataPath();
            File osm_gemf = new File(oamPath, MainActivity.OSM_MAP_FILE);
            File oam_gemf = new File(oamPath, MainActivity.OAM_MAP_FILE);

            osm_gemf.delete();
            oam_gemf.delete();

            // start the download task
            int time = new Random().nextInt(10000);
            Bundle params = new Bundler().add(DownloadMapTask.KEY_ESTIMATED, time).build();

            Groundy groundy = Groundy.create(this, DownloadMapTask.class);
            groundy.receiver(mDetachableReceiver);
            groundy.params(params);
            groundy.token(TOKEN);
            groundy.group(GROUP);
            groundy.queue();

            downloading = true;
        }
    }

    private final DetachableResultReceiver.Receiver mReceiver = new DetachableResultReceiver.Receiver() {
        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {
            case Groundy.STATUS_RUNNING: {
                ProgressBar pb = getProgressBar();

                if (pb != null) {
                    pb.setIndeterminate(true);
                }
            } break;

            case Groundy.STATUS_PROGRESS: {
                double progress = ((double) resultData.getInt(DownloadMapTask.KEY_COUNT))
                                / ((double) resultData.getInt(DownloadMapTask.KEY_ESTIMATED));

                showNotification(R.drawable.icon, R.string.download_status, progress);
                updateMainProgress(progress);
            } break;

            case Groundy.STATUS_FINISHED:
                Log.i(TAG, "STATUS_FINISHED");

                downloading = false;
                mNM.cancel(DOWNLOAD_NOTIFICATIONS);
                updateLabels();
                ProgressBar pb = getProgressBar();
                pb.setIndeterminate(false);
                pb.setProgress(0);

                Log.i(TAG, resultData.toString());

                break;

            default:
            }
        }
    };

    private void showNotification(int iconId, int textId, double d) {

        mBuilder.setContentTitle(getText(R.string.app_name));
        mBuilder.setSmallIcon(iconId);
        mBuilder.setContentText(getText(textId));
        mBuilder.setProgress(100, (int) (100d * d), false);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                              new Intent(this, DownloadMapActivity.class), 0);
        mBuilder.setContentIntent(contentIntent);

        mNM.notify(DOWNLOAD_NOTIFICATIONS, mBuilder.build());
    }

    private void updateMainProgress(double progress) {
        ProgressBar pb = getProgressBar();

        if (pb != null) {
            pb.setIndeterminate(false);
            pb.setProgress((int) (progress * 100));
        }
    }

    private ProgressBar getProgressBar() {
        return (ProgressBar) findViewById(R.id.downloadProgressBar);
    }

}
