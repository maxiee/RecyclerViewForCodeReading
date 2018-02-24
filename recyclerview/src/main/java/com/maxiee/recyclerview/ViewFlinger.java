package com.maxiee.recyclerview;

import android.os.Build;
import android.support.v4.os.TraceCompat;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import static android.support.v4.view.ViewCompat.TYPE_NON_TOUCH;
import static com.maxiee.recyclerview.RecyclerViewConstants.SCROLL_STATE_SETTLING;

public class ViewFlinger implements Runnable {
    private QuinticInterpolator sQuinticInterpolator = new QuinticInterpolator();

    private int mLastFlingX;
    private int mLastFlingY;
    private OverScroller mScroller;
    Interpolator mInterpolator = sQuinticInterpolator;


    // When set to true, postOnAnimation callbacks are delayed until the run method completes
    private boolean mEatRunOnAnimationRequest = false;

    // Tracks if postAnimationCallback should be re-attached when it is done
    private boolean mReSchedulePostAnimationCallback = false;

    ViewFlinger() {
        mScroller = new OverScroller(getContext(), sQuinticInterpolator);
    }

    @Override
    public void run() {
        if (mLayout == null) {
            stop();
            return; // no layout, cannot scroll.
        }
        disableRunOnAnimationRequests();
        consumePendingUpdateOperations();
        // keep a local reference so that if it is changed during onAnimation method, it won't
        // cause unexpected behaviors
        final OverScroller scroller = mScroller;
        final SmoothScroller smoothScroller = mLayout.mSmoothScroller;
        if (scroller.computeScrollOffset()) {
            final int[] scrollConsumed = mScrollConsumed;
            final int x = scroller.getCurrX();
            final int y = scroller.getCurrY();
            int dx = x - mLastFlingX;
            int dy = y - mLastFlingY;
            int hresult = 0;
            int vresult = 0;
            mLastFlingX = x;
            mLastFlingY = y;
            int overscrollX = 0, overscrollY = 0;

            if (dispatchNestedPreScroll(dx, dy, scrollConsumed, null, TYPE_NON_TOUCH)) {
                dx -= scrollConsumed[0];
                dy -= scrollConsumed[1];
            }

            if (mAdapter != null) {
                eatRequestLayout();
                onEnterLayoutOrScroll();
                TraceCompat.beginSection(TRACE_SCROLL_TAG);
                fillRemainingScrollValues(mState);
                if (dx != 0) {
                    hresult = mLayout.scrollHorizontallyBy(dx, mRecycler, mState);
                    overscrollX = dx - hresult;
                }
                if (dy != 0) {
                    vresult = mLayout.scrollVerticallyBy(dy, mRecycler, mState);
                    overscrollY = dy - vresult;
                }
                TraceCompat.endSection();
                repositionShadowingViews();

                onExitLayoutOrScroll();
                resumeRequestLayout(false);

                if (smoothScroller != null && !smoothScroller.isPendingInitialRun()
                        && smoothScroller.isRunning()) {
                    final int adapterSize = mState.getItemCount();
                    if (adapterSize == 0) {
                        smoothScroller.stop();
                    } else if (smoothScroller.getTargetPosition() >= adapterSize) {
                        smoothScroller.setTargetPosition(adapterSize - 1);
                        smoothScroller.onAnimation(dx - overscrollX, dy - overscrollY);
                    } else {
                        smoothScroller.onAnimation(dx - overscrollX, dy - overscrollY);
                    }
                }
            }
            if (!mItemDecorations.isEmpty()) {
                invalidate();
            }
            if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
                considerReleasingGlowsOnScroll(dx, dy);
            }

            if (!dispatchNestedScroll(hresult, vresult, overscrollX, overscrollY, null,
                    TYPE_NON_TOUCH)
                    && (overscrollX != 0 || overscrollY != 0)) {
                final int vel = (int) scroller.getCurrVelocity();

                int velX = 0;
                if (overscrollX != x) {
                    velX = overscrollX < 0 ? -vel : overscrollX > 0 ? vel : 0;
                }

                int velY = 0;
                if (overscrollY != y) {
                    velY = overscrollY < 0 ? -vel : overscrollY > 0 ? vel : 0;
                }

                if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
                    absorbGlows(velX, velY);
                }
                if ((velX != 0 || overscrollX == x || scroller.getFinalX() == 0)
                        && (velY != 0 || overscrollY == y || scroller.getFinalY() == 0)) {
                    scroller.abortAnimation();
                }
            }
            if (hresult != 0 || vresult != 0) {
                dispatchOnScrolled(hresult, vresult);
            }

            if (!awakenScrollBars()) {
                invalidate();
            }

            final boolean fullyConsumedVertical = dy != 0 && mLayout.canScrollVertically()
                    && vresult == dy;
            final boolean fullyConsumedHorizontal = dx != 0 && mLayout.canScrollHorizontally()
                    && hresult == dx;
            final boolean fullyConsumedAny = (dx == 0 && dy == 0) || fullyConsumedHorizontal
                    || fullyConsumedVertical;

            if (scroller.isFinished() || (!fullyConsumedAny
                    && !hasNestedScrollingParent(TYPE_NON_TOUCH))) {
                // setting state to idle will stop this.
                setScrollState(SCROLL_STATE_IDLE);
                if (ALLOW_THREAD_GAP_WORK) {
                    mPrefetchRegistry.clearPrefetchPositions();
                }
                stopNestedScroll(TYPE_NON_TOUCH);
            } else {
                postOnAnimation();
                if (mGapWorker != null) {
                    mGapWorker.postFromTraversal(RecyclerView.this, dx, dy);
                }
            }
        }
        // call this after the onAnimation is complete not to have inconsistent callbacks etc.
        if (smoothScroller != null) {
            if (smoothScroller.isPendingInitialRun()) {
                smoothScroller.onAnimation(0, 0);
            }
            if (!mReSchedulePostAnimationCallback) {
                smoothScroller.stop(); //stop if it does not trigger any scroll
            }
        }
        enableRunOnAnimationRequests();
    }

    private void disableRunOnAnimationRequests() {
        mReSchedulePostAnimationCallback = false;
        mEatRunOnAnimationRequest = true;
    }

    private void enableRunOnAnimationRequests() {
        mEatRunOnAnimationRequest = false;
        if (mReSchedulePostAnimationCallback) {
            postOnAnimation();
        }
    }

    void postOnAnimation() {
        if (mEatRunOnAnimationRequest) {
            mReSchedulePostAnimationCallback = true;
        } else {
            removeCallbacks(this);
            ViewCompat.postOnAnimation(RecyclerView.this, this);
        }
    }

    public void fling(int velocityX, int velocityY) {
        setScrollState(SCROLL_STATE_SETTLING);
        mLastFlingX = mLastFlingY = 0;
        mScroller.fling(0, 0, velocityX, velocityY,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        postOnAnimation();
    }

    public void smoothScrollBy(int dx, int dy) {
        smoothScrollBy(dx, dy, 0, 0);
    }

    public void smoothScrollBy(int dx, int dy, int vx, int vy) {
        smoothScrollBy(dx, dy, computeScrollDuration(dx, dy, vx, vy));
    }

    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * (float) Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    private int computeScrollDuration(int dx, int dy, int vx, int vy) {
        final int absDx = Math.abs(dx);
        final int absDy = Math.abs(dy);
        final boolean horizontal = absDx > absDy;
        final int velocity = (int) Math.sqrt(vx * vx + vy * vy);
        final int delta = (int) Math.sqrt(dx * dx + dy * dy);
        final int containerSize = horizontal ? getWidth() : getHeight();
        final int halfContainerSize = containerSize / 2;
        final float distanceRatio = Math.min(1.f, 1.f * delta / containerSize);
        final float distance = halfContainerSize + halfContainerSize
                * distanceInfluenceForSnapDuration(distanceRatio);

        final int duration;
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            float absDelta = (float) (horizontal ? absDx : absDy);
            duration = (int) (((absDelta / containerSize) + 1) * 300);
        }
        return Math.min(duration, MAX_SCROLL_DURATION);
    }

    public void smoothScrollBy(int dx, int dy, int duration) {
        smoothScrollBy(dx, dy, duration, sQuinticInterpolator);
    }

    public void smoothScrollBy(int dx, int dy, Interpolator interpolator) {
        smoothScrollBy(dx, dy, computeScrollDuration(dx, dy, 0, 0),
                interpolator == null ? sQuinticInterpolator : interpolator);
    }

    public void smoothScrollBy(int dx, int dy, int duration, Interpolator interpolator) {
        if (mInterpolator != interpolator) {
            mInterpolator = interpolator;
            mScroller = new OverScroller(getContext(), interpolator);
        }
        setScrollState(SCROLL_STATE_SETTLING);
        mLastFlingX = mLastFlingY = 0;
        mScroller.startScroll(0, 0, dx, dy, duration);
        if (Build.VERSION.SDK_INT < 23) {
            // b/64931938 before API 23, startScroll() does not reset getCurX()/getCurY()
            // to start values, which causes fillRemainingScrollValues() put in obsolete values
            // for LayoutManager.onLayoutChildren().
            mScroller.computeScrollOffset();
        }
        postOnAnimation();
    }

    public void stop() {
        removeCallbacks(this);
        mScroller.abortAnimation();
    }

    /**
     * add by Maxiee
     * @return
     */
    public OverScroller getScroller() {
        return mScroller;
    }
}
