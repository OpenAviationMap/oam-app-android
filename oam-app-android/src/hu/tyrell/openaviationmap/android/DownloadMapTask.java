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

import java.io.File;
import java.io.IOException;

import android.os.Bundle;

import com.codeslap.groundy.Groundy;
import com.codeslap.groundy.GroundyTask;

public class DownloadMapTask extends GroundyTask {

    public static final String KEY_ESTIMATED = "estimated";
    public static final String KEY_COUNT = "count";

    @Override
    protected boolean doInBackground() {
        int time = getIntParam(KEY_ESTIMATED);
        if (time < 1000) {
            time = 1000;
        }

        int interval = time / 10;

        int currentPercentage = 0;
        while (currentPercentage <= 100 && !isQuitting()) {
            try {
                Bundle resultData = new Bundle();
                resultData.putInt(Groundy.KEY_PROGRESS, currentPercentage);
                resultData.putInt(KEY_COUNT, currentPercentage);
                resultData.putInt(KEY_ESTIMATED, 100);
                send(Groundy.STATUS_PROGRESS, resultData);

                // let's fake some work ^_^
                Thread.sleep(interval);
                currentPercentage++;
            } catch (InterruptedException e) {
            }
        }

        // just create empty files
        send(Groundy.STATUS_RUNNING, new Bundle());
        try {
            File oamPath = MainActivity.getDataPath();
            File osm_gemf = new File(oamPath, MainActivity.OSM_MAP_FILE);
            File oam_gemf = new File(oamPath, MainActivity.OAM_MAP_FILE);

            oamPath.mkdirs();
            osm_gemf.createNewFile();
            oam_gemf.createNewFile();
        } catch (IOException e) {
        }

        return true;
    }
}
