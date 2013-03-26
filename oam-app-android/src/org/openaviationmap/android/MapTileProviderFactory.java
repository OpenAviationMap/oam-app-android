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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.modules.ArchiveFileFactory;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GEMFFile;
import org.osmdroid.views.util.constants.MapViewConstants;

import android.content.Context;
import android.util.Pair;

/**
 * Map tile factory that reads only local file-based maps.
 *
 * @author Akos Maroy
 */
public class MapTileProviderFactory implements MapViewConstants {

	/**
	 * Get a tile provider by scanning the pre-defined data path
     * directory for stored files.
     *
     * @param aContext the context
     * @param baseName the base name of the layer to provide tiles for
	 */
	public static MapTileProviderBase
	getInstance(final Context aContext, final String baseName) {

		// list the archive files available
		final File oamPath = HomeActivity.getDataPath(aContext);

		final ArrayList<IArchiveFile> archiveFiles =
		                                          new ArrayList<IArchiveFile>();

        final File[] files = oamPath.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.getName().startsWith(baseName)) {
                    final IArchiveFile archiveFile =
                                        ArchiveFileFactory.getArchiveFile(file);
                    if (archiveFile != null) {
                        archiveFiles.add(archiveFile);
                    }
                }
            }
        }

        IArchiveFile[] aFiles = new IArchiveFile[archiveFiles.size()];
        aFiles = archiveFiles.toArray(aFiles);

        MapTileFileArchiveProvider mtfap = new MapTileFileArchiveProvider(
                new SimpleRegisterReceiver(aContext.getApplicationContext()),
                null,
                aFiles);

        MapTileModuleProviderBase[] tileProviders =
                                        new MapTileModuleProviderBase[1];
        tileProviders[0] = mtfap;

        MapTileProviderArray provider = new MapTileProviderArray(null,
                new SimpleRegisterReceiver(aContext.getApplicationContext()),
                tileProviders);

        return provider;
	}

	public static Pair<Integer, Integer>
	getMinMaxZoomLevels(final Context aContext, final String baseName) {
        // list the archive files available
        final File oamPath = HomeActivity.getDataPath(aContext);

        final File[] files = oamPath.listFiles();
        int minZoom = Integer.MAX_VALUE;
        int maxZoom = Integer.MIN_VALUE;
        if (files != null) {
            for (final File file : files) {
                if (file.getName().startsWith(baseName)) {
                    try {
                        GEMFFile gf = new GEMFFile(file);
                        Set<Integer> zoomLevels = gf.getZoomLevels();
                        for (int i : zoomLevels) {
                            minZoom = Math.min(minZoom, i);
                            maxZoom = Math.max(maxZoom, i);
                        }
                    } catch (IOException e) {
                    }
                }
            }
        }

        return new Pair<Integer, Integer>(minZoom, maxZoom);
	}

	/**
	 * This is a utility class with only static members.
	 */
	private MapTileProviderFactory() {
	}
}
