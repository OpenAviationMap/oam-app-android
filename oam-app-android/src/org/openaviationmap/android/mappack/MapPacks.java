package org.openaviationmap.android.mappack;

import java.util.List;
import java.util.Vector;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import android.os.Parcel;
import android.os.Parcelable;

@Root
public class MapPacks implements Parcelable {
    @ElementList(inline=true, entry="mappack")
    private List<MapPack> mappacks;

    public long getSize() {
        long size = 0;
        for (MapPack pack : mappacks) {
            size += pack.getSize();
        }

        return size;
    }

    public void setMappacks(List<MapPack> mappacks) {
        this.mappacks = mappacks;
    }

    public List<MapPack> getMappacks() {
        return mappacks;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        MapPack[] packs;
        if (mappacks == null) {
            packs = new MapPack[0];
        } else {
            packs = new MapPack[mappacks.size()];
            for (int i = 0; i < mappacks.size(); ++i) {
                packs[i] = mappacks.get(i);
            }
        }
        dest.writeParcelableArray(packs, flags);
    }

    public static final Parcelable.Creator<MapPacks> CREATOR
                                        = new Parcelable.Creator<MapPacks>() {

        @Override
        public MapPacks createFromParcel(Parcel source) {
            MapPacks mps = new MapPacks();
            Object[] os = source.readParcelableArray(
                                            MapPacks.class.getClassLoader());
            mps.mappacks = new Vector<MapPack>(os.length);
            for (Object o : os) {
                mps.mappacks.add((MapPack) o);
            }

            return mps;
        }

        @Override
        public MapPacks[] newArray(int size) {
            MapPacks[] mpsa = new MapPacks[size];
            for (int i = 0; i < size; ++i) {
                mpsa[i] = new MapPacks();
            }

            return mpsa;
        }
    };

}
