package org.openaviationmap.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import org.openaviationmap.android.billing.IabHelper;
import org.openaviationmap.android.billing.IabResult;
import org.openaviationmap.android.billing.Inventory;
import org.openaviationmap.android.billing.Purchase;
import org.openaviationmap.android.billing.SkuDetails;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.euedge.openaviationmap.android.R;

public class AboutActivity extends SherlockActivity {

    private static final int IAB_REQUEST_CODE  = 112112;
    private static final String TAG = AboutActivity.class.getName();

    private DonationAdapter                     donationAdapter;
    private ArrayList<Pair<String, String> >    donationInfo;
    private IabHelper                           iabHelper;

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
    public void onStart() {
        super.onStart();

        // set up donations, if we have them
        Resources res = getResources();
        String licenseKey = res.getString(R.string.google_play_license_key);
        if (licenseKey != null && licenseKey.length() > 0) {
            iabHelper = new IabHelper(this, licenseKey);

            iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                @Override
                public void onIabSetupFinished(IabResult result) {
                    if (result.isSuccess()) {
                        iabHelper.queryInventoryAsync(true,
                                 Arrays.asList(HomeActivity.SKU_DONATION_NAMES),
                                 gotInventoryListener);
                    }
                }
            });
        }
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

    @Override
    public void onStop() {
        super.onStop();

        if (iabHelper != null) {
            iabHelper.dispose();
        }
    }

    @Override
    protected void onActivityResult(int     requestCode,
                                    int     resultCode,
                                    Intent  data) {
        if (requestCode == IAB_REQUEST_CODE) {
            iabHelper.handleActivityResult(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
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

    private class DonationAdapter extends ArrayAdapter<Pair<String, String> > {

        private final ArrayList<Pair<String, String> > items;

        public DonationAdapter(Context context,
                              int listViewResourceId,
                              ArrayList<Pair<String, String> > items) {
            super(context, listViewResourceId, items);
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            Pair<String, String> item = items.get(position);
            Resources res = getResources();
            String donate = res.getString(R.string.donate);

            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.about_donation_list_item, null);

                TextView tv = (TextView) v.findViewById(R.id.text);
                tv.setText(String.format(donate, item.second));
            }

            return v;
        }
    }

    IabHelper.QueryInventoryFinishedListener
    gotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isFailure()) {
                return;
            }

            donationInfo = new ArrayList<Pair<String, String>>();

            for (String sku : HomeActivity.SKU_DONATION_NAMES) {
                if (inventory.hasDetails(sku)) {
                    SkuDetails d = inventory.getSkuDetails(sku);
                    String price = d.getPrice();

                    donationInfo.add(new Pair<String, String>(sku, price));
                }
            }

            if (!donationInfo.isEmpty()) {
                findViewById(R.id.donate_text).setVisibility(View.VISIBLE);

                donationAdapter = new DonationAdapter(AboutActivity.this,
                                                      R.id.donate_list_view,
                                                      donationInfo);
                ListView lv = (ListView) findViewById(R.id.donate_list_view);
                lv.setAdapter(donationAdapter);
                lv.setVisibility(View.VISIBLE);
                lv.setOnItemClickListener(onDonateClickListener);
            }
        }
    };

    private final AdapterView.OnItemClickListener
    onDonateClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av,
                                View v, int position, long id) {

            String sku = donationInfo.get(position).first;

            if (iabHelper != null && !iabHelper.isAsyncOperationInProgress()) {
                iabHelper.launchPurchaseFlow(AboutActivity.this, sku,
                                 IAB_REQUEST_CODE, purchaseFinishedListener);
            }

        }
    };

    IabHelper.OnIabPurchaseFinishedListener
    purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {

            int code = result.getResponse();

            if (code != IabHelper.BILLING_RESPONSE_RESULT_OK
             && code != IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED
             && code != IabHelper.IABHELPER_USER_CANCELLED) {

                AlertDialog.Builder bld = new AlertDialog.Builder(
                                                            AboutActivity.this);
                bld.setMessage(R.string.donation_failed);
                bld.setNeutralButton(R.string.ok, null);
                bld.create().show();
                return;
            }

            if (result.isSuccess()) {
                iabHelper.consumeAsync(purchase, consumeFinishedListener);
            }
        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener
    consumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        @Override
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            AlertDialog.Builder bld = new AlertDialog.Builder(
                                                        AboutActivity.this);
            bld.setNeutralButton(R.string.ok, null);

            if (result.isSuccess()) {
                bld.setMessage(R.string.donation_successful);
            } else {
                bld.setMessage(R.string.donation_failed);
            }

            bld.create().show();
        }
    };

}
