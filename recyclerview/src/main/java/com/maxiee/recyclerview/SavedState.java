package com.maxiee.recyclerview;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.RestrictTo;
import android.support.v4.view.AbsSavedState;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * This is public so that the CREATOR can be accessed on cold launch.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class SavedState extends AbsSavedState {

    Parcelable mLayoutState;

    /**
     * called by CREATOR
     */
    SavedState(Parcel in, ClassLoader loader) {
        super(in, loader);
        mLayoutState = in.readParcelable(
                loader != null ? loader : LayoutManager.class.getClassLoader());
    }

    /**
     * Called by onSaveInstanceState
     */
    SavedState(Parcelable superState) {
        super(superState);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(mLayoutState, 0);
    }

    void copyFrom(SavedState other) {
        mLayoutState = other.mLayoutState;
    }

    public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
        @Override
        public SavedState createFromParcel(Parcel in, ClassLoader loader) {
            return new SavedState(in, loader);
        }

        @Override
        public SavedState createFromParcel(Parcel in) {
            return new SavedState(in, null);
        }

        @Override
        public SavedState[] newArray(int size) {
            return new SavedState[size];
        }
    };
}