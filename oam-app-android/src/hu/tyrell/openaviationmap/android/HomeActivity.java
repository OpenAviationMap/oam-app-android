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
package hu.tyrell.openaviationmap.android;

import hu.tyrell.openaviationmap.android.R.id;

import java.io.File;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.TilesOverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class HomeActivity extends Activity {

    private static String TAG = "MainActivity";

    public static String DEFAULT_OAM_DIR = "openaviationmap";
    public static String OSM_MAP_FILE    = "osm.gemf";
    public static String OAM_MAP_FILE    = "oam.gemf";

    private static String PREFERENCES_NAME  =
                                        HomeActivity.class.getCanonicalName();
    private static String KEY_ZOOM_LEVEL      = "zoom_level";
    private static String KEY_SCROLL_X        = "scroll_x";
    private static String KEY_SCROLL_Y        = "scroll_y";
    private static String KEY_SHOW_LOCATION   = "show_location";
    private static String KEY_FOLLOW_LOCATION = "follow_location";
    private static String KEY_WAKE_LOCK       = "wake_lock";

    private MapView             mOsmv            = null;
    private MyLocationOverlay   mLocationOverlay = null;
    private ITileSource         mBaseTileSource  = null;
    private ResourceProxy       mResourceProxy   = null;
    private MapTileProviderBase mOsmTileProvider = null;
    private MapTileProviderBase mOamTileProvider = null;

    private boolean             followFellow     = false;

    private Menu                menu     = null;
    PowerManager.WakeLock       wakeLock = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File oamPath = getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR);
        if (!oamPath.exists()) {
            oamPath.mkdirs();
        }

        mResourceProxy = new ResourceProxyImpl(getApplicationContext());

        mOsmTileProvider = MapTileProviderFactory.getInstance(
                                            getApplicationContext(), "osm");

        // replace the view in the layout definition with a manually created
        // osmand MapView
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mapView);
        layout.setBackgroundColor(Color.WHITE);
        mOsmv = new MapView(this, 256, mResourceProxy, mOsmTileProvider);
        layout.addView(mOsmv,
                  new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                                                LayoutParams.MATCH_PARENT));

        mOsmv.setBuiltInZoomControls(false);
        mOsmv.setMultiTouchControls(true);

        // add the ground map
        mBaseTileSource = new XYTileSource("osm", null, 0, 13, 256, ".png");

        mOsmTileProvider.setTileSource(mBaseTileSource);
        mOsmv.setTileSource(mBaseTileSource);

        // add the aviation map as an overlay
        mOamTileProvider = MapTileProviderFactory.getInstance(
                                            getApplicationContext(), "oam");
        ITileSource tileSource = new XYTileSource("oam", null, 0, 13, 256,
                                                  ".png");
        mOamTileProvider.setTileSource(tileSource);
        TilesOverlay oamTilesOverlay = new TilesOverlay(mOamTileProvider,
                                                     this.getBaseContext());
        oamTilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        mOsmv.getOverlays().add(oamTilesOverlay);

        // add a current location overlay
        mLocationOverlay = new MyLocationOverlay(this.getBaseContext(),
                                                mOsmv, mResourceProxy);

        mOsmv.getOverlays().add(mLocationOverlay);
        mLocationOverlay.enableMyLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mapsAvailableLocally()) {
            startActivity(new Intent(HomeActivity.this,
                                     DownloadMapActivity.class));
        }

        SharedPreferences prefs =
                        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);

        // zoom to the center of the map
        mOsmv.getController().setZoom(prefs.getInt(KEY_ZOOM_LEVEL, 9));
        mOsmv.setScrollX(prefs.getInt(KEY_SCROLL_X, 6954));
        mOsmv.setScrollY(prefs.getInt(KEY_SCROLL_Y, -19865));

        if (prefs.getBoolean(KEY_SHOW_LOCATION, true)) {
            mLocationOverlay.enableMyLocation();
        } else {
            mLocationOverlay.disableMyLocation();
        }
        if (prefs.getBoolean(KEY_FOLLOW_LOCATION, true)) {
            followFellow = true;
            mLocationOverlay.enableFollowLocation();
        } else {
            followFellow = false;
            mLocationOverlay.disableFollowLocation();
        }

        if (prefs.getBoolean(KEY_WAKE_LOCK, false)) {
            PowerManager pm = (PowerManager)
                                        getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                                      getText(R.string.app_name).toString());
            wakeLock.acquire();
        } else {
            if (wakeLock != null) {
                wakeLock.release();
            }
            wakeLock = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        SharedPreferences prefs =
                        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();

        edit.putInt(KEY_SCROLL_X, mOsmv.getScrollX());
        edit.putInt(KEY_SCROLL_Y, mOsmv.getScrollY());
        edit.putInt(KEY_ZOOM_LEVEL, mOsmv.getZoomLevel());
        edit.putBoolean(KEY_SHOW_LOCATION,
                                        mLocationOverlay.isMyLocationEnabled());
        edit.putBoolean(KEY_FOLLOW_LOCATION, followFellow);
        edit.putBoolean(KEY_WAKE_LOCK, wakeLock != null);
        edit.commit();

        mLocationOverlay.disableMyLocation();
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        mOamTileProvider.clearTileCache();
        mOsmTileProvider.clearTileCache();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        this.menu = menu;

        MenuItem locMenu = menu.findItem(id.action_follow_location);
        if (followFellow) {
            locMenu.setIcon(R.drawable.follow_location_on);
            locMenu.setTitle(R.string.action_follow_location_dont);
        } else {
            locMenu.setIcon(R.drawable.follow_location_off);
            locMenu.setTitle(R.string.action_follow_location);
        }

        int reqOrientation = getRequestedOrientation();
        MenuItem orientationMenu = menu.findItem(id.action_screen_lock);

        if (reqOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            orientationMenu.setIcon(R.drawable.screen_locked_to_landscape);
            orientationMenu.setTitle(R.string.action_screen_unlock);
        } else if (reqOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            orientationMenu.setIcon(R.drawable.screen_locked_to_portrait);
            orientationMenu.setTitle(R.string.action_screen_unlock);
        } else {
            orientationMenu.setIcon(R.drawable.screen_not_locked);
            orientationMenu.setTitle(R.string.action_screen_lock);
        }

        MenuItem wakeLockMenu = menu.findItem(id.action_wake_lock);
        if (wakeLock != null) {
            wakeLockMenu.setIcon(R.drawable.wake_lock);
            wakeLockMenu.setTitle(R.string.action_wake_unlock);
        } else {
            wakeLockMenu.setIcon(R.drawable.wake_auto);
            wakeLockMenu.setTitle(R.string.action_wake_lock);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_get_maps:
            startActivity(new Intent(HomeActivity.this,
                                     DownloadMapActivity.class));
            return true;

        case R.id.action_zoom_in:
            mOsmv.getController().zoomIn();
            return true;

        case R.id.action_zoom_out:
            mOsmv.getController().zoomOut();
            return true;

        case R.id.action_follow_location:
            followFellow = true;
            mLocationOverlay.enableFollowLocation();
            item.setIcon(R.drawable.follow_location_on);
            item.setTitle(R.string.action_follow_location_dont);
            return true;

        case R.id.action_screen_lock:
            // if it's a locked orientation, free it up:
            int reqOrientation = getRequestedOrientation();
            if (reqOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
             || reqOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {

                item.setIcon(R.drawable.screen_not_locked);
                item.setTitle(R.string.action_screen_lock);
                setRequestedOrientation(
                                  ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                return true;
            }

            // otherwise, freeze the current orientation
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                item.setIcon(R.drawable.screen_locked_to_landscape);
                item.setTitle(R.string.action_screen_unlock);
                setRequestedOrientation(
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                return true;
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                item.setIcon(R.drawable.screen_locked_to_portrait);
                item.setTitle(R.string.action_screen_unlock);
                setRequestedOrientation(
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return true;
            }

        case R.id.action_wake_lock:
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(
                                                        Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                                         getText(R.string.app_name).toString());
                wakeLock.acquire();

                item.setIcon(R.drawable.wake_lock);
                item.setTitle(R.string.action_wake_unlock);
            } else {
                wakeLock.release();
                wakeLock = null;

                item.setIcon(R.drawable.wake_auto);
                item.setTitle(R.string.action_wake_lock);
            }

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        followFellow = false;

        MenuItem locMenu = menu.findItem(id.action_follow_location);
        locMenu.setIcon(R.drawable.follow_location_off);

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        followFellow = false;

        MenuItem locMenu = menu.findItem(id.action_follow_location);
        locMenu.setIcon(R.drawable.follow_location_off);

        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(final MotionEvent event) {
        followFellow = false;

        MenuItem locMenu = menu.findItem(id.action_follow_location);
        locMenu.setIcon(R.drawable.follow_location_off);

        return mOsmv.onTrackballEvent(event);
    }

    public boolean mapsAvailableLocally() {
        final File oamPath = getExternalFilesDir(DEFAULT_OAM_DIR);

        File osm_gemf = new File(oamPath, OSM_MAP_FILE);
        File oam_gemf = new File(oamPath, OAM_MAP_FILE);

        return osm_gemf.exists() && oam_gemf.exists();
    }
}
