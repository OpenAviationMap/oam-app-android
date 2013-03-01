package org.openaviationmap.android.mappack;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;

import android.os.Parcel;
import android.os.Parcelable;

public class MapPack implements Parcelable {
    @Attribute
    private String id;

    @Attribute(required=false)
    private String depends;

    @ElementList(inline=true, entry="name")
    private List<String> names;

    @ElementList(inline=true, entry="mapfile")
    private List<MapFile> mapfiles;

    public String getId() {
        return id;
    }

    public String getDepends() {
        return depends;
    }

    public List<String> getNames() {
        return names;
    }

    public String getName() {
        return getName(Locale.getDefault());
    }

    public String getName(Locale locale) {
        // TODO
        return names.isEmpty() ? "" : names.get(0);
    }

    public List<MapFile> getMapfiles() {
        return mapfiles;
    }

    public boolean dependsOn(MapPack other) {
        return depends != null && depends.equals(other.id);
    }

    public long getSize() {
        long size = 0;
        for (MapFile mf : mapfiles) {
            size += mf.getSize();
        }

        return size;
    }

    /**
     * Tell if the map pack is available locally, based on a local base
     * path.
     *
     * @param path the local base path. map pack files will be looked up
     *        relative to this path
     * @return if all files for the map pack are available and are of
     *         proper size
     */
    public boolean isAvailableLocally(File path) {
        for (MapFile mf : mapfiles) {
            File file = new File(path, mf.getLocalFileName());
            if (!file.exists() || file.length() != mf.getSize()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Delete the map pack local files, based on a local base path.
     *
     * @param path the local base path. map pack files will be looked up
     *        relative to this path
     */
    public void deleteLocalFiles(File path) {
        for (MapFile mf : mapfiles) {
            File file = new File(path, mf.getLocalFileName());
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(depends);
        dest.writeStringList(names);

        MapFile[] files;
        if (mapfiles == null) {
            files = new MapFile[0];
        } else {
            files = new MapFile[mapfiles.size()];
            for (int i = 0; i < mapfiles.size(); ++i) {
                files[i] = mapfiles.get(i);
            }
        }
        dest.writeParcelableArray(files, flags);
    }

    public static final Parcelable.Creator<MapPack> CREATOR
                                        = new Parcelable.Creator<MapPack>() {

        @Override
        public MapPack createFromParcel(Parcel source) {
            MapPack mp = new MapPack();
            mp.id = source.readString();
            mp.depends = source.readString();
            mp.names = new Vector<String>();
            source.readStringList(mp.names);
            Object[] mfs = source.readParcelableArray(
                                            MapPack.class.getClassLoader());
            mp.mapfiles = new Vector<MapFile>(mfs.length);
            for (Object o : mfs) {
                mp.mapfiles.add((MapFile) o);
            }

            return mp;
        }

        @Override
        public MapPack[] newArray(int size) {
            MapPack[] mps = new MapPack[size];
            for (int i = 0; i < size; ++i) {
                mps[i] = new MapPack();
            }

            return mps;
        }
    };

}
