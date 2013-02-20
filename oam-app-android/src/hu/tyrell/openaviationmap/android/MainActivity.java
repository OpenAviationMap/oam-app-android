package hu.tyrell.openaviationmap.android;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

    private static String TAG = "MainActivity";

    public static String DEFAULT_OAM_DIR = "openaviationmap";
    public static String OSM_MAP_FILE    = "osm.gemf";
    public static String OAM_MAP_FILE    = "oam.gemf";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "onCreate");
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");

        if (!mapsAvailableLocally()) {
            Intent i = new Intent(MainActivity.this, DownloadMapActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            //i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public static File getDataPath() {
        final File oamPath = new File(Environment.getExternalStorageDirectory(),
                                      DEFAULT_OAM_DIR);

        return oamPath;
    }
    /**
     * Check if the map files are available locally.
     */
    public static boolean mapsAvailableLocally() {
        // TODO: store this as a preference with a default value
        final File oamPath = getDataPath();

        File osm_gemf = new File(oamPath, OSM_MAP_FILE);
        File oam_gemf = new File(oamPath, OAM_MAP_FILE);

        return osm_gemf.exists() && oam_gemf.exists();
    }
}
