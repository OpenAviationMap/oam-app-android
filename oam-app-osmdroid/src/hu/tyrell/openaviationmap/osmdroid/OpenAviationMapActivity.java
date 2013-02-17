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
package hu.tyrell.openaviationmap.osmdroid;

import hu.tyrell.openaviationmap.osmdroid.constants.OpenAviationMapConstants;
import hu.tyrell.openaviationmap.osmdroid.views.util.MapTileProviderFactory;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

/**
 * Default map view activity.
 *
 * @author Akos Maroy
 *
 */
public class OpenAviationMapActivity extends Activity implements OpenAviationMapConstants {

	private SharedPreferences mPrefs;
	private MapView mOsmv;
	private MyLocationOverlay mLocationOverlay;
	private ScaleBarOverlay mScaleBarOverlay;
	private ITileSource mBaseTileSource;
	private ResourceProxy mResourceProxy;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mResourceProxy = new ResourceProxyImpl(getApplicationContext());

		mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

		final RelativeLayout rl = new RelativeLayout(this);
		rl.setBackgroundColor(Color.WHITE);

        MapTileProviderBase osmTileProvider =
                MapTileProviderFactory.getInstance(getApplicationContext(), "osm");

		this.mOsmv = new MapView(this, 256, mResourceProxy, osmTileProvider);
		this.mOsmv.setBackgroundColor(0xFFFFFF);
        this.mOsmv.setBuiltInZoomControls(true);
        this.mOsmv.setMultiTouchControls(true);

		rl.addView(this.mOsmv, new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
        this.setContentView(rl);

        this.mLocationOverlay = new MyLocationOverlay(this.getBaseContext(), this.mOsmv,
                mResourceProxy);
        this.mOsmv.getOverlays().add(mLocationOverlay);
        mLocationOverlay.enableCompass();
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();

        mScaleBarOverlay = new ScaleBarOverlay(this.getBaseContext(), mResourceProxy);
        mScaleBarOverlay.setNautical();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setStyle(Style.FILL_AND_STROKE);
        paint.setAlpha(255);
        paint.setTextSize(14);
        mScaleBarOverlay.setTextPaint(paint);
        mScaleBarOverlay.setBarPaint(paint);
        mOsmv.getOverlays().add(mScaleBarOverlay);

        mBaseTileSource = new XYTileSource("osm", null, 0, 15, 256, ".png");
        osmTileProvider.setTileSource(mBaseTileSource);
        mOsmv.setTileSource(mBaseTileSource);

        MapTileProviderBase oamTileProvider =
                MapTileProviderFactory.getInstance(getApplicationContext(), "oam");
        ITileSource tileSource = new XYTileSource("oam", null, 0, 15, 256, ".png");
        oamTileProvider.setTileSource(tileSource);
        TilesOverlay oamTilesOverlay = new TilesOverlay(oamTileProvider, this.getBaseContext());
        oamTilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        mOsmv.getOverlays().add(oamTilesOverlay);

        //mOsmv.getController().setZoom(mPrefs.getInt(PREFS_ZOOM_LEVEL, 1));
        //mOsmv.scrollTo(mPrefs.getInt(PREFS_SCROLL_X, 0), mPrefs.getInt(PREFS_SCROLL_Y, 0));

        // zoom to the Hungary
        mOsmv.getController().setZoom(9);
        mOsmv.getController().setCenter(new GeoPoint(47800000, 19100000));


        // create a 'show my location' button
        final ImageView ivZoomIn = new ImageView(this);
        ivZoomIn.setImageResource(R.drawable.center);
        /* Create RelativeLayoutParams, that position it in the top right corner. */
        final RelativeLayout.LayoutParams zoominParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        zoominParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        zoominParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        rl.addView(ivZoomIn, zoominParams);

        ivZoomIn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mLocationOverlay.isFollowLocationEnabled()) {
                    mLocationOverlay.disableFollowLocation();
                } else {
                    mLocationOverlay.enableFollowLocation();
                }
            }
        });


		/*
		 * This is an example of usage of runOnFirstFix.
		 * It looks more complicated than necessary because we need to create an
		 * extra thread and a handler.
		 * If you wanted to do a non-GUI thread then you wouldn't need the handler.
		 */
		if (DEBUGMODE) {
			final Handler handler = new Handler();
			mLocationOverlay.runOnFirstFix(new Runnable() {
				@Override
				public void run() {
					handler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getApplicationContext(),
									R.string.first_fix_message,
									Toast.LENGTH_LONG).show();
						}
					});
				}
			});
		}
	}

	@Override
	protected void onPause() {
		final SharedPreferences.Editor edit = mPrefs.edit();
		edit.putString(PREFS_TILE_SOURCE, mOsmv.getTileProvider().getTileSource().name());
		edit.putInt(PREFS_SCROLL_X, mOsmv.getScrollX());
		edit.putInt(PREFS_SCROLL_Y, mOsmv.getScrollY());
		edit.putInt(PREFS_ZOOM_LEVEL, mOsmv.getZoomLevel());
		edit.putBoolean(PREFS_SHOW_LOCATION, mLocationOverlay.isMyLocationEnabled());
		edit.putBoolean(PREFS_SHOW_COMPASS, mLocationOverlay.isCompassEnabled());
		edit.commit();

		this.mLocationOverlay.disableMyLocation();
		this.mLocationOverlay.disableCompass();

		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			mOsmv.setTileSource(mBaseTileSource);
		} catch (final IllegalArgumentException ignore) {
		}
		if (mPrefs.getBoolean(PREFS_SHOW_LOCATION, false)) {
			this.mLocationOverlay.enableMyLocation();
		}
		if (mPrefs.getBoolean(PREFS_SHOW_COMPASS, false)) {
			this.mLocationOverlay.enableCompass();
		}
	}

	@Override
	public boolean onTrackballEvent(final MotionEvent event) {
		return this.mOsmv.onTrackballEvent(event);
	}
}

