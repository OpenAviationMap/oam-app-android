/*
    Open Aviation Map
    Copyright (C) 2012-2013 Ákos Maróy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openaviationmap.android;

import java.io.File;
import java.io.FilenameFilter;

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.text.format.Formatter;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.codeslap.groundy.DetachableResultReceiver;
import com.codeslap.groundy.Groundy;
import com.codeslap.groundy.GroundyManger;

public class DownloadMapActivity extends Activity {

    private enum State implements Parcelable {
        INCOMPLETE(0),
        COMPLETE(1),
        DOWNLOADING(2),
        CANCELLED(3);

        private final int value;

        private State(int v) {
            value = v;
        }

        public static final Parcelable.Creator<State> CREATOR
                                    = new Parcelable.Creator<State>() {
            @Override
            public State createFromParcel(Parcel in) {
                switch (in.readInt()) {
                default:
                case 0:
                    return INCOMPLETE;
                case 1:
                    return COMPLETE;
                case 2:
                    return DOWNLOADING;
                case 3:
                    return CANCELLED;
                }
            }

            @Override
            public State[] newArray(int size) {
                return new State[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(value);
        }
    };

    private static String TAG = "DownloadMapActivity";

    // use the activity ID as its a unique number
    private static int DOWNLOAD_NOTIFICATIONS = R.layout.activity_download;
    private static int GROUP                  = R.layout.activity_download;

    private static String PROGRESS_KEY = "progress";
    private static String RECEIVER_KEY = "receiver";
    private static String STATE_KEY = "state";
    private static String DOWNLOAD_START_KEY = "download_start";

    private NotificationManager mNM;
    private NotificationCompat.Builder mBuilder;
    private DetachableResultReceiver mDetachableReceiver;
    private State state;
    private long downloadStart;
    private boolean visible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.action_get_maps);

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);

        state = mapsAvailableLocally() ? State.COMPLETE
                                       : State.INCOMPLETE;

        if (savedInstanceState != null) {
            updateMainProgress(0, 0, savedInstanceState.getInt(PROGRESS_KEY));
            mDetachableReceiver =
                                savedInstanceState.getParcelable(RECEIVER_KEY);
            state = savedInstanceState.getParcelable(STATE_KEY);
            downloadStart = savedInstanceState.getLong(DOWNLOAD_START_KEY);
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

        ProgressBar bp = getProgressBar();
        outState.putInt(PROGRESS_KEY, bp.getProgress());
        outState.putParcelable(RECEIVER_KEY, mDetachableReceiver);
        outState.putParcelable(STATE_KEY, state);
        outState.putLong(DOWNLOAD_START_KEY, downloadStart);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // app icon in action bar clicked; go home
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        visible = true;

        if (state != State.DOWNLOADING) {
            mNM.cancel(DOWNLOAD_NOTIFICATIONS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        visible = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDetachableReceiver.clearReceiver();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void downloadButtonPressed() {
        if (state == State.DOWNLOADING) {
            // cancel download
            GroundyManger.cancelTasks(this, GROUP);

            Button downloadButton = (Button) findViewById(R.id.downloadButton);
            downloadButton.setActivated(false);

            if (visible) {
                mNM.cancel(DOWNLOAD_NOTIFICATIONS);
            }

            // labels will be set up when the process is canceled
        } else {
            startDownload();
            updateLabels();
        }
    }

    private void updateLabels() {
        Button downloadButton = (Button) findViewById(R.id.downloadButton);
        TextView note = (TextView) findViewById(R.id.textNote);

        switch (state) {
        case DOWNLOADING:
            downloadButton.setActivated(true);
            downloadButton.setText(R.string.download_cancel);
            note.setText(R.string.download_starting);
            break;

        case COMPLETE:
            downloadButton.setActivated(true);
            downloadButton.setText(R.string.download_force_start);
            note.setText(R.string.download_force_notification);
            break;

        default:
            downloadButton.setActivated(true);
            downloadButton.setText(R.string.download_start);
            note.setText(R.string.download_notification);
        }
    }

    private void startDownload() {
        if (state != State.DOWNLOADING) {
            // remove the files
            File oamPath = getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR);
            File[] files = oamPath.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String fileName) {
                    return fileName.startsWith(HomeActivity.OSM_MAP_FILE)
                        || fileName.startsWith(HomeActivity.OAM_MAP_FILE);
                }
            });
            for (File f : files) {
                f.delete();
            }

            state = State.DOWNLOADING;
            downloadStart = System.currentTimeMillis();

            // start the download task
            Groundy groundy = Groundy.create(this, DownloadMapTask.class);
            groundy.receiver(mDetachableReceiver);
            groundy.group(GROUP);
            groundy.queue();
        }
    }

    private final DetachableResultReceiver.Receiver mReceiver =
                                      new DetachableResultReceiver.Receiver() {
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
                long count  = resultData.getLong(DownloadMapTask.KEY_COUNT);
                long total  = resultData.getLong(DownloadMapTask.KEY_ESTIMATED);
                int progress = resultData.getInt(Groundy.KEY_PROGRESS);

                showNotification(false, count, total, progress);
                updateMainProgress(count, total, progress);
            } break;

            case Groundy.STATUS_FINISHED: {
                state = resultData.containsKey(DownloadMapTask.KEY_CANCELLED)
                      ? State.CANCELLED
                      : State.COMPLETE;

                if (visible) {
                    mNM.cancel(DOWNLOAD_NOTIFICATIONS);
                } else {
                    long count  = resultData.getLong(DownloadMapTask.KEY_COUNT);
                    long total  = resultData.getLong(
                                                DownloadMapTask.KEY_ESTIMATED);
                    int progress = resultData.getInt(Groundy.KEY_PROGRESS);
                    showNotification(true, count, total, progress);
                }
                updateLabels();
                ProgressBar pb = getProgressBar();
                pb.setIndeterminate(false);
                pb.setProgress(0);

                if (state == State.COMPLETE && mapsAvailableLocally()) {
                    startActivity(new Intent(DownloadMapActivity.this,
                                             HomeActivity.class));
                    finish();
                }
            } break;

            default:
            case Groundy.STATUS_ERROR: {
                Toast t = Toast.makeText(DownloadMapActivity.this,
                                         R.string.download_error,
                                         Toast.LENGTH_LONG);
                t.show();
                mNM.cancel(DOWNLOAD_NOTIFICATIONS);
                state = State.INCOMPLETE;
                updateLabels();
                ProgressBar pb = getProgressBar();
                pb.setIndeterminate(false);
                pb.setProgress(0);
            }
            }
        }
    };

    private void
    showNotification(boolean complete,
                     long    count,
                     long    total,
                     int     progress) {

        if (complete) {
            mBuilder.setSmallIcon(R.drawable.navigation_accept);
            mBuilder.setContentTitle(getText(R.string.download_complete));
            mBuilder.setProgress(100, 100, false);
            mBuilder.setOngoing(false);
            mBuilder.setContentText("");
            mBuilder.setContentInfo("");

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, HomeActivity.class), 0);
            mBuilder.setContentIntent(contentIntent);
        } else {
            mBuilder.setContentTitle(
                                  getText(R.string.download_inprogress_label));
            mBuilder.setSmallIcon(R.drawable.av_download);
            mBuilder.setProgress(100, progress, false);
            mBuilder.setOngoing(true);

            Resources res = getResources();
            long now = System.currentTimeMillis();
            long speed = 1000 * count / (now - downloadStart);
            long estSecsLeft  = 0;
            long estMinsLeft  = 0;
            long estHoursLeft = 0;
            if (count > 0) {
                double c = count;
                estSecsLeft = (long)
                                ((now - downloadStart) / (c / total) / 1000d);
                estHoursLeft = estSecsLeft / 3600;
                estMinsLeft  = (estSecsLeft / 60) % 60;
                estSecsLeft %= 60;
            }

            String text = String.format(
                    res.getString(R.string.download_inprogress_notification),
                                  Formatter.formatFileSize(this, count),
                                  Formatter.formatFileSize(this, total),
                                  Formatter.formatFileSize(this, speed));
            mBuilder.setContentText(text);

            text = String.format(
                    res.getString(R.string.download_inprogress_percent),
                    progress, estHoursLeft, estMinsLeft, estSecsLeft);
            mBuilder.setContentInfo(text);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, DownloadMapActivity.class), 0);
            mBuilder.setContentIntent(contentIntent);
        }


        mNM.notify(DOWNLOAD_NOTIFICATIONS, mBuilder.build());
    }

    private void updateMainProgress(long count, long total, int progress) {
        ProgressBar pb = getProgressBar();
        TextView note = (TextView) findViewById(R.id.textNote);

        if (pb != null) {
            pb.setIndeterminate(false);
            pb.setProgress(progress);
        }

        Resources res = getResources();
        long now = System.currentTimeMillis();
        long speed = 1000 * count / (now - downloadStart);
        long estSecsLeft  = 0;
        long estMinsLeft  = 0;
        long estHoursLeft = 0;
        if (count > 0) {
            double c = count;
            estSecsLeft = (long) ((now - downloadStart) / (c / total) / 1000d);
            estHoursLeft = estSecsLeft / 3600;
            estMinsLeft  = (estSecsLeft / 60) % 60;
            estSecsLeft %= 60;
        }

        String text = String.format(res.getString(R.string.download_inprogress),
                                    progress,
                                    Formatter.formatFileSize(this, count),
                                    Formatter.formatFileSize(this, total),
                                    Formatter.formatFileSize(this, speed),
                                    estHoursLeft, estMinsLeft, estSecsLeft);
        note.setText(text);
    }

    private ProgressBar getProgressBar() {
        return (ProgressBar) findViewById(R.id.downloadProgressBar);
    }

    public boolean mapsAvailableLocally() {
        // TODO: store this as a preference with a default value
        final File oamPath = getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR);

        File osm_gemf = new File(oamPath, HomeActivity.OSM_MAP_FILE);
        File oam_gemf = new File(oamPath, HomeActivity.OAM_MAP_FILE);

        return osm_gemf.exists() && oam_gemf.exists();
    }

}
