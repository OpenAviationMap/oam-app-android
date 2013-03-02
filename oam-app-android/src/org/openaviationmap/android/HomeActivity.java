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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import org.openaviationmap.android.billing.IabHelper;
import org.openaviationmap.android.billing.IabResult;
import org.openaviationmap.android.billing.Inventory;
import org.openaviationmap.android.billing.Purchase;
import org.openaviationmap.android.billing.SkuDetails;
import org.openaviationmap.android.mappack.MapPack;
import org.openaviationmap.android.mappack.MapPacks;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Pair;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.euedge.openaviationmap.android.R;

public class HomeActivity extends SherlockActivity {

    private static final String TAG = "MainActivity";

    public static final String DEFAULT_OAM_DIR = "openaviationmap";
    private static final String LAST_VERSION_FILE = "last.version";

    private static final int TILE_SIZE = 256;

    private static final String PREFERENCES_NAME  =
                                        HomeActivity.class.getCanonicalName();
    private static final String KEY_ZOOM_LEVEL      = "zoom_level";
    private static final String KEY_LATITUDE        = "latitude";
    private static final String KEY_LONGITUDE       = "longitude";
    private static final String KEY_SHOW_LOCATION   = "show_location";
    private static final String KEY_FOLLOW_LOCATION = "follow_location";
    private static final String KEY_MANUAL_LOCATION_LATITUDE =
                                                    "manual_location_latitude";
    private static final String KEY_MANUAL_LOCATION_LONGITUDE =
                                                    "manual_location_longitude";
    private static final String KEY_WAKE_LOCK       = "wake_lock";

    private static final int DEFAULT_LATITUDE = 47161707;
    private static final int DEFAULT_LONGITUDE = 18951416;
    private static final int DEFAULT_ZOOM = 0;

    private static final String SKU_DONATION_0 = "donation_0";
    private static final String SKU_DONATION_1 = "donation_1";
    private static final String SKU_DONATION_2 = "donation_2";
    private static final String SKU_DONATION_3 = "donation_3";
    private static final String SKU_DONATION_4 = "donation_4";
    private static final int IAB_REQUEST_CODE  = 112111;

    private static final String[] SKU_DONATION_NAMES = { SKU_DONATION_0,
            SKU_DONATION_1, SKU_DONATION_2, SKU_DONATION_3, SKU_DONATION_4 };

    private MapView             mOsmv            = null;
    private MyLocationOverlay   mLocationOverlay = null;
    private ITileSource         mBaseTileSource  = null;
    private ResourceProxy       mResourceProxy   = null;
    private MapTileProviderBase mOsmTileProvider = null;
    private MapTileProviderBase mOamTileProvider = null;

    private boolean             followFellow     = false;
    private IGeoPoint           manualLocation;

    private Menu                menu     = null;
    PowerManager.WakeLock       wakeLock = null;

    private IabHelper           iabHelper;

    private final ArrayList<String>   donation_skus   = new ArrayList<String>();
    private final ArrayList<String>   donation_prices = new ArrayList<String>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        File oamPath = getDataPath();
        if (!oamPath.exists()) {
            oamPath.mkdirs();
        }

        migrateFromPreviousVersion();

        // set up the map view
        mResourceProxy = new ResourceProxyImpl(getApplicationContext());

        Pair<Integer, Integer> zoomLevels = MapTileProviderFactory.
                                            getMinMaxZoomLevels(this,  "osm");
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
        mBaseTileSource = new XYTileSource("osm", null,
                                            zoomLevels.first, zoomLevels.second,
                                            TILE_SIZE, ".png");

        mOsmTileProvider.setTileSource(mBaseTileSource);
        mOsmv.setTileSource(mBaseTileSource);

        // add the aviation map as an overlay
        zoomLevels = MapTileProviderFactory.getMinMaxZoomLevels(this,  "osm");

        mOamTileProvider = MapTileProviderFactory.getInstance(
                                            getApplicationContext(), "oam");
        ITileSource tileSource = new XYTileSource("oam", null,
                                            zoomLevels.first, zoomLevels.second,
                                            TILE_SIZE, ".png");
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

        // set up in-app donations
        Resources res = getResources();
        String licenseKey = res.getString(R.string.google_play_license_key);
        if (licenseKey != null && licenseKey.length() > 0) {
            iabHelper = new IabHelper(this, licenseKey);

            iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                @Override
                public void onIabSetupFinished(IabResult result) {
                    if (result.isSuccess()) {
                        iabHelper.queryInventoryAsync(true,
                                             Arrays.asList(SKU_DONATION_NAMES),
                                             gotInventoryListener);
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mapsAvailableLocally()) {
            startActivity(new Intent(HomeActivity.this,
                                     MapPackActivity.class));
        }

        SharedPreferences prefs =
                        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);

        // zoom to the center of the map
        int zoomLevel = prefs.getInt(KEY_ZOOM_LEVEL, DEFAULT_ZOOM);
        if (zoomLevel < mOsmv.getMinZoomLevel()) {
            zoomLevel = mOsmv.getMinZoomLevel();
        } else if (zoomLevel > mOsmv.getMaxZoomLevel()) {
            zoomLevel = mOsmv.getMaxZoomLevel();
        }
        mOsmv.getController().setZoom(zoomLevel);

        GeoPoint center =
                new GeoPoint(prefs.getInt(KEY_LATITUDE, DEFAULT_LATITUDE),
                             prefs.getInt(KEY_LONGITUDE, DEFAULT_LONGITUDE));

        mOsmv.getController().setCenter(center);

        if (prefs.getBoolean(KEY_SHOW_LOCATION, true)) {
            mLocationOverlay.enableMyLocation();
        } else {
            mLocationOverlay.disableMyLocation();
        }
        manualLocation = new GeoPoint(
                prefs.getInt(KEY_MANUAL_LOCATION_LATITUDE, DEFAULT_LATITUDE),
                prefs.getInt(KEY_MANUAL_LOCATION_LONGITUDE, DEFAULT_LONGITUDE));
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
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                                    | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                    | PowerManager.ON_AFTER_RELEASE,
                                      getText(R.string.app_name).toString());
            wakeLock.acquire();
        } else {
            if (wakeLock != null) {
                wakeLock.release();
            }
            wakeLock = null;
        }

        if (menu != null) {
            MenuItem wakeLockMenu = menu.findItem(R.id.action_wake_lock);
            if (wakeLock != null) {
                wakeLockMenu.setIcon(R.drawable.wake_lock);
                wakeLockMenu.setTitle(R.string.action_wake_unlock);
            } else {
                wakeLockMenu.setIcon(R.drawable.wake_auto);
                wakeLockMenu.setTitle(R.string.action_wake_lock);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        SharedPreferences prefs =
                        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();

        IGeoPoint center = mOsmv.getMapCenter();

        edit.putInt(KEY_LATITUDE, center.getLatitudeE6());
        edit.putInt(KEY_LONGITUDE, center.getLongitudeE6());
        edit.putInt(KEY_ZOOM_LEVEL, mOsmv.getZoomLevel());
        edit.putBoolean(KEY_SHOW_LOCATION,
                                        mLocationOverlay.isMyLocationEnabled());
        edit.putBoolean(KEY_FOLLOW_LOCATION, followFellow);
        edit.putInt(KEY_MANUAL_LOCATION_LATITUDE,
                                               manualLocation.getLatitudeE6());
        edit.putInt(KEY_MANUAL_LOCATION_LONGITUDE,
                                               manualLocation.getLongitudeE6());
        edit.putBoolean(KEY_WAKE_LOCK, wakeLock != null);
        edit.commit();

        mLocationOverlay.disableMyLocation();
    }

    @Override
    public void onStop() {
        super.onStop();

        mOamTileProvider.clearTileCache();
        mOsmTileProvider.clearTileCache();

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.main, menu);
        this.menu = menu;

        MenuItem locMenu = menu.findItem(R.id.action_follow_location);
        if (followFellow) {
            locMenu.setIcon(R.drawable.follow_location_on);
            locMenu.setTitle(R.string.action_follow_location_dont);
        } else {
            locMenu.setIcon(R.drawable.follow_location_off);
            locMenu.setTitle(R.string.action_follow_location);
        }

        int reqOrientation = getRequestedOrientation();
        MenuItem orientationMenu = menu.findItem(R.id.action_screen_lock);

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

        MenuItem wakeLockMenu = menu.findItem(R.id.action_wake_lock);
        if (wakeLock != null) {
            wakeLockMenu.setIcon(R.drawable.wake_lock);
            wakeLockMenu.setTitle(R.string.action_wake_unlock);
        } else {
            wakeLockMenu.setIcon(R.drawable.wake_auto);
            wakeLockMenu.setTitle(R.string.action_wake_lock);
        }

        appendDonationsToMenu();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_manage_maps:
            startActivity(new Intent(HomeActivity.this, MapPackActivity.class));
            return true;

        case R.id.action_zoom_in:
            mOsmv.getController().zoomIn();
            return true;

        case R.id.action_zoom_out:
            mOsmv.getController().zoomOut();
            return true;

        case R.id.action_follow_location:
            if (followFellow) {
                followFellow = false;
                mOsmv.getController().animateTo(manualLocation);
                item.setIcon(R.drawable.follow_location_off);
                item.setTitle(R.string.action_follow_location);
            } else {
                manualLocation = mOsmv.getMapCenter();
                followFellow = true;
                mLocationOverlay.enableFollowLocation();
                item.setIcon(R.drawable.follow_location_on);
                item.setTitle(R.string.action_follow_location_dont);
            }
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
            return true;

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
            return true;

        case R.id.action_donate_0:
            if (iabHelper != null && !iabHelper.isAsyncOperationInProgress()) {
                iabHelper.launchPurchaseFlow(this, donation_skus.get(0),
                                 IAB_REQUEST_CODE, purchaseFinishedListener);
            }
            return true;

        case R.id.action_donate_1:
            if (iabHelper != null && !iabHelper.isAsyncOperationInProgress()) {
                iabHelper.launchPurchaseFlow(this, donation_skus.get(1),
                                 IAB_REQUEST_CODE, purchaseFinishedListener);
            }
            return true;

        case R.id.action_donate_2:
            if (iabHelper != null && !iabHelper.isAsyncOperationInProgress()) {
                iabHelper.launchPurchaseFlow(this, donation_skus.get(2),
                                 IAB_REQUEST_CODE, purchaseFinishedListener);
            }
            return true;

        case R.id.action_donate_3:
            if (iabHelper != null && !iabHelper.isAsyncOperationInProgress()) {
                iabHelper.launchPurchaseFlow(this, donation_skus.get(3),
                                 IAB_REQUEST_CODE, purchaseFinishedListener);
            }
            return true;

        case R.id.action_donate_4:
            if (iabHelper != null && !iabHelper.isAsyncOperationInProgress()) {
                iabHelper.launchPurchaseFlow(this, donation_skus.get(4),
                                 IAB_REQUEST_CODE, purchaseFinishedListener);
            }
            return true;

        case R.id.action_about:
            startActivity(new Intent(HomeActivity.this, AboutActivity.class));
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        followFellow = false;

        MenuItem locMenu = menu.findItem(R.id.action_follow_location);
        locMenu.setIcon(R.drawable.follow_location_off);

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        followFellow = false;

        MenuItem locMenu = menu.findItem(R.id.action_follow_location);
        locMenu.setIcon(R.drawable.follow_location_off);

        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(final MotionEvent event) {
        followFellow = false;

        MenuItem locMenu = menu.findItem(R.id.action_follow_location);
        locMenu.setIcon(R.drawable.follow_location_off);

        return mOsmv.onTrackballEvent(event);
    }

    private File getDataPath() {
        return getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR);
    }

    public boolean mapsAvailableLocally() {
        File  oamPath = getDataPath();
        File  packFile = new File(oamPath, MapPackActivity.MAPPACKS_FILE);

        if (!packFile.exists()) {
            return false;
        }

        try {
            Serializer serializer = new Persister();
            MapPacks packs = serializer.read(MapPacks.class, packFile);
            if (packs == null) {
                return false;
            }

            for (MapPack pack : packs.getMappacks()) {
                if (pack.isAvailableLocally(oamPath)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    IabHelper.QueryInventoryFinishedListener
    gotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isFailure()) {
                return;
            }

            for (String sku : SKU_DONATION_NAMES) {
                if (inventory.hasDetails(sku)) {
                    SkuDetails d = inventory.getSkuDetails(sku);

                    String price = d.getPrice();

                    donation_skus.add(sku);
                    donation_prices.add(price);
                }
            }

            appendDonationsToMenu();
        }
    };

    private void appendDonationsToMenu() {
        if (donation_skus.size() != donation_prices.size() || menu == null) {
            return;
        }

        Resources res = getResources();
        String    donate = res.getString(R.string.donate);
        MenuItem item;

        switch (donation_skus.size()) {
        default:
        case 5:
            item = menu.findItem(R.id.action_donate_4);
            item.setTitle(String.format(donate, donation_prices.get(4)));
            item.setVisible(true);

        case 4:
            item = menu.findItem(R.id.action_donate_3);
            item.setTitle(String.format(donate, donation_prices.get(3)));
            item.setVisible(true);

        case 3:
            item = menu.findItem(R.id.action_donate_2);
            item.setTitle(String.format(donate, donation_prices.get(2)));
            item.setVisible(true);

        case 2:
            item = menu.findItem(R.id.action_donate_1);
            item.setTitle(String.format(donate, donation_prices.get(1)));
            item.setVisible(true);

        case 1:
            item = menu.findItem(R.id.action_donate_0);
            item.setTitle(String.format(donate, donation_prices.get(0)));
            item.setVisible(true);

        case 0:
        }
    }

    private void migrateFromPreviousVersion() {
        try {
            File path = getDataPath();
            File versionFile = new File(path, LAST_VERSION_FILE);
            int lastVersion = 0;
            if (versionFile.exists()) {
                BufferedReader br = new BufferedReader(
                                                new FileReader(versionFile));
                String str = br.readLine();
                br.close();

                try {
                    lastVersion = Integer.parseInt(str);
                } catch (NumberFormatException e) {
                }
            }

            switch (lastVersion) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7: {
                // handle changes from version 7 to 8 here
                Vector<File> files = new Vector<File>();

                files.add(new File(path, "android.files"));
                files.add(new File(path, "osm.gemf"));
                files.add(new File(path, "osm.gemf-1"));
                files.add(new File(path, "oam.gemf"));

                for (File f : files) {
                    if (f.exists()) {
                        f.delete();
                    }
                }
            };

            default:
            }

            PackageInfo pi = getPackageManager().
                    getPackageInfo("com.euedge.openaviationmap.android", 0);

            // save the current version
            FileWriter fw = new FileWriter(versionFile);
            fw.write(Integer.toString(pi.versionCode));
            fw.close();

        } catch (IOException e) {
        } catch (NameNotFoundException e) {
        }
    }

    IabHelper.OnIabPurchaseFinishedListener
    purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (result.isFailure()) {
                AlertDialog.Builder bld = new AlertDialog.Builder(
                                                            HomeActivity.this);
                bld.setMessage(R.string.donation_failed);
                bld.setNeutralButton(R.string.ok, null);
                bld.create().show();
                return;
            }

            iabHelper.consumeAsync(purchase, consumeFinishedListener);
        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener
    consumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        @Override
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            AlertDialog.Builder bld = new AlertDialog.Builder(HomeActivity.this);
            bld.setNeutralButton(R.string.ok, null);

            if (result.isSuccess()) {
                bld.setMessage(R.string.donation_successful);
            } else {
                bld.setMessage(R.string.donation_failed);
            }

            bld.create().show();
        }
    };

}
