package com.maxiee.recyclerview;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.maxiee.recyclerview.AdapterChangeConstants.FLAG_APPEARED_IN_PRE_LAYOUT;
import static com.maxiee.recyclerview.AdapterChangeConstants.FLAG_CHANGED;
import static com.maxiee.recyclerview.AdapterChangeConstants.FLAG_INVALIDATED;
import static com.maxiee.recyclerview.AdapterChangeConstants.FLAG_MOVED;
import static com.maxiee.recyclerview.AdapterChangeConstants.FLAG_REMOVED;

/**
 * The set of flags that might be passed to
 * {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
 */
@IntDef(flag = true, value = {
        FLAG_CHANGED, FLAG_REMOVED, FLAG_MOVED, FLAG_INVALIDATED,
        FLAG_APPEARED_IN_PRE_LAYOUT
})
@Retention(RetentionPolicy.SOURCE)
public @interface AdapterChanges {


}
