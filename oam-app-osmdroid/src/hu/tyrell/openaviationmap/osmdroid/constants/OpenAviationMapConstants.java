/*
    Open Aviation Map
    Copyright (C) 2012 Ákos Maróy

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
package hu.tyrell.openaviationmap.osmdroid.constants;

/**
 * This class contains constants used by the applications.
 *
 * @author Akos Maroy
 */
public interface OpenAviationMapConstants {

	public static final String DEBUGTAG = "OPENAVIATIONMAP";

	public static final boolean DEBUGMODE = false;

	public static final int NOT_SET = Integer.MIN_VALUE;

	public static final String PREFS_NAME = "hu.tyrell.openaviationmap.osmdorid.prefs";
	public static final String PREFS_TILE_SOURCE = "tilesource";
	public static final String PREFS_SCROLL_X = "scrollX";
	public static final String PREFS_SCROLL_Y = "scrollY";
	public static final String PREFS_ZOOM_LEVEL = "zoomLevel";
	public static final String PREFS_SHOW_LOCATION = "showLocation";
	public static final String PREFS_SHOW_COMPASS = "showCompass";
}
