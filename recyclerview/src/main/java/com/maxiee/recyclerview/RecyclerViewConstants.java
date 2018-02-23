package com.maxiee.recyclerview;

import android.os.Build;

public class RecyclerViewConstants {
    public static final String TAG = "MaxieeRV";
    public static final boolean DEBUG = true;

    public static final int NO_POSITION = -1;
    public static final long NO_ID = -1;
    public static final int INVALID_TYPE = -1;

    static final boolean VERBOSE_TRACING = false;

    static final boolean ALLOW_SIZE_IN_UNSPECIFIED_SPEC = Build.VERSION.SDK_INT >= 23;

}
