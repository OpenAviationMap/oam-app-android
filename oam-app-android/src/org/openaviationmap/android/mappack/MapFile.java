package org.openaviationmap.android.mappack;

import org.simpleframework.xml.Attribute;

import android.os.Parcel;
import android.os.Parcelable;

public class MapFile implements Parcelable {
    @Attribute
    private long size;

    @Attribute
    private String url;

    public long getSize() {
        return size;
    }

    public String getUrl() {
        return url;
    }

    public String getLocalFileName() {
        int lastSlash = url.lastIndexOf('/');
        return lastSlash == -1 ? url : url.substring(lastSlash);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(size);
        dest.writeString(url);
    }

    public static final Parcelable.Creator<MapFile> CREATOR
                                        = new Parcelable.Creator<MapFile>() {

        @Override
        public MapFile createFromParcel(Parcel source) {
            MapFile mf = new MapFile();
            mf.size = source.readLong();
            mf.url  = source.readString();

            return mf;
        }

        @Override
        public MapFile[] newArray(int size) {
            MapFile[] mfs = new MapFile[size];
            for (int i = 0; i < size; ++i) {
                mfs[i] = new MapFile();
            }

            return mfs;
        }
    };

}
