package com.example.testlistpip2;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

public class VodScreen {
    private static int orientation = Configuration.ORIENTATION_PORTRAIT;
    private static boolean portrait_full = false;
    //
    private static int mDragTopHeight = 0;
    private static int mDragTopWidth = 0;
    private static int mDragBottomHeight = 0;

    //
    public static int getOrientation() {
        return orientation;
    }

    public static boolean isLandscape() {
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isHalf() {
        return orientation == Configuration.ORIENTATION_PORTRAIT && portrait_full == false;
    }

    public static boolean isPortraitFull() {
        return orientation == Configuration.ORIENTATION_PORTRAIT && portrait_full;
    }

    //
    public static void init(Context context) {
        if (mDragTopHeight == 0) {
            WindowManager wManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            int width, height;

            wManager.getDefaultDisplay().getMetrics(metrics);
            if (metrics.heightPixels > metrics.widthPixels) {
                width = metrics.widthPixels;
                height = metrics.heightPixels;
            } else {
                width = metrics.heightPixels;
                height = metrics.widthPixels;
            }
            mDragTopHeight = width * 9 / 16;
            mDragTopWidth = width;
        }
    }

    public static int getDragTopHeight() {
        return mDragTopHeight;
    }

    public static int getDragBottomHeight() {
        return mDragBottomHeight;
    }

    public static void setDragBottomHeight(int height) {
        mDragBottomHeight = isHalf() ? height : 0;
    }

    //
    static void setOrientation(int orientation) {
        VodScreen.orientation = orientation;
        System.out.println(">>> 1.ORIENTATION: " + orientation + ", portrait_full: " + portrait_full);
    }

    static void setPortrait_full(boolean portrait_full) {
        VodScreen.portrait_full = portrait_full;
        System.out.println(">>> 2.ORIENTATION: " + orientation + ", portrait_full: " + portrait_full);
    }

    public static void showView_Landscape(View view) {
        if (view == null) return;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    public static void hideView_Portrait(View view) {
        if (view == null) return;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
        }
    }

    public static void hideView_Landscape(View view) {
        if (view == null) return;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
        }
    }

    //
    public static int getScreenOrientation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    System.out.println("getScreenOrientation() - Unknown screen orientation. Defaulting to portrait.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    System.out.println("getScreenOrientation() - Unknown screen orientation. Defaulting to landscape.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    public static int getScreenOrientation_Landscape(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    //
    public static Size getVideoSize_16_9(int w, int h) {
        Size size = new Size();
        if (w <= 0 || h <= 0) {
            return new Size(mDragTopWidth, mDragTopHeight);
        }
        int wtot = w * 9;
        int htot = h * 16;

        if (wtot == htot) {
            return new Size(mDragTopWidth, mDragTopHeight);
        }
        if (wtot < htot) {
            int nWidth = mDragTopHeight * w / h;
            return new Size(nWidth, mDragTopHeight);
        }
        int nHeight = mDragTopWidth * h / w;
        return new Size(mDragTopWidth, nHeight);
    }

    //
    public static class Size {
        public int cx;
        public int cy;

        //
        public Size() {
            this.cx = 0;
            this.cy = 0;
        }

        public Size(int x, int y) {
            this.cx = x;
            this.cy = y;
        }
    }
}
