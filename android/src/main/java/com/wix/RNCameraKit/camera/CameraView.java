package com.wix.RNCameraKit.camera;

import android.graphics.Color;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.util.Log;

import com.facebook.react.uimanager.ThemedReactContext;
import com.wix.RNCameraKit.Utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class CameraView extends FrameLayout implements SurfaceHolder.Callback {
    private ThemedReactContext context;
    private SurfaceView surface;

    public CameraView(ThemedReactContext context) {
        super(context);
        this.context = context;


        surface = new SurfaceView(context);
        setBackgroundColor(Color.BLACK);
        addView(surface, MATCH_PARENT, MATCH_PARENT);
        surface.getHolder().addCallback(this);
        
        // autofocus over tap-to-focus
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actualPreviewWidth = getResources().getDisplayMetrics().widthPixels;
        int actualPreviewHeight = getResources().getDisplayMetrics().heightPixels;
        int height = Utils.convertDeviceHeightToSupportedAspectRatio(actualPreviewWidth, actualPreviewHeight);
        surface.layout(0, 0, actualPreviewWidth, height);

        Log.w("kyrpae", ""+actualPreviewWidth);
        Log.w("kyrpae", ""+actualPreviewHeight);
        Log.w("kyrpae", ""+height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        CameraViewManager.setCameraView(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        CameraViewManager.setCameraView(this);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        CameraViewManager.removeCameraView();
    }


    public SurfaceHolder getHolder() {
        return surface.getHolder();
    }
}
