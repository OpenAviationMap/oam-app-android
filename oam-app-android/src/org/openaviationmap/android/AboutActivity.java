package org.openaviationmap.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.euedge.openaviationmap.android.R;

public class AboutActivity extends SherlockActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.about);

        try {
            TextView tv = (TextView) findViewById(R.id.info_text);
            PackageInfo pi = getPackageManager().
                                        getPackageInfo(getPackageName(), 0);

            tv.setText(Html.fromHtml(
                   String.format(readRawTextFile(R.raw.info), pi.versionName)));
        } catch (NameNotFoundException e) {
        }

        TextView tv = (TextView) findViewById(R.id.credits_text);

        tv.setText(Html.fromHtml(readRawTextFile(R.raw.credits)));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public String readRawTextFile(int id) {
        InputStream inputStream = getResources().openRawResource(id);
        InputStreamReader in = new InputStreamReader(inputStream);
        BufferedReader buf = new BufferedReader(in);
        String line;
        StringBuilder text = new StringBuilder();

        try {
            while (( line = buf.readLine()) != null) text.append(line);
        } catch (IOException e) {
            return null;
        }

        return text.toString();
    }

}
