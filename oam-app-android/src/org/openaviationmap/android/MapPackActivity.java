package org.openaviationmap.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Vector;

import org.openaviationmap.android.mappack.MapPack;
import org.openaviationmap.android.mappack.MapPacks;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.NavUtils;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.codeslap.groundy.DetachableResultReceiver;
import com.codeslap.groundy.Groundy;
import com.codeslap.groundy.GroundyManger;
import com.euedge.openaviationmap.android.R;

public class MapPackActivity extends SherlockActivity {

    public static final String MAPPACKS_FILE = "oam.files";
    private static final int SOCKET_TIMEOUT = 4000;

    // use the activity ID as its a unique number
    private static int DOWNLOAD_NOTIFICATIONS = R.layout.activity_map_pack;
    private static int GROUP                  = R.layout.activity_map_pack;

    private static final String KEY_PROGRESS       = "progress";
    private static final String KEY_RECEIVER       = "receiver";
    private static final String KEY_DOWNLOAD_START = "download_start";
    private static final String KEY_STATE          = "state";
    private static final String KEY_DESIRED_STATES = "desired_states";
    private static final String KEY_TO_DELETE      = "to_delete";
    private static final String KEY_TO_DOWNLOAD    = "to_download";

    private static final String TAG = MapPackActivity.class.getName();

    private State           state = State.SELECTING;
    private TextView        note;
    private ProgressBar     downloadProgress;
    private ProgressBar     spinner;
    private ListView        listView;
    private MapPackAdapter  packAdapter;
    private Menu            menu;
    private boolean[]       desiredStates;
    private MapPacks        packs;
    private MapPacks        toDelete = null;
    private MapPacks        toDownload = null;
    private long            downloadStart;
    private boolean         visible = true;

    private NotificationManager        notificaitonMgr;
    private NotificationCompat.Builder notificationBuilder;

    private DetachableResultReceiver detachableReceiver;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_map_pack);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.action_manage_maps);

        notificaitonMgr = (NotificationManager)
                                        getSystemService(NOTIFICATION_SERVICE);
        notificationBuilder = new NotificationCompat.Builder(this);

        spinner = (ProgressBar) findViewById(R.id.map_pack_progress);
        listView = (ListView) findViewById(R.id.map_pack_list_view);
        note = (TextView) findViewById(R.id.textNote);
        downloadProgress = (ProgressBar) findViewById(R.id.downloadProgressBar);

        packAdapter = new MapPackAdapter(this,
                                         R.id.map_pack_list_view,
                                         new ArrayList<MapPack>());
        listView.setAdapter(packAdapter);

        if (savedState != null) {
            state = (State) savedState.getParcelable(KEY_STATE);
            desiredStates = savedState.getBooleanArray(KEY_DESIRED_STATES);
            toDelete = (MapPacks) savedState.getParcelable(KEY_TO_DELETE);
            toDownload = (MapPacks) savedState.getParcelable(KEY_TO_DOWNLOAD);
            downloadStart = savedState.getLong(KEY_DOWNLOAD_START);
            updateMainProgress(0, 0, 0, savedState.getInt(KEY_PROGRESS));
            detachableReceiver = savedState.getParcelable(KEY_RECEIVER);
        } else {
            detachableReceiver = new DetachableResultReceiver(new Handler());
        }
        detachableReceiver.setReceiver(receiver);

        new GetMappacksTask().execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.map_pack, menu);
        this.menu = menu;

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        setupMenu(menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;

        case R.id.action_apply:
            applyChanges();
            return true;

        case R.id.action_cancel:
            GroundyManger.cancelTasks(this, GROUP);
            notificaitonMgr.cancel(DOWNLOAD_NOTIFICATIONS);
            state = State.SELECTING;
            packAdapter.notifyDataSetChanged();
            setupUI();
            return true;

        case R.id.action_pause:
            GroundyManger.cancelTasks(this, GROUP);
            notificaitonMgr.cancel(DOWNLOAD_NOTIFICATIONS);
            state = State.PAUSED;
            packAdapter.notifyDataSetChanged();
            setupUI();
            return true;

        case R.id.action_resume_download:
            startDownload();
            packAdapter.notifyDataSetChanged();
            setupUI();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save desired checkbox states
        int nChildren = listView.getChildCount();
        desiredStates = new boolean[nChildren];
        forEachCheckbox(R.id.desired_state, new CheckboxFunction() {
            @Override public void f(int idx, CheckBox cb) {
                desiredStates[idx] = cb.isChecked();
            }
        });

        outState.putParcelable(KEY_STATE, state);
        outState.putBooleanArray(KEY_DESIRED_STATES, desiredStates);
        outState.putParcelable(KEY_TO_DELETE, toDelete);
        outState.putParcelable(KEY_TO_DOWNLOAD, toDownload);
        outState.putLong(KEY_DOWNLOAD_START, downloadStart);
        outState.putInt(KEY_PROGRESS, downloadProgress.getProgress());
        outState.putParcelable(KEY_RECEIVER, detachableReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        visible = true;

        if (state != State.DOWNLOADING) {
            notificaitonMgr.cancel(DOWNLOAD_NOTIFICATIONS);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        setupUI();
    }

    @Override
    protected void onPause() {
        super.onPause();

        visible = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        detachableReceiver.clearReceiver();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private File getDataPath() {
        return getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR);
    }

    private File getMappacksFile() {
        File  oamPath = getDataPath();
        File  filelistFile = new File(oamPath, MAPPACKS_FILE);

        return filelistFile;
    }

    /**
     * A function object to be used with forEachCheckbox()
     *
     * @see #forEachCheckbox()
     */
    private interface CheckboxFunction {
        public void f(int idx, CheckBox cb);
    }

    /**
     * Perform a particular task for each checkbox in the list view, of
     * a specified id.
     *
     * @param id the resource id of the checkbox to look at, either
     *        R.id.desired_state or R.id.existing_state
     * @param f the function to perform
     */
    private void forEachCheckbox(int id, CheckboxFunction f) {
        int nChildren = listView.getChildCount();
        for (int i = 0; i < nChildren; ++i) {
            View rl = listView.getChildAt(i);
            CheckBox c = (CheckBox) rl.findViewById(id);

            f.f(i, c);
        }
    }

    /**
     * Set up the UI based on the state of the activity.
     */
    private void setupUI() {
        switch (state) {
        case COMPLETE:
        default:
            note.setVisibility(View.GONE);
            downloadProgress.setVisibility(View.GONE);
            spinner.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);

            forEachCheckbox(R.id.desired_state, new CheckboxFunction() {
                @Override
                public void f(int idx, CheckBox cb) { cb.setEnabled(true); }
            });
            break;

        case PAUSED:
            note.setVisibility(View.VISIBLE);
            downloadProgress.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);

            forEachCheckbox(R.id.desired_state, new CheckboxFunction() {
                @Override
                public void f(int idx, CheckBox cb) { cb.setEnabled(false); }
            });
            break;

        case DOWNLOADING:
            note.setVisibility(View.VISIBLE);
            downloadProgress.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);

            note.setText(R.string.download_starting);

            forEachCheckbox(R.id.desired_state, new CheckboxFunction() {
                @Override
                public void f(int idx, CheckBox cb) { cb.setEnabled(false); }
            });
            break;
        }

        if (menu != null) {
            setupMenu(menu);
        }
    }

    private void setupMenu(Menu menu) {
        MenuItem apply  = menu.findItem(R.id.action_apply);
        MenuItem resume = menu.findItem(R.id.action_resume_download);
        MenuItem pause  = menu.findItem(R.id.action_pause);
        MenuItem cancel = menu.findItem(R.id.action_cancel);

        switch (state) {
        default:
        case SELECTING:
            apply.setVisible(true);
            resume.setVisible(false);
            pause.setVisible(false);
            cancel.setVisible(false);
            break;

        case PAUSED:
            apply.setVisible(false);
            resume.setVisible(true);
            pause.setVisible(false);
            cancel.setVisible(true);
            break;

        case DOWNLOADING:
            apply.setVisible(false);
            resume.setVisible(false);
            pause.setVisible(true);
            cancel.setVisible(true);
            break;
        }
    }

    private void
    updateMainProgress(long count, long totalCount, long est, int progress) {
        if (downloadProgress != null) {
            downloadProgress.setIndeterminate(false);
            downloadProgress.setProgress(progress);
        }

        Resources res = getResources();
        long now = System.currentTimeMillis();
        long speed = 1000 * count / (now - downloadStart);
        long estSecsLeft  = 0;
        long estMinsLeft  = 0;
        long estHoursLeft = 0;
        if (count > 0) {
            double c = count;
            double secs = (now - downloadStart) / 1000d;
            long estSecs = (long) (secs / (c / (est - totalCount + c)));
            estSecsLeft = estSecs - (long) secs;
            estHoursLeft = estSecsLeft / 3600;
            estMinsLeft  = (estSecsLeft / 60) % 60;
            estSecsLeft %= 60;
        }

        String text = String.format(res.getString(R.string.download_inprogress),
                                    progress,
                                    Formatter.formatFileSize(this, totalCount),
                                    Formatter.formatFileSize(this, est),
                                    Formatter.formatFileSize(this, speed),
                                    estHoursLeft, estMinsLeft, estSecsLeft);
        note.setText(text);
    }

    private void
    showNotification(boolean complete,
                     long    count,
                     long    totalCount,
                     long    est,
                     int     progress) {

        if (complete) {
            notificationBuilder.setSmallIcon(R.drawable.navigation_accept);
            notificationBuilder.setContentTitle(
                                        getText(R.string.download_complete));
            notificationBuilder.setProgress(100, 100, false);
            notificationBuilder.setOngoing(false);
            notificationBuilder.setContentText("");
            notificationBuilder.setContentInfo("");

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MapPackActivity.class), 0);
            notificationBuilder.setContentIntent(contentIntent);
        } else {
            notificationBuilder.setContentTitle(
                                  getText(R.string.download_inprogress_label));
            notificationBuilder.setSmallIcon(R.drawable.download);
            notificationBuilder.setProgress(100, progress, false);
            notificationBuilder.setOngoing(true);

            Resources res = getResources();
            long now = System.currentTimeMillis();
            long speed = 1000 * count / (now - downloadStart);
            long estSecsLeft  = 0;
            long estMinsLeft  = 0;
            long estHoursLeft = 0;
            if (count > 0) {
                double c = count;
                double secs = (now - downloadStart) / 1000d;
                long estSecs = (long) (secs / (c / (est - totalCount + c)));
                estSecsLeft = estSecs - (long) secs;
                estHoursLeft = estSecsLeft / 3600;
                estMinsLeft  = (estSecsLeft / 60) % 60;
                estSecsLeft %= 60;
            }

            String text = String.format(
                    res.getString(R.string.download_inprogress_notification),
                                  Formatter.formatFileSize(this, totalCount),
                                  Formatter.formatFileSize(this, est),
                                  Formatter.formatFileSize(this, speed));
            notificationBuilder.setContentText(text);

            text = String.format(
                    res.getString(R.string.download_inprogress_percent),
                    progress, estHoursLeft, estMinsLeft, estSecsLeft);
            notificationBuilder.setContentInfo(text);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MapPackActivity.class), 0);
            notificationBuilder.setContentIntent(contentIntent);
        }


        notificaitonMgr.notify(DOWNLOAD_NOTIFICATIONS,
                               notificationBuilder.build());
    }

    private class GetMappacksTask extends AsyncTask<Void, Void, MapPacks> {
        @Override
        protected void onPreExecute() {
            listView.setVisibility(View.INVISIBLE);
            spinner.setVisibility(View.VISIBLE);
        }

        @Override
        protected MapPacks doInBackground(Void... voids) {
            try {
                downloadMappacksFile();
            } catch (IOException e) {
                // ok, couldn't download, but we still might have a file
                // locally
            }

            try {
                Serializer serializer = new Persister();
                packs = serializer.read(MapPacks.class, getMappacksFile());

                return packs;

            } catch (Exception e) {
            }

            return null;
        }

        @Override
        protected void onPostExecute(MapPacks packs) {
            spinner.setVisibility(View.GONE);
            setupUI();

            packAdapter.clear();
            if (packs != null) {
                for (MapPack p : packs.getMappacks()) {
                    packAdapter.add(p);
                }
            }
            packAdapter.notifyDataSetChanged();
        }
    }

    private synchronized void downloadMappacksFile() throws IOException {
        File mappacksFile = getMappacksFile();
        Resources res = getResources();

        URL               url = new URL(res.getString(R.string.map_packs_url));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (mappacksFile.exists()) {
            // only download if newer than what we have
            conn.setIfModifiedSince(mappacksFile.lastModified());
        }
        conn.setConnectTimeout(SOCKET_TIMEOUT);
        conn.connect();

        // only download if newer than what we have
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR).mkdirs();
            InputStream         is   = conn.getInputStream();
            BufferedInputStream bis  = new BufferedInputStream(is);
            byte[]              buf  = new byte[1024];
            FileOutputStream    file = new FileOutputStream(mappacksFile);
            int                 c;

            while ((c = bis.read(buf, 0, buf.length)) != -1) {
                file.write(buf, 0, c);
            }

            file.close();
            bis.close();
            mappacksFile.setLastModified(conn.getLastModified());
        }

        conn.disconnect();
    }

    private void applyChanges() {
        Vector<MapPack> toDeleteV = new Vector<MapPack>();
        Vector<MapPack> toDownloadV = new Vector<MapPack>();

        int nChildren = listView.getChildCount();
        for (int i = 0; i < nChildren; ++i) {
            View rl = listView.getChildAt(i);
            CheckBox d = (CheckBox) rl.findViewById(R.id.desired_state);
            CheckBox e = (CheckBox) rl.findViewById(R.id.existing_state);
            MapPack mp = (MapPack) d.getTag();

            if (e.isChecked() && !d.isChecked()) {
                toDeleteV.add(mp);
            } else if (d.isChecked() && !e.isChecked()) {
                toDownloadV.add(mp);
            }
        }

        toDelete = null;
        toDownload = null;

        if (!toDeleteV.isEmpty()) {
            toDelete = new MapPacks();
            toDelete.setMappacks(toDeleteV);
        }
        if (!toDownloadV.isEmpty()) {
            toDownload = new MapPacks();
            toDownload.setMappacks(toDownloadV);
        }

        Resources res = getResources();
        StringBuffer message = new StringBuffer();

        if (toDelete != null) {
            message.append(String.format(
                 res.getString(R.string.confirm_delete_map_packs),
                     Formatter.formatFileSize(this, toDelete.getSize())));
            message.append("<br/><br/>");
            for (MapPack pack : toDelete.getMappacks()) {
                message.append(String.format(
                        res.getString(R.string.map_pack_confirm_delete_line),
                        pack.getName(),
                        Formatter.formatFileSize(this, pack.getSize())));
            }
        }

        if (toDownload != null) {
            File path = getDataPath();
            long size = toDownload.getSize();
            long remaining = size - toDownload.getLocalSize(path);

            message.append(String.format(
                             res.getString(R.string.confirm_download_map_packs),
                                 Formatter.formatFileSize(this, remaining)));
            message.append("<br/><br/>");
            for (MapPack pack : toDownload.getMappacks()) {
                size = pack.getSize();
                remaining = size - pack.getLocalSize(path);

                message.append(String.format(
                    res.getString(R.string.map_pack_confirm_download_line),
                    pack.getName(),
                    Formatter.formatFileSize(this, remaining),
                    Formatter.formatFileSize(this, size)));
            }
        }

        if (message.length() > 0) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setMessage(Html.fromHtml(message.toString()));
            bld.setNegativeButton(R.string.cancel, null);
            bld.setPositiveButton(R.string.ok, onApplyDeleteDownloadListener);

            bld.create().show();
        }

    }

    private void doDelete() {
        if (toDelete == null) {
            return;
        }

        spinner.setVisibility(View.VISIBLE);

        File path = getDataPath();
        for (MapPack pack : toDelete.getMappacks()) {
            pack.deleteLocalFiles(path);
        }

        packAdapter.notifyDataSetChanged();

        spinner.setVisibility(View.GONE);
    }

    private void startDownload() {
        if (toDownload != null) {
            state = State.DOWNLOADING;
            downloadStart = System.currentTimeMillis();

            // start the download task
            Bundle params = new Bundle();
            params.putParcelable(DownloadMapTask.KEY_MAPPACKS, toDownload);

            Groundy groundy = Groundy.create(this, DownloadMapTask.class);
            groundy.receiver(detachableReceiver);
            groundy.group(GROUP);
            groundy.params(params);
            groundy.queue();
        } else {
            state = State.SELECTING;
            setupUI();
        }
    }

    private class MapPackAdapter extends ArrayAdapter<MapPack> {

        private final ArrayList<MapPack> items;

        public MapPackAdapter(Context context,
                              int listViewResourceId,
                              ArrayList<MapPack> items) {
            super(context, listViewResourceId, items);
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            MapPack pack = items.get(position);

            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(
                                              Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.map_pack_list_item, null);

                v.setClickable(true);
                v.setOnLongClickListener(listLongClickListener);
                v.setTag(pack);

                CheckBox cb = (CheckBox) v.findViewById(R.id.desired_state);
                cb.setOnCheckedChangeListener(onCheckListener);
            }

            File path = getDataPath();

            if (pack != null) {
                TextView t = (TextView) v.findViewById(R.id.firstLine);

                t.setText(pack.getName());

                Resources res = getResources();
                t = (TextView) v.findViewById(R.id.secondLine);
                t.setText(
                    String.format(res.getString(R.string.map_pack_second_line),
                           Formatter.formatFileSize(MapPackActivity.this,
                                                    pack.getSize()),
                           Formatter.formatFileSize(MapPackActivity.this,
                                                    pack.getLocalSize(path))));

                CheckBox cb = (CheckBox) v.findViewById(R.id.existing_state);
                cb.setChecked(pack.isAvailableLocally(path));
                cb.setEnabled(false);
                CheckBox cbb = (CheckBox) v.findViewById(R.id.desired_state);
                cbb.setTag(pack);
                if (desiredStates != null && position < desiredStates.length) {
                    cbb.setChecked(desiredStates[position]);
                } else {
                    cbb.setChecked(cb.isChecked());
                }
                cbb.setEnabled(state != State.DOWNLOADING
                            && state != State.PAUSED);
            }

            return v;
        }
    }

    private final CompoundButton.OnCheckedChangeListener onCheckListener =
            new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean        isChecked) {

            CheckBox cb = (CheckBox) buttonView;
            final MapPack pack = (MapPack) cb.getTag();

            if (isChecked) {
                // find dependencies and select them as well
                forEachCheckbox(R.id.desired_state, new CheckboxFunction() {
                    @Override public void f(int idx, CheckBox c) {
                        if (pack.dependsOn((MapPack) c.getTag())) {
                            c.setChecked(true);
                        }
                    }
                });
            } else {
                // uncheck packs that depend on this one
                forEachCheckbox(R.id.desired_state, new CheckboxFunction() {
                    @Override public void f(int idx, CheckBox c) {
                        if (((MapPack) c.getTag()).dependsOn(pack)) {
                            c.setChecked(false);
                        }
                    }
                });
            }

            // TODO: enable / disable the apply menu if there is a difference
        }
    };

    private final View.OnLongClickListener
    listLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Log.i(TAG, "long click!");

            MapPack pack = (MapPack) v.getTag();
            final File path = getDataPath();

            // find dependencies
            final Vector<MapPack> ps = new Vector<MapPack>();
            long totalSize = pack.getLocalSize(path);
            ps.add(pack);
            for (MapPack p : packs.getMappacks()) {
                if (p.dependsOn(pack)) {
                    ps.add(p);
                    totalSize += p.getLocalSize(path);
                }
            }

            Resources res = getResources();
            String message = String.format(
                            res.getString(R.string.confirm_clear_map_pack),
                            pack.getName(),
                            Formatter.formatFileSize(MapPackActivity.this,
                                                     totalSize));

            AlertDialog.Builder bld = new AlertDialog.Builder(
                                                        MapPackActivity.this);
            bld.setMessage(message);
            bld.setNegativeButton(R.string.cancel, null);
            bld.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (MapPack p : ps) {
                            p.deleteLocalFiles(path);
                        }
                        packAdapter.notifyDataSetChanged();
                    }
            });

            bld.create().show();

            return false;
        }
    };

    private final DialogInterface.OnClickListener
        onApplyDeleteDownloadListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            state = State.DOWNLOADING;
            setupUI();

            doDelete();
            startDownload();
        }
    };

    private final DetachableResultReceiver.Receiver receiver =
                                    new DetachableResultReceiver.Receiver() {
        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {
            case Groundy.STATUS_RUNNING: {
                if (downloadProgress != null) {
                    downloadProgress.setIndeterminate(true);
                }
            } break;

            case Groundy.STATUS_PROGRESS: {
                long count = resultData.getLong(DownloadMapTask.KEY_COUNT);
                long tc = resultData.getLong(DownloadMapTask.KEY_TOTAL_COUNT);
                long est = resultData.getLong(DownloadMapTask.KEY_ESTIMATED);
                int progress = resultData.getInt(Groundy.KEY_PROGRESS);

                showNotification(false, count, tc, est, progress);
                updateMainProgress(count, tc, est, progress);
            } break;

            case Groundy.STATUS_FINISHED: {
                if (state != State.PAUSED) {
                    state = resultData.containsKey(
                                                DownloadMapTask.KEY_CANCELLED)
                          ? State.CANCELLED
                          : State.COMPLETE;

                    desiredStates = null;
                    downloadProgress.setIndeterminate(false);
                    downloadProgress.setProgress(0);
                }

                if (visible) {
                    notificaitonMgr.cancel(DOWNLOAD_NOTIFICATIONS);
                } else {
                    long count = resultData.getLong(DownloadMapTask.KEY_COUNT);
                    long tc = resultData.getLong(
                                              DownloadMapTask.KEY_TOTAL_COUNT);
                    long est = resultData.getLong(
                                                DownloadMapTask.KEY_ESTIMATED);
                    int progress = resultData.getInt(Groundy.KEY_PROGRESS);

                    showNotification(true, count, tc, est, progress);
                }
                setupUI();
                packAdapter.notifyDataSetChanged();
            } break;

            default:
            case Groundy.STATUS_ERROR: {
                Toast t = Toast.makeText(MapPackActivity.this,
                                         R.string.download_error,
                                         Toast.LENGTH_LONG);
                t.show();
                notificaitonMgr.cancel(DOWNLOAD_NOTIFICATIONS);
                state = State.INCOMPLETE;
                desiredStates = null;
                setupUI();
                packAdapter.notifyDataSetChanged();
                downloadProgress.setIndeterminate(false);
                downloadProgress.setProgress(0);
            }
            }
        }
    };


    private enum State implements Parcelable {
        SELECTING(0),
        INCOMPLETE(1),
        COMPLETE(2),
        DOWNLOADING(3),
        CANCELLED(4),
        PAUSED(5);

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
                    return SELECTING;
                case 1:
                    return PAUSED;
                case 2:
                    return COMPLETE;
                case 3:
                    return DOWNLOADING;
                case 4:
                    return CANCELLED;
                case 5:
                    return PAUSED;
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

}
