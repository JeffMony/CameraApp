package com.jeffmony.cameraapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.cameraapp.model.ViewSize;
import com.jeffmony.cameraapp.utils.ImageUtils;
import com.jeffmony.cameraapp.utils.LogUtils;
import com.jeffmony.cameraapp.utils.MediaUtils;
import com.jeffmony.cameraapp.view.Camera2Preview;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.M)
public class Camera2Activity extends AppCompatActivity implements View.OnClickListener, Handler.Callback {

    private static final String TAG = "Camera2Activity";

    //view对象
    private Camera2Preview mCamera2Preview;
    private Button mFocusBtn;
    private Button mCaptureBtn;
    private Button mSwitchBtn;
    private Button mRecorderBtn;
    private Button mStopRecorderBtn;

    //消息回调的处理逻辑
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    //Camera2相关的属性
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private String mCurrentCameraId = null;
    private String mFrontCameraId = null;
    private String mBackCameraId = null;
    private CameraCharacteristics mCurrentCameraCharacteristics = null;
    private CameraCharacteristics mBackCameraCharacteristics = null;
    private CameraCharacteristics mFrontCameraCharacteristics = null;

    private SurfaceTexture mPreviewSurfaceTexture;
    private Surface mPreviewSurface;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private ImageReader mPreviewDataImageReader = null;
    private Surface mPreviewDataSurface = null;
    private ImageReader mJpegImageReader = null;
    private Surface mJpegSurface = null;
    private CameraCaptureSession mCameraCaptureSession = null;
    private int mOrientation = 0;

    private CaptureRequest.Builder mPreviewImageRequestBuilder = null;
    private CaptureRequest.Builder mCaptureImageRequestBuilder = null;
    private CaptureRequest.Builder mCaptureVideoRequestBuilder = null;

    //录制视频相关的变量
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecording;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    private void startCameraThread() {
        mCameraThread = new HandlerThread("Camera2Thread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper(), this);
    }

    private static final int MSG_OPEN_CAMERA = 1;
    private static final int MSG_CLOSE_CAMERA = 2;
    private static final int MSG_CREATE_REQUEST_BUILDER = 3;
    private static final int MSG_SET_PREVIEW_SIZE = 4;
    private static final int MSG_SET_PICTURE_SIZE = 5;
    private static final int MSG_SET_VIDEO_SIZE = 6;
    private static final int MSG_CREATE_SESSION = 7;
    private static final int MSG_START_PREVIEW = 8;
    private static final int MSG_STOP_PREVIEW = 9;
    private static final int MSG_FOCUS_CAMERA = 10;
    private static final int MSG_SWITCH_CAMERA = 11;
    private static final int MSG_CAPTURE_IMAGE = 12;
    private static final int MSG_RECORD_VIDEO = 13;
    private static final int MSG_STOP_RECORD_VIDEO = 14;

    //Camera2中各项功能的消息处理
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean handleMessage(@NonNull @NotNull Message msg) {
        if (msg.what == MSG_OPEN_CAMERA) {
            String cameraId = (String)msg.obj;
            openCameraInternal(cameraId);
        } else if (msg.what == MSG_CLOSE_CAMERA) {
            closeCameraInternal();
        } else if (msg.what == MSG_CREATE_REQUEST_BUILDER) {
            createCaptureRequestBuilderInternal();
        } else if (msg.what == MSG_SET_PREVIEW_SIZE) {
            ViewSize size = (ViewSize)msg.obj;
            setPreviewSizeInternal(size);
        } else if (msg.what == MSG_SET_PICTURE_SIZE) {
            ViewSize size = (ViewSize)msg.obj;
            setPictureSizeInternal(size);
        } else if (msg.what == MSG_SET_VIDEO_SIZE) {
            ViewSize size = (ViewSize)msg.obj;
            setVideoSizeInternal(size);
        } else if (msg.what == MSG_CREATE_SESSION) {
            createSessionInternal();
        } else if (msg.what == MSG_START_PREVIEW) {
            startPreviewInternal();
        } else if (msg.what == MSG_STOP_PREVIEW) {
            stopPreviewInternal();
        } else if (msg.what == MSG_FOCUS_CAMERA) {
            focusCameraInternal();
        } else if (msg.what == MSG_SWITCH_CAMERA) {
            switchCameraInternal();
        } else if (msg.what == MSG_CAPTURE_IMAGE) {
            captureImageInternal();
        } else if (msg.what == MSG_RECORD_VIDEO) {
            recordVideoInternal();
        } else if (msg.what == MSG_STOP_RECORD_VIDEO) {
            stopRecordVideoInternal();
        }
        return false;
    }

    @Override
    protected void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        startCameraThread();

        mCameraManager = getSystemService(CameraManager.class);

        initCamera2Info();

        mCamera2Preview = findViewById(R.id.camera2_preview);

        mCamera2Preview.setSurfaceTextureListener(mPreviewSurfaceTextureListener);

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

    }

    @Override
    protected void onResume() {
        super.onResume();

        LogUtils.i(TAG, "onResume");

        //默认打开
        openCamera(mBackCameraId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
        if (mPreviewDataImageReader != null) {
            mPreviewDataImageReader.close();
        }
        if (mJpegImageReader != null) {
            mJpegImageReader.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraThread.quitSafely();
        mCameraThread = null;
        mCameraHandler = null;
    }

    private void initCamera2Info() {
        String[] ids = null;
        try {
            ids = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            LogUtils.w(TAG, "GetCameraIdList failed, exception = " + e.getMessage());
        }
        if (ids != null) {
            for (String id : ids) {
                CameraCharacteristics cameraCharacteristics = null;

                try {
                    cameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
                } catch (Exception e) {
                    LogUtils.w(TAG, "CameraManager getCameraCharacteristics failed, exception = " + e.getMessage());
                }
                if (cameraCharacteristics != null) {
                    if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                        mBackCameraId = id;
                        mBackCameraCharacteristics = cameraCharacteristics;
                    } else if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                        mFrontCameraId = id;
                        mFrontCameraCharacteristics = cameraCharacteristics;
                    }
                }
            }
        }
    }

    private int getDisplayCameraOrientation(String cameraId, CameraCharacteristics cameraCharacteristics) {
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
        int result = 0;
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (cameraId.equals(mBackCameraId)) {
            result = (sensorOrientation -degrees + 360) % 360;
        } else if (cameraId.equals(mFrontCameraId)) {
            result = (sensorOrientation + degrees) % 360;
            result = (360 - result) % 360;
        } else { }
        return result;
    }

    private void openCamera(String cameraId) {
        mCameraHandler.obtainMessage(MSG_OPEN_CAMERA, cameraId).sendToTarget();
    }

    private void openCameraInternal(String cameraId) {
        try {
            mCameraManager.openCamera(cameraId, mCameraStateCallback, mMainHandler);
        } catch (Exception e) {
            LogUtils.w(TAG, "OpenCamera failed, exception = " + e.getMessage());
        }
    }

    private void closeCamera() {
        mCameraHandler.obtainMessage(MSG_CLOSE_CAMERA).sendToTarget();
    }

    private void closeCameraInternal() {
        LogUtils.i(TAG, "Close camera device");
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mCurrentCameraCharacteristics = null;
    }

    private void createCaptureRequestBuilder() {
        mCameraHandler.obtainMessage(MSG_CREATE_REQUEST_BUILDER).sendToTarget();
    }

    private void createCaptureRequestBuilderInternal() {
        CameraDevice cameraDevice = mCameraDevice;
        if (cameraDevice != null) {
            try {
                mPreviewImageRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mCaptureImageRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            } catch (Exception e) {
                LogUtils.w(TAG, "CreateCaptureRequest failed, exception = " + e.getMessage());
            }
        }
    }

    private void createSession() {
        mCameraHandler.obtainMessage(MSG_CREATE_SESSION).sendToTarget();
    }

    private void createSessionInternal() {
        Surface previewSuface = mPreviewSurface;
        Surface previewDataSurface = mPreviewDataSurface;
        Surface jpegSurface = mJpegSurface;

        List<Surface> outputSurfaces = new ArrayList<>();

        if (previewSuface != null) {
            outputSurfaces.add(previewSuface);
        }

        if (previewDataSurface != null) {
            outputSurfaces.add(previewDataSurface);
        }

        if (jpegSurface != null) {
            outputSurfaces.add(jpegSurface);
        }

        CameraDevice cameraDevice = mCameraDevice;
        if (cameraDevice != null) {
            try {
                cameraDevice.createCaptureSession(outputSurfaces, mSessionStateCallback, mMainHandler);
            } catch (Exception e) {
                LogUtils.w(TAG, "Camera2 createCaptureSession failed, exception = " + e.getMessage());
            }
        }
    }

    private void startPreview() {
        mCameraHandler.obtainMessage(MSG_START_PREVIEW).sendToTarget();
    }

    private void startPreviewInternal() {
        CameraDevice cameraDevice = mCameraDevice;
        CameraCaptureSession cameraCaptureSession = mCameraCaptureSession;
        CaptureRequest.Builder previewImageRequestBuilder = mPreviewImageRequestBuilder;
        CaptureRequest.Builder captureImageRequestBuilder = mCaptureImageRequestBuilder;
        if (cameraDevice != null && cameraCaptureSession != null) {
            Surface previewSurface = mPreviewSurface;
            Surface previewDataSurface = mPreviewDataSurface;
            if (previewSurface != null) {
                previewImageRequestBuilder.addTarget(previewSurface);
                captureImageRequestBuilder.addTarget(previewSurface);
            }
            if (previewDataSurface != null) {
                previewImageRequestBuilder.addTarget(previewDataSurface);
                captureImageRequestBuilder.addTarget(previewDataSurface);
            }
            CaptureRequest previewRequest = previewImageRequestBuilder.build();

            try {
                cameraCaptureSession.setRepeatingRequest(previewRequest, mRepeatingCaptureStateCallback, mMainHandler);
            } catch (Exception e) {
                LogUtils.w(TAG, "CameraCaptureSession setRepeatingRequest failed, exception = " + e.getMessage());
            }
        }

    }

    private void stopPreview() {
        mCameraHandler.obtainMessage(MSG_STOP_PREVIEW).sendToTarget();
    }

    private void stopPreviewInternal() {
        CameraCaptureSession cameraCaptureSession = mCameraCaptureSession;
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (Exception e) {
                LogUtils.w(TAG, "StopPreview failed, exception = " + e.getMessage());
            }
        }
    }

    private CameraCharacteristics getCameraCharacteristics(String cameraId) {
        if (cameraId.equals(mFrontCameraId)) {
            return mFrontCameraCharacteristics;
        } else if (cameraId.equals(mBackCameraId)) {
            return mBackCameraCharacteristics;
        } else {
            throw new RuntimeException("No available camera");
        }
    }

    private void setPreviewSize(int width, int height) {
        ViewSize size = new ViewSize(width, height);
        mCameraHandler.obtainMessage(MSG_SET_PREVIEW_SIZE, size).sendToTarget();
    }

    private void setPreviewSizeInternal(ViewSize size) {
        if (mCurrentCameraCharacteristics != null && size.getWidth() != 0 && size.getHeight() != 0) {
            StreamConfigurationMap streamConfigurationMap = mCurrentCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
            float ratio = size.getHeight() * 1.0f / size.getWidth();
            for (Size previewSize : sizes) {
                LogUtils.i(TAG, "previewSize width = " + previewSize.getWidth() + ", height = " + previewSize.getHeight());
                float previewRatio = previewSize.getWidth() * 1.0f / previewSize.getHeight();
                if (Math.abs(previewRatio - ratio) < 0.001f) {

                    SurfaceTexture surfaceTexture = mPreviewSurfaceTexture;
                    surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                    mPreviewSurface = new Surface(surfaceTexture);

                    mPreviewDataImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 3);
                    mPreviewDataImageReader.setOnImageAvailableListener(mOnPreviewDataAvailableListener, mCameraHandler);
                    mPreviewDataSurface = mPreviewDataImageReader.getSurface();
                    break;
                }
            }
        }
    }

    private void setPictureSize(int width, int height) {
        ViewSize size = new ViewSize(width, height);
        mCameraHandler.obtainMessage(MSG_SET_PICTURE_SIZE, size).sendToTarget();
    }

    private void setPictureSizeInternal(ViewSize size) {
        if (mCurrentCameraCharacteristics != null && size.getWidth() != 0 && size.getHeight() != 0) {
            StreamConfigurationMap streamConfigurationMap = mCurrentCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageReader.class);
            float ratio = size.getHeight() * 1.0f / size.getWidth();
            for (Size pictureSize : sizes) {
                LogUtils.i(TAG, "pictureSize width = " + pictureSize.getWidth() + ", height = " + pictureSize.getHeight());
                float pictureRatio = pictureSize.getWidth() * 1.0f / pictureSize.getHeight();
                if (Math.abs(pictureRatio - ratio) < 0.001f) {
                    mJpegImageReader = ImageReader.newInstance(pictureSize.getWidth(), pictureSize.getHeight(), ImageFormat.JPEG, 5);
                    mJpegImageReader.setOnImageAvailableListener(mOnJpegImageAvailableListener, mCameraHandler);
                    mJpegSurface = mJpegImageReader.getSurface();
                    break;
                }
            }
        }
    }

    private void setVideoSize(int width, int height) {
        ViewSize size = new ViewSize(width, height);
        mCameraHandler.obtainMessage(MSG_SET_VIDEO_SIZE, size).sendToTarget();
    }

    private void setVideoSizeInternal(ViewSize size) {
        if (mCurrentCameraCharacteristics != null && size.getWidth() != 0 && size.getHeight() != 0) {
            StreamConfigurationMap streamConfigurationMap = mCurrentCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);
            float ratio = size.getHeight() * 1.0f / size.getWidth();
            for (Size videoSize : sizes) {
                LogUtils.i(TAG, "videoSize width = " + videoSize.getWidth() + ", height = " + videoSize.getHeight());
                float videoRatio = videoSize.getWidth() * 1.0f / videoSize.getHeight();
                if (Math.abs(videoRatio - ratio) < 0.001f) {
                    mVideoWidth = videoSize.getWidth();
                    mVideoHeight = videoSize.getHeight();
                    break;
                }
            }
        }
    }

    private void focusCamera() {
        mCameraHandler.obtainMessage(MSG_FOCUS_CAMERA).sendToTarget();
    }

    private void focusCameraInternal() {
        CameraCaptureSession cameraCaptureSession = mCameraCaptureSession;
        if (cameraCaptureSession != null) {
            CaptureRequest.Builder previewCaptureRequestBuilder = mPreviewImageRequestBuilder;
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_AUTO);
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            try {
                cameraCaptureSession.setRepeatingRequest(previewCaptureRequestBuilder.build(), null, mMainHandler);
            } catch (Exception e) {
                LogUtils.w(TAG, "Camera setRepeatingRequest failed, exception = " + e.getMessage());
            }

            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
            try {
                cameraCaptureSession.capture(previewCaptureRequestBuilder.build(), mFocusCaptureCallback, mMainHandler);
            } catch (Exception e) {
                LogUtils.w(TAG, "Camera capture failed, exception = " + e.getMessage());
            }
        }
    }

    private void switchCamera() {
        mCameraHandler.obtainMessage(MSG_SWITCH_CAMERA).sendToTarget();
    }

    private void switchCameraInternal() {
        CameraDevice cameraDevice = mCameraDevice;
        if (cameraDevice != null) {
            String newCameraId = getSwitchCameraId(cameraDevice.getId());
            if (newCameraId != null) {
                closeCamera();
                openCamera(newCameraId);
            }
        }
    }

    private String getSwitchCameraId(String cameraId) {
        if (cameraId.equals(mFrontCameraId)) {
            return mBackCameraId;
        } else if (cameraId.equals(mBackCameraId)) {
            return mFrontCameraId;
        } else {
            throw new RuntimeException("No available camera");
        }
    }

    private void captureImage() {
        mCameraHandler.obtainMessage(MSG_CAPTURE_IMAGE).sendToTarget();
    }

    private void captureImageInternal() {
        CameraCharacteristics cameraCharacteristics = mCurrentCameraCharacteristics;
        CameraCaptureSession cameraCaptureSession = mCameraCaptureSession;
        Surface jpegSurface = mJpegSurface;
        CaptureRequest.Builder captureImageRequestBuilder = mCaptureImageRequestBuilder;
        if (cameraCharacteristics != null && cameraCaptureSession != null && jpegSurface != null && captureImageRequestBuilder != null) {
            captureImageRequestBuilder.addTarget(jpegSurface);
            CaptureRequest captureImageRequest = captureImageRequestBuilder.build();

            try {
                cameraCaptureSession.capture(captureImageRequest, mCaptureImageStateCallback, mMainHandler);
            } catch (Exception e) {
                LogUtils.w(TAG, "Capture image failed, exception = " + e.getMessage());
            }
        }
    }

    private void recordVideo() {
        if (!mIsRecording) {
            mCameraHandler.obtainMessage(MSG_RECORD_VIDEO).sendToTarget();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void recordVideoInternal() {
        CameraDevice cameraDevice = mCameraDevice;
        if (prepareVideoRecorder() && cameraDevice != null) {
            //说明可以创建MediaRecorder
            stopPreview();

            try {
                mCaptureVideoRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

                List<Surface> outputSurfaces = new ArrayList<>();

                Surface previewSurface = mPreviewSurface;
                mCaptureVideoRequestBuilder.addTarget(previewSurface);
                outputSurfaces.add(previewSurface);

                Surface recordSurface = mMediaRecorder.getSurface();
                if (recordSurface != null) {
                    mCaptureVideoRequestBuilder.addTarget(recordSurface);
                    outputSurfaces.add(recordSurface);
                }

                cameraDevice.createCaptureSession(outputSurfaces, mCaptureVideoStateCallback, mMainHandler);

            } catch (Exception e) {
                LogUtils.w(TAG, "Record video failed, exception = " + e.getMessage());
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        //3.设置视频的质量,但是这样设置不够精细化，如果想设置的更加细致一些，可以拆开来设置
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);

        mMediaRecorder.setOutputFile(MediaUtils.getOutputMediaFile(this.getApplicationContext(), MediaUtils.MEDIA_TYPE_VIDEO));

        //6.设置旋转的角度
        if (mCurrentCameraId.equals(mBackCameraId)) {
            mMediaRecorder.setOrientationHint(mOrientation);
        } else if (mCurrentCameraId.equals(mFrontCameraId)) {
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
        }
    }

    private void stopRecordVideo() {
        if (mIsRecording) {
            mCameraHandler.obtainMessage(MSG_STOP_RECORD_VIDEO).sendToTarget();
        }
    }

    private void stopRecordVideoInternal() {
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            releaseMediaRecorder();
            mMediaRecorder = null;
            mIsRecording = false;
        }

        stopPreview();

        createSession();

    }

    private TextureView.SurfaceTextureListener mPreviewSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mPreviewSurfaceTexture = surface;
            mPreviewWidth = width;
            mPreviewHeight = height;
            LogUtils.i(TAG, "onSurfaceTextureAvailable width = " + width+", height="+height);
            //SurfaceTexture大小已经生成，可以开始设置预览界面的大小了
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            LogUtils.i(TAG, "Camera onOpened");
            mCameraDevice = camera;
            mCurrentCameraId = camera.getId();
            mCurrentCameraCharacteristics = getCameraCharacteristics(camera.getId());

            mOrientation = getDisplayCameraOrientation(camera.getId(), mCurrentCameraCharacteristics);

            /**
             * 这儿有严格的流程：
             * 1.只有当前的camera先打开了
             * 2.才能设置preview相关的属性
             * 3.才能设置预览和拍照的属性
             * 4.才能创建session流程
             * 5.session configured之后才能开始预览流程
             */
            setPreviewSize(mPreviewWidth, mPreviewHeight);
            setPictureSize(mPreviewWidth, mPreviewHeight);
            setVideoSize(mPreviewWidth, mPreviewHeight);

            createCaptureRequestBuilder();
            createSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraDevice = camera;
            closeCamera();
        }
    };

    //预览的ImageReader监听
    private ImageReader.OnImageAvailableListener mOnPreviewDataAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                Image.Plane yPlane = planes[0];
                Image.Plane uPlane = planes[1];
                Image.Plane vPlane = planes[2];
                ByteBuffer yBuffer = yPlane.getBuffer();
                ByteBuffer uBuffer = uPlane.getBuffer();
                ByteBuffer vBuffer = vPlane.getBuffer();

                image.close();
            }
            //可以定制化修改
        }
    };

    //拍照的ImageReader监听
    private ImageReader.OnImageAvailableListener mOnJpegImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer jpegByteBuffer = planes[0].getBuffer();
                byte[] jpegByteArray = new byte[jpegByteBuffer.remaining()];
                jpegByteBuffer.get(jpegByteArray);
                generatePictureFile(jpegByteArray);

                image.close();
            }
        }
    };

    private void generatePictureFile(byte[] data) {
        File pictureFile = MediaUtils.getOutputMediaFile(this.getApplicationContext(), MediaUtils.MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            LogUtils.w(TAG, "Create picture file failed, check storage permission");
            return;
        }

        LogUtils.i(TAG, "generatePictureFile filePath = " + pictureFile.getAbsolutePath());

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap != null) {

            if (mCurrentCameraId.equals(mBackCameraId)) {
                bitmap = ImageUtils.getRotatedBitmap(bitmap, mOrientation);
            } else if (mCurrentCameraId.equals(mFrontCameraId)) {
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
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraCaptureSession = session;

            startPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            mCameraCaptureSession = session;
        }
    };

    private CameraCaptureSession.CaptureCallback mRepeatingCaptureStateCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    private CameraCaptureSession.CaptureCallback mFocusCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureImageStateCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

            //可以定制快门的声音
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            Toast.makeText(Camera2Activity.this, "拍照完成", Toast.LENGTH_SHORT).show();
        }
    };

    private CameraCaptureSession.StateCallback mCaptureVideoStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraCaptureSession = session;
            //重新开始预览
            try {
                mCameraCaptureSession.setRepeatingRequest(mCaptureVideoRequestBuilder.build(), null, mMainHandler);
            } catch (Exception e) {
                LogUtils.w(TAG, "CaptureVideo setRepeatingRequest failed, exception = " + e.getMessage());
            }

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    //开始录制视频
                    mMediaRecorder.start();
                    mIsRecording = true;
                }
            });
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            mCameraCaptureSession = session;
        }
    };

    @Override
    public void onClick(View v) {
        if (v == mFocusBtn) {
            focusCamera();
        } else if (v == mCaptureBtn) {
            captureImage();
        } else if (v == mSwitchBtn) {
            switchCamera();
        } else if (v == mRecorderBtn) {
            recordVideo();
        } else if (v == mStopRecorderBtn) {
            stopRecordVideo();
        }
    }
}
