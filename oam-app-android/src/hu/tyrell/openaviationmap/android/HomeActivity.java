package hu.tyrell.openaviationmap.android;

import java.io.File;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.TilesOverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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

    private MapView             mOsmv;
    private MyLocationOverlay   mLocationOverlay;
    private ITileSource         mBaseTileSource;
    private ResourceProxy       mResourceProxy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File oamPath = getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR);
        if (!oamPath.exists()) {
            oamPath.mkdirs();
        }

        mResourceProxy = new ResourceProxyImpl(getApplicationContext());

        MapTileProviderBase osmTileProvider =
             MapTileProviderFactory.getInstance(getApplicationContext(), "osm");

        // replace the view in the layout definition with a manually created
        // osmand MapView
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.mapView);
        layout.setBackgroundColor(Color.WHITE);
        mOsmv = new MapView(this, 256, mResourceProxy, osmTileProvider);
        layout.addView(mOsmv,
                       new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                                                    LayoutParams.MATCH_PARENT));

        mOsmv.setBuiltInZoomControls(false);
        mOsmv.setMultiTouchControls(true);

        // add a current location overlay
        mLocationOverlay = new MyLocationOverlay(this.getBaseContext(), mOsmv,
                                                 mResourceProxy);
        mOsmv.getOverlays().add(mLocationOverlay);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();

        // add the ground map
        mBaseTileSource = new XYTileSource("osm", null, 0, 13, 256, ".png");
        osmTileProvider.setTileSource(mBaseTileSource);
        mOsmv.setTileSource(mBaseTileSource);

        // add the aviation map as an overlay
        MapTileProviderBase oamTileProvider =
             MapTileProviderFactory.getInstance(getApplicationContext(), "oam");
        ITileSource tileSource = new XYTileSource("oam", null, 0, 13, 256,
                                                  ".png");
        oamTileProvider.setTileSource(tileSource);
        TilesOverlay oamTilesOverlay = new TilesOverlay(oamTileProvider,
                                                        this.getBaseContext());
        oamTilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        mOsmv.getOverlays().add(oamTilesOverlay);

        // zoom to the Hungary
        mOsmv.getController().setZoom(9);
        mOsmv.getController().setCenter(new GeoPoint(47800000, 19100000));

        mLocationOverlay.enableFollowLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mapsAvailableLocally()) {
            startActivity(new Intent(HomeActivity.this,
                                     DownloadMapActivity.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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

        case R.id.action_show_location:
            mLocationOverlay.enableFollowLocation();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onTrackballEvent(final MotionEvent event) {
        return mOsmv.onTrackballEvent(event);
    }

    public boolean mapsAvailableLocally() {
        // TODO: store this as a preference with a default value
        final File oamPath = getExternalFilesDir(DEFAULT_OAM_DIR);

        File osm_gemf = new File(oamPath, OSM_MAP_FILE);
        File oam_gemf = new File(oamPath, OAM_MAP_FILE);

        return osm_gemf.exists() && oam_gemf.exists();
    }
}
