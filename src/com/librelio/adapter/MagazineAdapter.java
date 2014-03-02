package com.librelio.adapter;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.librelio.LibrelioApplication;
import com.librelio.activity.BillingActivity;
import com.librelio.activity.MagazinesActivity;
import com.librelio.event.LoadPlistEvent;
import com.librelio.model.DictItem;
import com.librelio.model.Magazine;
import com.librelio.service.DownloadMagazineService;
import com.librelio.storage.MagazineManager;
import com.librelio.utils.SystemHelper;
import com.niveales.wind.R;
import de.greenrobot.event.EventBus;

import java.io.File;
import java.util.ArrayList;

public class MagazineAdapter extends BaseAdapter {

	private static final String TAG = "MagazineAdapter";
	private Context context;
	private ArrayList<DictItem> magazines;
	private boolean hasTestMagazine;

	public MagazineAdapter(ArrayList<DictItem> magazines, Context context, boolean hasTestMagazine) {
		this.context = context;
		this.magazines = magazines;
		this.hasTestMagazine = hasTestMagazine;
	}

	@Override
	public int getCount() {
		return magazines.size();
	}

	@Override
	public Object getItem(int position) {
		return position;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	static class MagazineItemHolder {
		public TextView title;
		public TextView subtitle;
		public ImageView thumbnail;
        public LinearLayout progressLayout;
        public TextView info;
        public ProgressBar progressBar;
		public Button downloadOrReadButton;
		public Button sampleOrDeleteButton;
        public int position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {

            MagazineItemHolder holder = new MagazineItemHolder();
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.magazine_list_item, parent, false);
                holder.title = (TextView) convertView.findViewById(R.id.item_title);
                holder.subtitle = (TextView) convertView.findViewById(R.id.item_subtitle);
                holder.thumbnail = (ImageView) convertView.findViewById(R.id.item_thumbnail);
                holder.progressLayout = (LinearLayout) convertView.findViewById(R.id.item_progress_layout);
                holder.info = (TextView) convertView.findViewById(R.id.item_info);
                holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progress_bar);
                holder.downloadOrReadButton = (Button) convertView.findViewById(R.id.item_button_one);
                holder.sampleOrDeleteButton = (Button) convertView.findViewById(R.id.item_button_two);
                convertView.setTag(holder);
            } else {
                holder = (MagazineItemHolder) convertView.getTag();
            }

        holder.title.setText(magazines.get(position).getTitle());

        if (holder.position != position) {
            holder.position = position;
            holder.thumbnail.setImageDrawable(null);
        }

        // Using an AsyncTask to load the slow images in a background thread
        new AsyncTask<MagazineItemHolder, Void, Bitmap>() {
            private MagazineItemHolder v;

            @Override
            protected Bitmap doInBackground(MagazineItemHolder... params) {
                v = params[0];
                try {
                    return SystemHelper.decodeSampledBitmapFromFile(magazines.get(position).getPngPath(),
                            (int) context.getResources().getDimension(R.dimen.preview_image_height),
                            (int) context.getResources().getDimension(R.dimen.preview_image_width));
                } catch (Resources.NotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                super.onPostExecute(result);
                if (v.position == position && result != null) {
                    v.thumbnail.setImageBitmap(result);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, holder);

        if (magazines.get(position) instanceof Magazine) {
            final Magazine currentMagazine = (Magazine) magazines.get(position);
            holder.subtitle.setText(currentMagazine.getSubtitle());

            // reset the visibilities
            holder.progressLayout.setVisibility(View.GONE);
            holder.downloadOrReadButton.setVisibility(View.INVISIBLE);
            holder.sampleOrDeleteButton.setVisibility(View.INVISIBLE);

            if (hasTestMagazine && currentMagazine.isFake()) {
                holder.downloadOrReadButton.setVisibility(View.VISIBLE);
                holder.downloadOrReadButton.setText(context.getResources().getString(R.string.read));
                holder.downloadOrReadButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (new File(currentMagazine.getItemPath()).exists()) {
                            LibrelioApplication.startPDFActivity(context,
                                    currentMagazine.getItemPath(),
                                    currentMagazine.getTitle(), true);
                        } else {
                            Toast.makeText(context, "No test pdf, check assets dir", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                return convertView;
            }

            if ((currentMagazine.getDownloadStatus() >= DownloadManager.STATUS_PENDING) &&
                    (currentMagazine.getDownloadStatus() <= DownloadManager.STATUS_FAILED)) {
                // currently downloading
                holder.progressLayout.setVisibility(View.VISIBLE);
                holder.downloadOrReadButton.setVisibility(View.INVISIBLE);
                holder.sampleOrDeleteButton.setVisibility(View.VISIBLE);
                if (currentMagazine.getDownloadStatus() == DownloadManager.STATUS_RUNNING || currentMagazine
                        .getDownloadStatus() == DownloadManager.STATUS_PENDING) {
                    holder.info.setText(context.getResources()
                            .getString(R.string.download_in_progress));
                } else if (currentMagazine.getDownloadStatus() == DownloadManager.STATUS_PAUSED) {
                    holder.info.setText("Queued");
                } else if (currentMagazine.getDownloadStatus() == DownloadManager.STATUS_FAILED) {
                    holder.info.setText("ERROR");
                } else if (currentMagazine.getDownloadStatus() == DownloadManager.STATUS_SUCCESSFUL) {
                    holder.info.setText(context.getResources()
                            .getString(R.string.download_in_progress));
                }

                if (currentMagazine.getDownloadStatus() == DownloadManager.STATUS_RUNNING && currentMagazine
                        .getDownloadProgress() > 0) {
                    holder.progressBar.setIndeterminate(false);
                    holder.progressBar.setProgress(currentMagazine.getDownloadProgress());
                } else {
                    holder.progressBar.setIndeterminate(true);
                }

                holder.sampleOrDeleteButton.setText(context.getResources().getString(R.string.cancel));
                holder.sampleOrDeleteButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        currentMagazine.clearMagazineDir();
                        MagazineManager.removeDownloadedMagazine(context, currentMagazine);
                        EventBus.getDefault().post(new LoadPlistEvent());
                    }
                });
            } else if (!currentMagazine.isDownloaded()) {
                holder.downloadOrReadButton.setVisibility(View.VISIBLE);
                if (currentMagazine.isPaid()) {
                    holder.downloadOrReadButton.setText(context.getResources()
                            .getString(R.string.download));
                } else {
                    holder.downloadOrReadButton.setText(context.getResources()
                            .getString(R.string.free_Download));
                }
                holder.downloadOrReadButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (currentMagazine.isPaid()) {
                            Intent intent = new Intent(context, BillingActivity.class);
                            intent.putExtra(BillingActivity.FILE_NAME_KEY, currentMagazine.getFileName());
                            intent.putExtra(BillingActivity.TITLE_KEY, currentMagazine.getTitle());
                            intent.putExtra(BillingActivity.SUBTITLE_KEY, currentMagazine.getSubtitle());
                            context.startActivity(intent);
                        } else {
                            DownloadMagazineService.startDownload(context, currentMagazine);
                        }
                    }
                });
                // Sample button
                if (currentMagazine.isPaid()) {
                    holder.sampleOrDeleteButton.setVisibility(View.VISIBLE);
                    if (currentMagazine.isSampleDownloaded()) {
                        holder.sampleOrDeleteButton.setText(context.getResources().getString(
                                R.string.read_sample));
                    } else {
                        holder.sampleOrDeleteButton.setText(context.getResources().getString(
                                R.string.sample));
                    }
                    holder.sampleOrDeleteButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            File sample = new File(currentMagazine.getSamplePdfPath());
                            Log.d(TAG, "test: " + sample.exists() + " "
                                    + currentMagazine.isSampleDownloaded());
                            if (currentMagazine.isSampleDownloaded()) {
                                LibrelioApplication.startPDFActivity(context,
                                        currentMagazine.getSamplePdfPath(),
                                        currentMagazine.getTitle(), true);
                            } else {
                                currentMagazine.setSample(true);
                                DownloadMagazineService.startDownload(context, currentMagazine);
                            }
                        }
                    });
                }
            } else if (currentMagazine.isDownloaded()) {
                // Read case
                holder.downloadOrReadButton.setVisibility(View.VISIBLE);
                holder.downloadOrReadButton.setText(context.getResources()
                        .getString(R.string.read));
                holder.downloadOrReadButton
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LibrelioApplication.startPDFActivity(context,
                                        currentMagazine.getItemPath(),
                                        currentMagazine.getTitle(), true);
                            }
                        });
            }
            int totalAssetCount = currentMagazine.getTotalAssetCount();
            int downloadedAssetCount = currentMagazine.getDownloadedAssetCount();
            if (totalAssetCount > 0 && downloadedAssetCount < totalAssetCount) {
                holder.progressLayout.setVisibility(View.VISIBLE);
                holder.progressBar.setIndeterminate(false);
                holder.progressBar.setProgress((int) ((downloadedAssetCount * 100.0f) / totalAssetCount));
                holder.info.setText(context.getResources()
                        .getString(R.string.download_in_progress) + "\n" + downloadedAssetCount + "/" + totalAssetCount);
            }
            return convertView;
        } else {
            holder.subtitle.setVisibility(View.GONE);
            holder.progressLayout.setVisibility(View.GONE);
            holder.info.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
            holder.downloadOrReadButton.setVisibility(View.GONE);
            holder.sampleOrDeleteButton.setVisibility(View.GONE);
            holder.thumbnail.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                	context.startActivity(MagazinesActivity.getIntent(context, magazines.get(position).getFileName()));
//                    context.startActivity(MainMagazineActivity.getIntent(context, magazines.get(position).getFileName()));
                }
            });
            return convertView;
        }
    }
}
