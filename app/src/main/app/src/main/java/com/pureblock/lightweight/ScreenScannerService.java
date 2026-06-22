package com.pureblock.lightweight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.nio.ByteBuffer;

public class ScreenScannerService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private WindowManager windowManager;
    private View blockerOverlay;
    private boolean isOverlayVisible = false;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Handler mainHandler;
    
    // Fix: Timestamp tracker to limit operations per second
    private long lastAnalysisTime = 0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(getMainLooper());
        
        backgroundThread = new HandlerThread("PureBlockWorker");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        createNotificationChannel();
        setupOverlayView();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel("pb_svc", "PureBlock Service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        
        Notification notification = new Notification.Builder(this, "pb_svc")
                .setContentTitle("PureBlock Shield Active")
                .setContentText("Running smoothly in background...")
                .setSmallIcon(android.R.drawable.ic_secure) 
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    private void setupOverlayView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        blockerOverlay = new FrameLayout(this);
        blockerOverlay.setBackgroundColor(0xFF0F0F11); 
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        int resultCode = intent.getIntExtra("RESULT_CODE", 0);
        Intent data = intent.getParcelableExtra("DATA_INTENT");

        if (resultCode != 0 && data != null && mediaProjection == null) {
            MediaProjectionManager mpManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = mpManager.getMediaProjection(resultCode, data);

            imageReader = ImageReader.newInstance(160, 120, PixelFormat.RGBA_8888, 2);
            
            virtualDisplay = mediaProjection.createVirtualDisplay("ScanBelt", 160, 120, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, backgroundHandler);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                Bitmap bitmap = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        
                        // Fix: Throttle engine. Only allow 1 frame every 500 milliseconds (2 frames per second)
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastAnalysisTime < 500) {
                            image.close();
                            return;
                        }
                        lastAnalysisTime = currentTime;

                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * 160;

                        int width = 160 + rowPadding / pixelStride;
                        if (buffer.remaining() >= width * 120 * 4) {
                            bitmap = Bitmap.createBitmap(width, 120, Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(buffer);

                            boolean shouldBlock = MathematicalAnalyzer.analyzeFrame(bitmap);
                            updateOverlayState(shouldBlock);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (bitmap != null) bitmap.recycle();
                    if (image != null) image.close();
                }
            }, backgroundHandler);
        }

        return START_STICKY;
    }

    private void updateOverlayState(final boolean show) {
        mainHandler.post(() -> {
            try {
                if (show && !isOverlayVisible) {
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, 
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.CENTER;
                    windowManager.addView(blockerOverlay, params);
                    isOverlayVisible = true;
                } else if (!show && isOverlayVisible) {
                    windowManager.removeView(blockerOverlay);
                    isOverlayVisible = false;
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (virtualDisplay != null) virtualDisplay.release();
            if (mediaProjection != null) mediaProjection.stop();
            if (backgroundThread != null) backgroundThread.quitSafely();
            if (isOverlayVisible && blockerOverlay != null) windowManager.removeView(blockerOverlay);
        } catch (Exception ignored) {}
    }
}
