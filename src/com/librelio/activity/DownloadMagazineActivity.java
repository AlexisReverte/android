package com.librelio.activity;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.librelio.LibrelioApplication;
import com.librelio.base.BaseActivity;
import com.librelio.event.MagazineDownloadedEvent;
import com.librelio.model.DownloadStatus;
import com.librelio.model.Magazine;
import com.librelio.storage.MagazineManager;
import com.niveales.wind.R;

public class DownloadMagazineActivity extends BaseActivity {

    private Magazine magazine;
    private ProgressBar progress;
    private Handler handler = new Handler();

    private Runnable loadPlistTask = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MagazineManager.updateMagazineDetails(DownloadMagazineActivity.this, magazine);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (magazine.getDownloadStatus() > DownloadStatus.QUEUED && magazine.getDownloadStatus() < DownloadStatus.DOWNLOADED) {
                                progress.setIndeterminate(false);
                                progress.setProgress(magazine.getDownloadStatus());
                                progressText.setText(R.string.download_in_progress);
                            } else {
                                progress.setIndeterminate(true);
                                progressText.setText(getString(R.string.queued));
                            }
                            handler.postDelayed(loadPlistTask, 2000);
                        }
                    });
                }
            });
        }
    };
	private TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_magazines_activity);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        String title = getIntent().getExtras().getString(BillingActivity.TITLE_KEY);
        String subtitle = getIntent().getExtras().getString(BillingActivity.SUBTITLE_KEY);
        String fileName = getIntent().getExtras().getString(BillingActivity.FILE_NAME_KEY);
        boolean isSample = getIntent().getExtras().getBoolean(BillingActivity.IS_SAMPLE_KEY);

        magazine = new Magazine(fileName, title, subtitle, "", this);
        magazine.setSample(isSample);

        ImageView preview = (ImageView) findViewById(R.id.download_preview_image);
        progress = (ProgressBar)findViewById(R.id.download_progress);
        progressText = (TextView)findViewById(R.id.download_progress_text);
        preview.setImageBitmap(BitmapFactory.decodeFile(magazine.getPngPath()));

        ((TextView) findViewById(R.id.item_title)).setText(title);
        ((TextView) findViewById(R.id.item_subtitle)).setText(subtitle);

        Button cancelButton = (Button) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                magazine.clearMagazineDir();
                MagazineManager.removeDownloadedMagazine(DownloadMagazineActivity.this, magazine);
                finish();
            }
        });
    }

    public void onEventMainThread(MagazineDownloadedEvent event) {
        if (event.getMagazine().getFileName().equals(magazine.getFileName())) {
            LibrelioApplication.startPDFActivity(this, magazine.getItemPath(), magazine.getTitle(), true);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(loadPlistTask);
        handler.post(loadPlistTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(loadPlistTask);
    }
}
