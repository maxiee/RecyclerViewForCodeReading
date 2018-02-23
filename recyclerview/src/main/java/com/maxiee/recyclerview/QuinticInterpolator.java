package com.maxiee.recyclerview;

import android.view.animation.Interpolator;

public class QuinticInterpolator implements Interpolator {
    @Override
    public float getInterpolation(float t) {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
    }
}
