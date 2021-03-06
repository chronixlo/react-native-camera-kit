package com.wix.RNCameraKit.camera;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.support.annotation.IntRange;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.wix.RNCameraKit.Utils;

import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wix.RNCameraKit.camera.Orientation.getSupportedRotation;

@SuppressWarnings("MagicNumber deprecation") // We're still using Camera API 1, everything is deprecated
public class CameraViewManager extends SimpleViewManager<CameraView> {

    private static Camera camera = null;
    private static int currentCamera = 0;
    private static String flashMode = Camera.Parameters.FLASH_MODE_AUTO;
    private static Stack<CameraView> cameraViews = new Stack<>();
    private static ThemedReactContext reactContext;
    private static OrientationEventListener orientationListener;
    private static int currentRotation = 0;
    private static AtomicBoolean cameraReleased = new AtomicBoolean(false);

    public static Camera getCamera() {
        return camera;
    }

    @Override
    public String getName() {
        return "CameraView";
    }

    @Override
    protected CameraView createViewInstance(ThemedReactContext reactContext) {
        CameraViewManager.reactContext = reactContext;
        return new CameraView(reactContext);
    }

    static void setCameraView(CameraView cameraView) {
        if(!cameraViews.isEmpty() && cameraViews.peek() == cameraView) return;
        CameraViewManager.cameraViews.push(cameraView);
        connectHolder();
    }

    static boolean setFlashMode(String mode) {
        if (camera == null) {
            return false;
        }
        Camera.Parameters parameters = camera.getParameters();
        List supportedModes = parameters.getSupportedFlashModes();
        if (supportedModes != null && supportedModes.contains(mode)) {
            flashMode = mode;
            parameters.setFlashMode(flashMode);
            camera.setParameters(parameters);
            camera.startPreview();
            return true;
        } else {
            return false;
        }
    }

    static boolean changeCamera() {
        if (Camera.getNumberOfCameras() == 1) {
            return false;
        }
        currentCamera++;
        currentCamera = currentCamera % Camera.getNumberOfCameras();
        initCamera();
        connectHolder();

        return true;
    }

    private static void initCamera() {
        if (camera != null) {
            releaseCamera();
        }
        try {
            camera = Camera.open(currentCamera);
            updateCameraSize();
            cameraReleased.set(false);
            setCameraProps();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
    
    private static void releaseCamera() {
        cameraReleased.set(true);
        camera.release();
    }

    private static void connectHolder() {
        if (cameraViews.isEmpty()  || cameraViews.peek().getHolder() == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(camera == null) {
                    initCamera();
                }

                if(cameraViews.isEmpty()) {
                    return;
                }

                cameraViews.peek().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            camera.stopPreview();
                            camera.setPreviewDisplay(cameraViews.peek().getHolder());
                            camera.startPreview();
                        } catch (IOException | RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }).start();
    }

    static void removeCameraView() {
        if(!cameraViews.isEmpty()) {
            cameraViews.pop();
        }
        if(!cameraViews.isEmpty()) {
            connectHolder();
        } else if(camera != null){
            releaseCamera();
            camera = null;
        }
        if (cameraViews.isEmpty()) {
            clearOrientationListener();
        }
    }

    private static void clearOrientationListener() {
        if (orientationListener != null) {
            orientationListener.disable();
            orientationListener = null;
        }
    }

    private static void setCameraProps() {
        if (camera == null) return;
        if (cameraReleased.get()) return;

        int deviceOrientation = Orientation.getDeviceOrientation(reactContext.getCurrentActivity());

        Camera.Parameters parameters = camera.getParameters();
        parameters.setRotation(deviceOrientation);
        parameters.setPictureFormat(PixelFormat.JPEG);
        camera.setDisplayOrientation(deviceOrientation);
        
        // focus
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        camera.setParameters(parameters);
    }

    public static Camera.CameraInfo getCameraInfo() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(currentCamera, info);
        return info;
    }

    private static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int maxWidth, int maxHeight) {
        Camera.Size bestSize = null;
        for (Camera.Size size : sizes) {
            if (size.width == maxWidth && size.height == maxHeight) {
                bestSize = size;
                break;
            }

            if (size.width > maxWidth || size.height > maxHeight) {
                continue;
            }

            if (bestSize == null) {
                bestSize = size;
                continue;
            }

            int resultArea = bestSize.width * bestSize.height;
            int newArea = size.width * size.height;

            if (newArea > resultArea) {
                bestSize = size;
            }
        }

        return bestSize;
    }

    private static void updateCameraSize() {
        try {
            Camera camera = CameraViewManager.getCamera();

            WindowManager wm = (WindowManager) reactContext.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getRealSize(size);
            size.y = Utils.convertDeviceHeightToSupportedAspectRatio(size.x, size.y);
            if (camera == null) return;
            List<Camera.Size> supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
            List<Camera.Size> supportedPictureSizes = camera.getParameters().getSupportedPictureSizes();
            // swap x&y cuz portrait
            Camera.Size optimalSize = getOptimalPreviewSize(supportedPreviewSizes, size.y, size.x);
            Camera.Size optimalPictureSize = getOptimalPreviewSize(supportedPictureSizes, 1920, 1080);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            parameters.setPictureSize(optimalPictureSize.width, optimalPictureSize.height);
            parameters.setFlashMode(flashMode);
            camera.setParameters(parameters);
        } catch (RuntimeException ignored) {}
    }

    public static void reconnect() {
        connectHolder();
    }
}
