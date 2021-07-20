package com.jeffmony.cameraapp.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MediaUtils {
    private static final String TAG = "MediaUtils";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    public static Uri getOutputMediaFileUri(Context context, int type) {
        return Uri.fromFile(getOutputMediaFile(context, type));
    }

    public static File getOutputMediaFile(Context context, int type) {
        LogUtils.i(TAG, "StorageState = " + Environment.getExternalStorageState());
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return null;
        }
        File mediaStorageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                LogUtils.w(TAG, "Failed to create dir");
                return null;
            }
        }

        LogUtils.i(TAG, "Storage dir = " + mediaStorageDir.getAbsolutePath());

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getAbsolutePath() + File.separator + "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getAbsolutePath() + File.separator + "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }
        return mediaFile;
    }
}
