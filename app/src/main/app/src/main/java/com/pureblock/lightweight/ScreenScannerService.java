package com.pureblock.lightweight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;

public class ScreenScannerService extends Service {
    public static MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private WindowManager windowManager;
    private View blockerOverlay;
    private boolean isOverlayVisible = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupOverlayView();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel("pb_svc", "PureBlock Active", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, "pb_svc").build();
        startForeground(1, notification);
    }

    private void setupOverlayView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        blockerOverlay = new FrameLayout(this);
        blockerOverlay.setBackgroundColor(0xFF0F0F11); // Clean, ultra-dark blocking design

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Capture at ultra-low 160x120 resolution to save memory and CPU pipelines
        imageReader = ImageReader.newInstance(160, 120, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScanBelt", 160, 120, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * 160;

                    // Reuse a small scale bitmap allocation
                    Bitmap bitmap = Bitmap.createBitmap(160 + rowPadding / pixelStride, 120, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    boolean shouldBlock = MathematicalAnalyzer.analyzeFrame(bitmap);
                    updateOverlayState(shouldBlock);
                    bitmap.recycle();
                }
            } catch (Exception ignored) {}
        }, null);

        return START_STICKY;
    }

    private void updateOverlayState(boolean show) {
        if (show && !isOverlayVisible) {
            windowManager.addView(blockerOverlay, new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT));
            isOverlayVisible = true;
        } else if (!show && isOverlayVisible) {
            windowManager.removeView(blockerOverlay);
            isOverlayVisible = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
    }
}
