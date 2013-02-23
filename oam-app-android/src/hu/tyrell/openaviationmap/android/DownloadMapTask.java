/*
 * Copyright 2013 CodeSlap
 *
 *   Authors: Cristian C. <cristian@elhacker.net>
 *            Evelio T.   <eveliotc@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.tyrell.openaviationmap.android;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Vector;

import org.apache.http.util.ByteArrayBuffer;

import android.os.Bundle;

import com.codeslap.groundy.Groundy;
import com.codeslap.groundy.GroundyTask;

public class DownloadMapTask extends GroundyTask {

    private static final String FILELIST_URL =
                                "http://www.openaviationmap.org/android.files";
    private static final String FILELIST_FILE = "android.files";

    public static final String KEY_ESTIMATED = "estimated";
    public static final String KEY_COUNT = "count";
    public static final String KEY_CANCELLED = "cancelled";

    private static final String TAG = "DownloadMapTask";

    private List<URL> urls;

    @Override
    protected boolean doInBackground() {
        // first, download the torrent file
        try {
            downloadFilelistFile();

            File filelistFile = getFilelistFile();
            if (!filelistFile.exists()) {
                send(Groundy.STATUS_ERROR, new Bundle());
                return false;
            }

            parseFilelist();
            downloadFiles();

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

    private File getFilelistFile() {
        File  oamPath = getContext().getExternalFilesDir(
                                                HomeActivity.DEFAULT_OAM_DIR);
        File  filelistFile = new File(oamPath, FILELIST_FILE);

        return filelistFile;
    }

    private void downloadFilelistFile() throws IOException {
        File filelistFile = getFilelistFile();
        if (filelistFile.exists()) {
            filelistFile.delete();
        }

        URL                 url  = new URL(FILELIST_URL);
        URLConnection       conn = url.openConnection();
        InputStream         is   = conn.getInputStream();
        BufferedInputStream bis  = new BufferedInputStream(is);
        ByteArrayBuffer     bab  = new ByteArrayBuffer(64);
        byte[]              buf  = new byte[256];
        int                 c;

        while ((c = bis.read(buf, 0, buf.length)) != -1) {
            bab.append(buf, 0, c);
            send(Groundy.STATUS_RUNNING, new Bundle());

            if (isQuitting()) {
                return;
            }
        }

        getContext().getExternalFilesDir(HomeActivity.DEFAULT_OAM_DIR).mkdirs();
        FileOutputStream file = new FileOutputStream(filelistFile);
        file.write(bab.toByteArray());
        file.close();
    }

    private void parseFilelist() throws IOException {
        File filelistFile = getFilelistFile();
        if (!filelistFile.exists()) {
            return;
        }

        BufferedReader br = new BufferedReader(new FileReader(filelistFile));
        urls = new Vector<URL>();
        String line;
        while ((line = br.readLine()) != null) {
            urls.add(new URL(line));
        }
        br.close();
    }

    private void downloadFiles() throws NoSuchAlgorithmException, IOException {
        File oamPath = getContext().getExternalFilesDir(
                                                HomeActivity.DEFAULT_OAM_DIR);
        oamPath.mkdirs();

        long estTotal = 0;
        for (URL url : urls) {
            URLConnection c = url.openConnection();
            estTotal += c.getContentLength();
        }

        long done = 0;
        Bundle resultData = new Bundle();
        for (URL url : urls) {
            URLConnection       conn    = url.openConnection();
            InputStream         is      = conn.getInputStream();
            BufferedInputStream bis     = new BufferedInputStream(is);
            byte[]              buf     = new byte[256 * 1024];
            String              fileName = url.getPath();
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/"));
            }
            File                outFile = new File(oamPath, fileName);
            FileOutputStream    out     = new FileOutputStream(outFile);
            int                 percent = -1;
            long                nextNotification = -1;
            int                 c;

            while (!isQuitting() && (c = bis.read(buf, 0, buf.length)) != -1) {
                out.write(buf, 0, c);

                done += c;
                int newPercent = (int) (100l * done / estTotal);
                long now = System.currentTimeMillis();
                if (percent < newPercent || nextNotification < now) {
                    percent = newPercent;

                    resultData.putInt(Groundy.KEY_PROGRESS, percent);
                    resultData.putLong(KEY_ESTIMATED, estTotal);
                    resultData.putLong(KEY_COUNT, done);

                    send(Groundy.STATUS_PROGRESS, resultData);

                    nextNotification = now + 1000;
                }
            }
            out.close();
            bis.close();
        }

        addIntResult(Groundy.KEY_PROGRESS, 100);
        addLongResult(KEY_ESTIMATED, estTotal);
        addLongResult(KEY_COUNT, done);
    }
}