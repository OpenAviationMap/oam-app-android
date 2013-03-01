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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;

import org.openaviationmap.android.mappack.MapFile;
import org.openaviationmap.android.mappack.MapPack;
import org.openaviationmap.android.mappack.MapPacks;

import android.os.Bundle;

import com.codeslap.groundy.Groundy;
import com.codeslap.groundy.GroundyTask;

public class DownloadMapTask extends GroundyTask {

    public static final String KEY_MAPPACKS = "mappacks";
    public static final String KEY_ESTIMATED = "estimated";
    public static final String KEY_COUNT = "count";
    public static final String KEY_TOTAL_COUNT = "total_count";
    public static final String KEY_CANCELLED = "cancelled";

    private static final String TAG = "DownloadMapTask";

    private MapPacks packs;
    private long count;
    private long totalCount;
    private long estTotal;

    @Override
    protected boolean doInBackground() {
        packs = (MapPacks) getParameters().getParcelable(KEY_MAPPACKS);

        // first, download the torrent file
        try {
            downloadPacks();

        } catch (IOException e) {
            send(Groundy.STATUS_ERROR, new Bundle());
            return false;
        } catch (NoSuchAlgorithmException e) {
            send(Groundy.STATUS_ERROR, new Bundle());
            return false;
        }

        if (isQuitting()) {
            addIntResult(KEY_CANCELLED, true);
        }

        return true;
    }

    private void downloadPacks() throws NoSuchAlgorithmException, IOException {
        File oamPath = getContext().getExternalFilesDir(
                                                HomeActivity.DEFAULT_OAM_DIR);
        oamPath.mkdirs();

        estTotal = packs.getSize();

        count = 0;
        totalCount = 0;
        for (MapPack pack : packs.getMappacks()) {
            for (MapFile file : pack.getMapfiles()) {
                download(file.getUrl(), oamPath);
            }
        }

        addIntResult(Groundy.KEY_PROGRESS, (int) (totalCount / estTotal));
        addLongResult(KEY_ESTIMATED, estTotal);
        addLongResult(KEY_COUNT, count);
        addLongResult(KEY_TOTAL_COUNT, totalCount);
    }

    private void download(String urlStr, File oamPath)
                                    throws IOException, FileNotFoundException {

        Bundle resultData = new Bundle();

        URL                 url     = new URL(urlStr);
        URLConnection       conn    = url.openConnection();

        String              fileName = url.getPath();
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/"));
        }
        File                outFile = new File(oamPath, fileName);
        // if the file exists, continue the download
        if (outFile.exists()) {
            long existingSize = outFile.length();
            totalCount += existingSize;
            conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
        }
        FileOutputStream    out     = new FileOutputStream(outFile, true);

        InputStream         is      = conn.getInputStream();
        BufferedInputStream bis     = new BufferedInputStream(is);
        byte[]              buf     = new byte[256 * 1024];

        int                 percent = -1;
        long                nextNotification = -1;
        int                 c;

        while (!isQuitting() && (c = bis.read(buf, 0, buf.length)) != -1) {
            out.write(buf, 0, c);

            count += c;
            totalCount += c;
            int newPercent = (int) (100l * totalCount / estTotal);
            long now = System.currentTimeMillis();
            if (percent < newPercent || nextNotification < now) {
                percent = newPercent;

                resultData.putInt(Groundy.KEY_PROGRESS, percent);
                resultData.putLong(KEY_ESTIMATED, estTotal);
                resultData.putLong(KEY_COUNT, count);
                resultData.putLong(KEY_TOTAL_COUNT, totalCount);

                send(Groundy.STATUS_PROGRESS, resultData);

                nextNotification = now + 1000;
            }
        }
        out.close();
        bis.close();
    }
}
