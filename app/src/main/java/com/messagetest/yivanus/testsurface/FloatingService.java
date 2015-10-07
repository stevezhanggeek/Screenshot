package com.messagetest.yivanus.testsurface;
//悬浮窗口相关的代码以及用户接口相关

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;

import java.nio.ByteBuffer;

public class FloatingService extends Service {

    private int statusBarHeight;// 状态栏高度
    private View floatview;// 透明窗体
    private boolean viewAdded = false;// 透明窗体是否已经显示
    private WindowManager windowManager;
    private LayoutParams layoutParams;
    private Button cap;
    private String TAG="fuck";
    int mwidth = 1080;
    int mheigth = 1920;
    Intent data;
    MediaProjectionManager mediaProjectionManager;
    MediaProjection mediaProjection;
    ImageReader mImageReader;
    VirtualDisplay virtualDisplay1;
    DisplayMetrics displayMetrics;
    ImageView imgView;
    private static Handler handler=new Handler();
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        data = intent;
        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data);
                        virtualDisplay1 = mediaProjection.createVirtualDisplay("capingress", mwidth, mheigth, displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader.getSurface(), null, null);
                    }
                },1000);
            }
        }).start();
        return super.onStartCommand(intent,flags,startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("oncreate");
        floatview = LayoutInflater.from(this).inflate(R.layout.server, null);
        windowManager = (WindowManager) this.getSystemService(WINDOW_SERVICE);

        imgView = (ImageView)floatview.findViewById(R.id.img1);

        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
        displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        mImageReader = ImageReader.newInstance(mwidth, mheigth, PixelFormat.RGBA_8888, 2);

        cap = (Button) floatview.findViewById(R.id.start);
        cap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Long starttime = System.currentTimeMillis();
                final Image image = mImageReader.acquireLatestImage();
                Image.Plane plane  = image.getPlanes()[0];
                final ByteBuffer buffer = plane.getBuffer();
                final int width = image.getWidth();
                final int height = image.getHeight();
                final int pixelStride = plane.getPixelStride();
                final int rowStride = plane.getRowStride();
                final int rowPadding = rowStride - pixelStride * width;
                final Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buffer);
                final Bitmap bitmap = Bitmap.createScaledBitmap(bmp,bmp.getWidth()/2,bmp.getHeight()/2,false);
                bmp.recycle();
                Long endtime = System.currentTimeMillis();
                Log.e(TAG,String.valueOf(endtime-starttime));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        imgView.setImageBitmap(bitmap);
                    }
                });
                image.close();
            }
        });

        //以下为悬浮窗主要代码

        /*
         * LayoutParams.TYPE_SYSTEM_ERROR：保证该悬浮窗所有View的最上层
         * LayoutParams.FLAG_NOT_FOCUSABLE:该浮动窗不会获得焦点，但可以获得拖动
         * PixelFormat.TRANSPARENT：悬浮窗透明
         */
        layoutParams = new LayoutParams();
        layoutParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;//关键
        layoutParams.type = LayoutParams.TYPE_PHONE;//关键
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.format = PixelFormat.TRANSPARENT;
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        floatview.setOnTouchListener(new View.OnTouchListener() {
            float[] temp = new float[]{0f, 0f};
            public boolean onTouch(View v, MotionEvent event) {
                layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
                int eventaction = event.getAction();
                switch (eventaction) {
                    case MotionEvent.ACTION_DOWN: // 按下事件，记录按下时手指在悬浮窗的XY坐标值
                        temp[0] = event.getX();
                        temp[1] = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        refreshView((int) (event.getRawX() - temp[0]), (int) (event.getRawY() - temp[1]));
                        break;
                }
                return false;
            }
        });
        refresh();
    }


    /**
     * 刷新悬浮窗
     *
     * @param x 拖动后的X轴坐标
     * @param y 拖动后的Y轴坐标
     */
    public void refreshView(int x, int y) {
        //状态栏高度不能立即取，不然得到的值是0
        if (statusBarHeight == 0) {
            View rootView = floatview.getRootView();
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            statusBarHeight = r.top;
        }
        layoutParams.x = x;
        // y轴减去状态栏的高度，因为状态栏不是用户可以绘制的区域，不然拖动的时候会有跳动
        layoutParams.y = y - statusBarHeight;//STATUS_HEIGHT;
        refresh();
    }


    /**
     * 添加悬浮窗或者更新悬浮窗 如果悬浮窗还没添加则添加 如果已经添加则更新其位置
     */
    private void refresh() {
        if (viewAdded) {
            windowManager.updateViewLayout(floatview, layoutParams);
        } else {
            windowManager.addView(floatview, layoutParams);
            viewAdded = true;
        }
    }


    /**
     * 关闭悬浮窗
     */
    public void removeView() {
        if (viewAdded) {
            windowManager.removeView(floatview);
            viewAdded = false;
        }
    }

    @Override
    public void onDestroy() {
        removeView();
        mediaProjection.stop();
        super.onDestroy();
    }

}


