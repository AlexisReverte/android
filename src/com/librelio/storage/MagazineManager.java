package com.librelio.storage;

import java.util.ArrayList;
import java.util.List;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.librelio.base.BaseManager;
import com.librelio.event.ChangeInDownloadedMagazinesEvent;
import com.librelio.model.Magazine;

import de.greenrobot.event.EventBus;

public class MagazineManager extends BaseManager {
	private static final String TAG = "MagazineManager";
	public static final String TEST_FILE_NAME = "test/test.pdf";
    private final DownloadManager downloadManager;

    public MagazineManager(Context context) {
		super(context);

        downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
	}

//	public List<Magazine> getMagazines(boolean hasTestMagazine) {
//		List<Magazine> magazines = new ArrayList<Magazine>();
//		if (hasTestMagazine) {
//			magazines.add(new Magazine(TEST_FILE_NAME, "TEST", "test", "", getContext()));
//		}
//        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getReadableDatabase();
//        Cursor c = db.rawQuery("select Magazines._id,Magazines.filename,Magazines.title," +
//                "DownloadedMagazines.downloaddate,Magazines.subtitle,DownloadedMagazines.sample," +
//                "DownloadedMagazines.downloadmanagerid from " + Magazine
//                .TABLE_MAGAZINES + " LEFT JOIN " + Magazine
//                .TABLE_DOWNLOADED_MAGAZINES + " ON " +
//                Magazine.TABLE_MAGAZINES + "." + Magazine.FIELD_FILE_NAME + "=" + Magazine
//                .TABLE_DOWNLOADED_MAGAZINES + "." + Magazine.FIELD_FILE_NAME,
//                null);
//        while (c.moveToNext()) {
//            Magazine magazine = new Magazine(c, getContext());
//            // Update download status from DownloadManager
//            DownloadManager.Query q = new DownloadManager.Query();
//            q.setFilterById(magazine.getDownloadManagerId());
//            Cursor cursor = downloadManager.query(q);
//            if (cursor.moveToFirst()) {
//                magazine.setDownloadStatus(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
//                long fileSize = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
//                long bytesDL = cursor.getLong(cursor.getColumnIndex(DownloadManager
//                        .COLUMN_BYTES_DOWNLOADED_SO_FAR));
//                magazine.setDownloadProgress((int) ((bytesDL * 100.0f) / fileSize));
//            }
//            cursor.close();
//            magazine.setTotalAssetCount(getTotalAssetCount(magazine));
//            magazine.setDownloadedAssetCount(getDownloadedAssetCount(magazine));
//            magazines.add(magazine);
//        }
//        c.close();
//		return magazines;
//	}

    public List<Magazine> getDownloadedMagazines(boolean hasTestMagazine) {
        List<Magazine> magazines = new ArrayList<Magazine>();
        if (hasTestMagazine) {
            magazines.add(new Magazine(TEST_FILE_NAME, "TEST", "test", "", getContext()));
        }
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getReadableDatabase();
        Cursor c = db.rawQuery("select * from " + DataBaseHelper.TABLE_DOWNLOADED_MAGAZINES, null);
        while (c.moveToNext()) {
            Magazine magazine = new Magazine(c, getContext());
            if (magazine.isDownloaded() || magazine.isSampleDownloaded()) {
                magazines.add(magazine);
            }
        }
        c.close();
        return magazines;
    }

	public synchronized void addMagazine(Magazine magazine, String tableName, boolean withSample) {
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(DataBaseHelper.FIELD_FILE_NAME, magazine.getFileName());
		cv.put(DataBaseHelper.FIELD_DOWNLOAD_DATE, magazine.getDownloadDate());
		cv.put(DataBaseHelper.FIELD_TITLE, magazine.getTitle());
		cv.put(DataBaseHelper.FIELD_SUBTITLE, magazine.getSubtitle());
		if (withSample){
			cv.put(DataBaseHelper.FIELD_IS_SAMPLE, magazine.isSampleForBase());
            cv.put(DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID, magazine.getDownloadManagerId());
		}
        // Add magazine and set id of magazine to newly created row id
		magazine.setId(db.insert(tableName, null, cv));

		EventBus.getDefault().post(new ChangeInDownloadedMagazinesEvent());
	}
	
	public static synchronized void removeDownloadedMagazine(Context context, Magazine magazine) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        SQLiteDatabase db = DataBaseHelper.getInstance(context).getWritableDatabase();

        // cancel any download and notification for this magazine
        Cursor c = db.query(DataBaseHelper.TABLE_DOWNLOADED_MAGAZINES, new String[] {DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID}, 
                DataBaseHelper.FIELD_FILE_NAME + "=?", new String[] {magazine.getFileName()}, null, null, null);
        while (c.moveToNext()) {
            int downloadManagerID = c.getInt(c.getColumnIndex(DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID));
            removeNotification(context, downloadManagerID);
            dm.remove(downloadManagerID);
        }
        c.close();

        // cancel any asset downloads for this magazine
       c = db.query(DataBaseHelper.TABLE_ASSETS, new String[] {DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID},
               DataBaseHelper.FIELD_FILE_NAME + "=?", new String[] {magazine.getFileName()}, null, null, null);
        while (c.moveToNext()) {
            int downloadManagerID = c.getInt(c.getColumnIndex(DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID));
//            removeNotification(downloadManagerID);
            dm.remove(downloadManagerID);
        }
        c.close();

        db.delete(DataBaseHelper.TABLE_DOWNLOADED_MAGAZINES, DataBaseHelper.FIELD_FILE_NAME + "=?", new String[] {magazine.getFileName()});
        db.delete(DataBaseHelper.TABLE_ASSETS, DataBaseHelper.FIELD_FILE_NAME + "=?", new String[] {magazine.getFileName()});

        EventBus.getDefault().post(new ChangeInDownloadedMagazinesEvent());
	}

    public static void removeNotification(Context context, int notificationId) {
        // Clear downloaded notification for magazine if visible
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(notificationId);
    }

	public int getCount(String tableName) {
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getReadableDatabase();
		int count = (int) DatabaseUtils.longForQuery(db, "select COUNT(" + DataBaseHelper.FIELD_ID + ") from " + tableName, null);
		return count;
	}

	public synchronized void cleanMagazines(String tableName){
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getWritableDatabase();
		db.execSQL("DELETE FROM " + tableName + " WHERE 1");
		Log.d(TAG, "at cleanMagazinesListInBase: " + tableName + " table was clean");
	}

	/**
	 * Look up magazine by path
	 * @param path
	 * @return
	 */
	public Magazine findByFileName(String path, String tableName) {
		Magazine magazine = null;
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getReadableDatabase();
		Cursor cursor = db.query(tableName, null, DataBaseHelper.FIELD_FILE_NAME + "=?", new String[]{path}, null, null, null);
		if (cursor.moveToFirst()) {
			magazine = new Magazine(cursor, getContext());
		}

		cursor.close();
		return magazine;
	}

    /**
     * Look up magazine by DownloadManager ID
     * @param downloadManagerID
     * @return
     */
    public Magazine findByDownloadManagerID(long downloadManagerID, String tableName) {
        Magazine magazine = null;
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getReadableDatabase();
        Cursor cursor = db.query(tableName, null, DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID + "=?",
                new String[] {String.valueOf(downloadManagerID)}, null, null, null);
        if (cursor.moveToFirst()) {
            magazine = new Magazine(cursor, getContext());
        }

        cursor.close();
        return magazine;
    }

    public synchronized void setAssetDownloaded(long downloadManagerID) {
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DataBaseHelper.FIELD_ASSET_IS_DOWNLOADED, true);
        db.update(DataBaseHelper.TABLE_ASSETS, cv, DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID + "=?",
                new String[] {String.valueOf(downloadManagerID)});
    }

    public String getAssetFilename(long downloadManagerID) {
        String assetFilename = null;
        SQLiteDatabase db = DataBaseHelper.getInstance(getContext()).getReadableDatabase();
        Cursor cursor = db.query(DataBaseHelper.TABLE_ASSETS, null, DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID + "=?",
                new String[] {String.valueOf(downloadManagerID)}, null, null, null);
        if (cursor.moveToFirst()) {
            assetFilename = cursor.getString(cursor.getColumnIndex(DataBaseHelper.FIELD_ASSET_FILE_NAME));
        }
        cursor.close();
        return assetFilename;
    }

    public static void updateMagazineDetails(Context context, Magazine magazine) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        SQLiteDatabase db = DataBaseHelper.getInstance(context).getReadableDatabase();
        Cursor c = db.query(DataBaseHelper.TABLE_DOWNLOADED_MAGAZINES, new String[]{DataBaseHelper.FIELD_IS_SAMPLE,
                DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID}, DataBaseHelper.FIELD_FILE_NAME + "=?", new String[]{magazine.getFileName()},
                null, null, null);
        while (c.moveToNext()) {
            magazine.setSample(c.getInt(c.getColumnIndex(DataBaseHelper.FIELD_IS_SAMPLE)) == 0 ? false : true);
            magazine.setDownloadManagerId(c.getLong(c.getColumnIndex(DataBaseHelper.FIELD_DOWNLOAD_MANAGER_ID)));
            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(magazine.getDownloadManagerId());
            Cursor cursor = downloadManager.query(q);
            if (cursor.moveToFirst()) {
                magazine.setDownloadStatus(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
                long fileSize = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long bytesDL = cursor.getLong(cursor.getColumnIndex(DownloadManager
                        .COLUMN_BYTES_DOWNLOADED_SO_FAR));
                magazine.setDownloadProgress((int) ((bytesDL * 100.0f) / fileSize));
            } else {
                magazine.setDownloadProgress(0);
                magazine.setDownloadStatus(-1);
            }
            cursor.close();
            magazine.setTotalAssetCount(getTotalAssetCount(context, magazine));
            magazine.setDownloadedAssetCount(getDownloadedAssetCount(context, magazine));
        }

    	EventBus.getDefault().post(new ChangeInDownloadedMagazinesEvent());
    }

    public static int getTotalAssetCount(Context context, Magazine magazine) {
        SQLiteDatabase db = DataBaseHelper.getInstance(context).getReadableDatabase();
        int count = (int) DatabaseUtils.longForQuery(db, "select COUNT(" + DataBaseHelper.FIELD_ID + ") from " + DataBaseHelper
                .TABLE_ASSETS + " WHERE " + DataBaseHelper.FIELD_FILE_NAME + "=?",
                new String[]{magazine.getFileName()});
        return count;
    }

    public static int getDownloadedAssetCount(Context context, Magazine magazine) {
        SQLiteDatabase db = DataBaseHelper.getInstance(context).getReadableDatabase();
        int count = (int) DatabaseUtils.longForQuery(db, "select COUNT(" + DataBaseHelper.FIELD_ID + ") from " + DataBaseHelper
                .TABLE_ASSETS + " WHERE " + DataBaseHelper.FIELD_FILE_NAME + "=? AND " + DataBaseHelper.FIELD_ASSET_IS_DOWNLOADED
                + "='1'",
                new String[]{magazine.getFileName()});
        return count;
    }

}
