package com.jeffmony.cameraapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.cameraapp.utils.ImageUtils;
import com.jeffmony.cameraapp.utils.LogUtils;
import com.jeffmony.cameraapp.utils.MediaUtils;
import com.jeffmony.cameraapp.view.Camera1Preview;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class Camera1Activity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Camera1Activity";

    private Camera1Preview mCameraPreview;
    private Button mFocusBtn;
    private Button mCaptureBtn;
    private Button mSwitchBtn;
    private Button mRecorderBtn;
    private Button mStopRecorderBtn;

    private Camera mCamera;
    private SurfaceHolder mPreviewSurfaceHolder;
    private int mPreviewSurfaceWidth;
    private int mPreviewSurfaceHeight;
    private int mCurrentCameraId = -1;
    private Camera.CameraInfo mCurrentCameraInfo = null;
    private int mOrientation = 0;

    //Camera相关的属性
    private int mCameraCount;
    private int mBackCameraId = -1;
    private int mFrontCameraId = -1;
    private Camera.CameraInfo mBackCameraInfo = null;
    private Camera.CameraInfo mFrontCameraInfo = null;

    //录制视频相关的变量
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecording;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;


    @Override
    protected void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera1);

        mCameraPreview = findViewById(R.id.camera1_preview);
        mFocusBtn = findViewById(R.id.focus_btn);
        mCaptureBtn = findViewById(R.id.capture_btn);
        mSwitchBtn = findViewById(R.id.switch_btn);
        mRecorderBtn = findViewById(R.id.recorder_btn);
        mStopRecorderBtn = findViewById(R.id.stop_recorder_btn);

        mFocusBtn.setOnClickListener(this);
        mCaptureBtn.setOnClickListener(this);
        mSwitchBtn.setOnClickListener(this);
        mRecorderBtn.setOnClickListener(this);
        mStopRecorderBtn.setOnClickListener(this);

        initCameraInfo();
        mCameraPreview.getHolder().addCallback(new PreviewSurfaceCallback());
    }

    private void initCameraInfo() {
        mCameraCount = Camera.getNumberOfCameras();
        if (mCameraCount < 1) {
            Toast.makeText(this, "没有找到摄像头", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int id = 0; id < mCameraCount; id++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(id, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mBackCameraId = id;
                mBackCameraInfo = cameraInfo;
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mFrontCameraId = id;
                mFrontCameraInfo = cameraInfo;
            }
        }

        LogUtils.i(TAG, "initCameraInfo CameraCount="+mCameraCount+", hasBackCamera="+hasBackCamera()
                +", hasFrontCamera="+hasFrontCamera());

    }

    private boolean hasBackCamera() {
        return mBackCameraId != -1;
    }

    private boolean hasFrontCamera() {
        return mFrontCameraId != -1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.i(TAG, "onResume");
        openCamera(mBackCameraId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.i(TAG, "onPause");
        closeCamera();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.i(TAG, "onDestroy");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Window window = getWindow();
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void openCamera(int id) {
        mCamera = Camera.open(id);
        mCurrentCameraId = id;
        mCurrentCameraInfo = (id == mBackCameraId ? mBackCameraInfo : mFrontCameraInfo);
        mOrientation = getCameraDisplayOrientation(mCurrentCameraInfo);
        mCamera.setDisplayOrientation(mOrientation);
    }

    private int getCameraDisplayOrientation(Camera.CameraInfo cameraInfo) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        return result;

    }

    //honor显示是setPreviewSize width=720, height=960
    private void setPreviewSize(int width, int height) {
        LogUtils.i(TAG, "setPreviewSize width="+width+", height="+height);
        Camera camera = mCamera;
        if (camera != null && width != 0 && height != 0) {
            float ratio = width * 1.0f / height;
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            for (Camera.Size previewSize : supportedPreviewSizes) {
                LogUtils.i(TAG, "PreviewSize width = " + previewSize.width + ", height=" + previewSize.height);
                float previewSizeRatio = previewSize.height * 1.0f / previewSize.width;
                if (Math.abs(previewSizeRatio - ratio) < 0.001f) {
                    parameters.setPreviewSize(previewSize.width, previewSize.height);

                    parameters.setPreviewFormat(ImageFormat.NV21);
                    int frameWidth = previewSize.width;
                    int frameHeight = previewSize.height;
                    int previewFormat = parameters.getPreviewFormat();
                    PixelFormat pixelFormat = new PixelFormat();
                    PixelFormat.getPixelFormatInfo(previewFormat, pixelFormat);
                    int bufferSize = (frameWidth * frameHeight * pixelFormat.bitsPerPixel) / 8;
                    camera.addCallbackBuffer(new byte[bufferSize]);
                    camera.addCallbackBuffer(new byte[bufferSize]);
                    camera.addCallbackBuffer(new byte[bufferSize]);

                    camera.setParameters(parameters);
                    break;
                }
            }
        }

    }

    private void setPictureSize(int width, int height)  {
        Camera camera = mCamera;
        if (camera != null && width != 0 && height != 0) {
            float ratio = width * 1.0f / height;
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
            for (Camera.Size pictureSize : supportedPictureSizes) {
                float pictureSizeRatio = pictureSize.height * 1.0f / pictureSize.width;
                if (Math.abs(pictureSizeRatio - ratio) < 0.001f) {
                    //一般是从大到小排序的，所以最好取最大的值
                    parameters.setPictureSize(pictureSize.width, pictureSize.height);
                    camera.setParameters(parameters);
                    break;
                }
            }
        }
    }

    //设置视频录制的实际分辨率大小
    private void setVideoSize(int width, int height) {
        Camera camera = mCamera;
        if (camera != null && width != 0 && height != 0) {
            float ratio = width * 1.0f / height;
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supportedVideoSizes = parameters.getSupportedVideoSizes();
            for (Camera.Size videoSize : supportedVideoSizes) {
                float videoSizeRatio = videoSize.height * 1.0f / videoSize.width;
                if (Math.abs(videoSizeRatio - ratio) < 0.001f) {
                    mVideoWidth = videoSize.width;
                    mVideoHeight = videoSize.height;
                    break;
                }
            }
        }
    }

    private void setPreviewSurface(SurfaceHolder holder) {
        Camera camera = mCamera;
        if (camera != null && holder != null) {
            try {
                camera.setPreviewDisplay(holder);
            } catch (Exception e) {
                LogUtils.w(TAG, "Camera setPreviewDisplay failed, exception = " + e.getMessage());
            }
        }
    }

    private void startPreview() {
        Camera camera = mCamera;
        SurfaceHolder previewHolder = mPreviewSurfaceHolder;
        if (camera != null && previewHolder != null) {
            camera.setPreviewCallbackWithBuffer(new PreviewCallback());
            camera.startPreview();
        }
    }

    private void stopPreview() {
        Camera camera = mCamera;
        if (camera != null) {
            camera.stopPreview();;
        }
    }

    private void closeCamera() {
        Camera camera = mCamera;
        mCamera = null;
        if (camera != null) {
            camera.release();
            mCurrentCameraId = -1;
            mCurrentCameraInfo = null;;
        }
    }

    private class PreviewCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            camera.addCallbackBuffer(data);
        }
    }

    private class PreviewSurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mPreviewSurfaceHolder = holder;
            mPreviewSurfaceWidth = width;
            mPreviewSurfaceHeight = height;
            setPreviewSize(width, height);
            setPictureSize(width, height);
            setVideoSize(width, height);

            setPreviewSurface(holder);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            LogUtils.i(TAG, "surfaceDestroyed");
            mPreviewSurfaceHolder = null;
            mPreviewSurfaceWidth = 0;
            mPreviewSurfaceHeight = 0;
        }
    }

    private void focusCamera() {
        Camera camera = mCamera;
        if (camera != null) {
            camera.cancelAutoFocus();
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (success) {
                        Toast.makeText(Camera1Activity.this, "对焦成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(Camera1Activity.this, "对焦失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void capturePicture() {
        Camera camera = mCamera;
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.takePicture(new ShutterCallback(), new RawCallback(), new PostCallback(), new JpegCallback());
        }
    }

    //点击快门的回调
    private class ShutterCallback implements Camera.ShutterCallback {
        @Override
        public void onShutter() {
            LogUtils.i(TAG, "onShutter is invoke");
        }
    }

    //原始数据回调
    private class RawCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    }

    //后续处理数据回调
    private class PostCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    }

    //最终生成图片回调
    private class JpegCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            generatePictureFile(data);
        }
    }

    private void generatePictureFile(byte[] data) {
        File pictureFile = MediaUtils.getOutputMediaFile(this.getApplicationContext(), MediaUtils.MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            LogUtils.w(TAG, "Create picture file failed, check storage permission");
            return;
        }

        LogUtils.i(TAG, "generatePictureFile filePath = " + pictureFile.getAbsolutePath());

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap != null) {
            if (mCurrentCameraId == mBackCameraId) {
                bitmap = ImageUtils.getRotatedBitmap(bitmap, mOrientation);
            } else if (mCurrentCameraId == mFrontCameraId) {
                //这儿特别注意前置摄像头的出来的图片旋转角度是不同的
                bitmap = ImageUtils.getRotatedBitmap(bitmap, 360 - mOrientation);
            } else {
                throw new RuntimeException("Current camera id is inavailable");
            }

            FileOutputStream fos = null;
            BufferedOutputStream bos = null;
            try {
                fos = new FileOutputStream(pictureFile);
                bos = new BufferedOutputStream(fos);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                bos.flush();;
            } catch (Exception e) {
                LogUtils.w(TAG, "Write data failed, exception = " + e.getMessage());
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception e) {
                        LogUtils.w(TAG, "Close FileOutputStream failed, exception = " + e.getMessage());
                    }
                }
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (Exception e) {
                        LogUtils.w(TAG, "Close BufferedOutputStream failed, exception = " + e.getMessage());
                    }
                }
            }
        }

        //拍完照要重新设置预览界面的，不然一直卡在拍照的那一帧
        startPreview();
    }

    private void switchCamera() {
        SurfaceHolder previewHolder = mPreviewSurfaceHolder;
        if (previewHolder != null) {
            int cameraId = getSwitchCameraId();
            stopPreview();
            closeCamera();

            openCamera(cameraId);
            setPreviewSize(mPreviewSurfaceWidth, mPreviewSurfaceHeight);
            setPictureSize(mPreviewSurfaceWidth, mPreviewSurfaceHeight);
            setVideoSize(mPreviewSurfaceWidth, mPreviewSurfaceHeight);
            setPreviewSurface(previewHolder);
            startPreview();
        }

    }

    private int getSwitchCameraId() {
        if (mCurrentCameraId == mFrontCameraId && hasBackCamera()) {
            return mBackCameraId;
        } else if (mCurrentCameraId == mBackCameraId && hasFrontCamera()) {
            return mFrontCameraId;
        } else {
            throw new RuntimeException("No available camera to switch");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();

        //1.解锁camera
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        //2.设置camera音频和视频输出源
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        //3.设置视频的质量,但是这样设置不够精细化，如果想设置的更加细致一些，可以拆开来设置
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);

        //4.设置输出的路径
        mMediaRecorder.setOutputFile(MediaUtils.getOutputMediaFile(this.getApplicationContext(), MediaUtils.MEDIA_TYPE_VIDEO));

        //5.设置预览的界面
        mMediaRecorder.setPreviewDisplay(mPreviewSurfaceHolder.getSurface());

        //6.设置旋转的角度
        if (mCurrentCameraId == mBackCameraId) {
            mMediaRecorder.setOrientationHint(mOrientation);
        } else if (mCurrentCameraId == mFrontCameraId) {
            mMediaRecorder.setOrientationHint(360 - mOrientation);
        } else {
            throw new RuntimeException("No available camera");
        }

        try {
            mMediaRecorder.prepare();
        } catch (Exception e) {
            releaseMediaRecorder();
            LogUtils.w(TAG, "MediaRecorder prepare failed, exception = " + e.getMessage());
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startRecordVideo() {
        if (!mIsRecording) {
            if (prepareVideoRecorder()) {
                mMediaRecorder.start();
                mIsRecording = true;
            } else {
                releaseMediaRecorder();
            }
        }
    }

    private void stopRecordVideo() {
        if (mIsRecording) {
            mMediaRecorder.stop();
            releaseMediaRecorder();
            mCamera.lock();
            mIsRecording = false;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onClick(View v) {
        if (v == mFocusBtn) {
            focusCamera();
        } else if (v == mCaptureBtn) {
            capturePicture();
        } else if (v == mSwitchBtn) {
            switchCamera();
        } else if (v == mRecorderBtn) {
            startRecordVideo();
        } else if (v == mStopRecorderBtn) {
            stopRecordVideo();
        }
    }
}
