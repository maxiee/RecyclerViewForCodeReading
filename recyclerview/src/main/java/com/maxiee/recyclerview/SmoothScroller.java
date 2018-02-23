package com.maxiee.recyclerview;

import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;

import static com.maxiee.recyclerview.RecyclerViewConstants.DEBUG;
import static com.maxiee.recyclerview.RecyclerViewConstants.NO_POSITION;
import static com.maxiee.recyclerview.RecyclerViewConstants.TAG;

/**
 * <p>Base class for smooth scrolling. Handles basic tracking of the target view position and
 * provides methods to trigger a programmatic scroll.</p>
 *
 * @see LinearSmoothScroller
 */
public abstract class SmoothScroller {

    private int mTargetPosition = NO_POSITION;

    private RecyclerView mRecyclerView;

    private LayoutManager mLayoutManager;

    private boolean mPendingInitialRun;

    private boolean mRunning;

    private View mTargetView;

    private final Action mRecyclingAction;

    public SmoothScroller() {
        mRecyclingAction = new Action(0, 0);
    }

    /**
     * Starts a smooth scroll for the given target position.
     * <p>In each animation step, {@link RecyclerView} will check
     * for the target view and call either
     * {@link #onTargetFound(android.view.View, RecyclerView.State, SmoothScroller.Action)} or
     * {@link #onSeekTargetStep(int, int, RecyclerView.State, SmoothScroller.Action)} until
     * SmoothScroller is stopped.</p>
     *
     * <p>Note that if RecyclerView finds the target view, it will automatically stop the
     * SmoothScroller. This <b>does not</b> mean that scroll will stop, it only means it will
     * stop calling SmoothScroller in each animation step.</p>
     */
    void start(RecyclerView recyclerView, LayoutManager layoutManager) {
        mRecyclerView = recyclerView;
        mLayoutManager = layoutManager;
        if (mTargetPosition == NO_POSITION) {
            throw new IllegalArgumentException("Invalid target position");
        }
        mRecyclerView.mState.mTargetPosition = mTargetPosition;
        mRunning = true;
        mPendingInitialRun = true;
        mTargetView = findViewByPosition(getTargetPosition());
        onStart();
        mRecyclerView.mViewFlinger.postOnAnimation();
    }

    public void setTargetPosition(int targetPosition) {
        mTargetPosition = targetPosition;
    }

    /**
     * @return The LayoutManager to which this SmoothScroller is attached. Will return
     * <code>null</code> after the SmoothScroller is stopped.
     */
    @Nullable
    public LayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    /**
     * Stops running the SmoothScroller in each animation callback. Note that this does not
     * cancel any existing {@link Action} updated by
     * {@link #onTargetFound(android.view.View, RecyclerView.State, SmoothScroller.Action)} or
     * {@link #onSeekTargetStep(int, int, RecyclerView.State, SmoothScroller.Action)}.
     */
    protected final void stop() {
        if (!mRunning) {
            return;
        }
        onStop();
        mRecyclerView.mState.mTargetPosition = RecyclerView.NO_POSITION;
        mTargetView = null;
        mTargetPosition = RecyclerView.NO_POSITION;
        mPendingInitialRun = false;
        mRunning = false;
        // trigger a cleanup
        mLayoutManager.onSmoothScrollerStopped(this);
        // clear references to avoid any potential leak by a custom smooth scroller
        mLayoutManager = null;
        mRecyclerView = null;
    }

    /**
     * Returns true if SmoothScroller has been started but has not received the first
     * animation
     * callback yet.
     *
     * @return True if this SmoothScroller is waiting to start
     */
    public boolean isPendingInitialRun() {
        return mPendingInitialRun;
    }


    /**
     * @return True if SmoothScroller is currently active
     */
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Returns the adapter position of the target item
     *
     * @return Adapter position of the target item or
     * {@link RecyclerView#NO_POSITION} if no target view is set.
     */
    public int getTargetPosition() {
        return mTargetPosition;
    }

    private void onAnimation(int dx, int dy) {
        final RecyclerView recyclerView = mRecyclerView;
        if (!mRunning || mTargetPosition == NO_POSITION || recyclerView == null) {
            stop();
        }
        mPendingInitialRun = false;
        if (mTargetView != null) {
            // verify target position
            if (getChildPosition(mTargetView) == mTargetPosition) {
                onTargetFound(mTargetView, recyclerView.mState, mRecyclingAction);
                mRecyclingAction.runIfNecessary(recyclerView);
                stop();
            } else {
                Log.e(TAG, "Passed over target position while smooth scrolling.");
                mTargetView = null;
            }
        }
        if (mRunning) {
            onSeekTargetStep(dx, dy, recyclerView.mState, mRecyclingAction);
            boolean hadJumpTarget = mRecyclingAction.hasJumpTarget();
            mRecyclingAction.runIfNecessary(recyclerView);
            if (hadJumpTarget) {
                // It is not stopped so needs to be restarted
                if (mRunning) {
                    mPendingInitialRun = true;
                    recyclerView.mViewFlinger.postOnAnimation();
                } else {
                    stop(); // done
                }
            }
        }
    }

    /**
     * @see RecyclerView#getChildLayoutPosition(android.view.View)
     */
    public int getChildPosition(View view) {
        return mRecyclerView.getChildLayoutPosition(view);
    }

    /**
     * @see RecyclerView.LayoutManager#getChildCount()
     */
    public int getChildCount() {
        return mRecyclerView.mLayout.getChildCount();
    }

    /**
     * @see RecyclerView.LayoutManager#findViewByPosition(int)
     */
    public View findViewByPosition(int position) {
        return mRecyclerView.mLayout.findViewByPosition(position);
    }

    /**
     * @see RecyclerView#scrollToPosition(int)
     * @deprecated Use {@link Action#jumpTo(int)}.
     */
    @Deprecated
    public void instantScrollToPosition(int position) {
        mRecyclerView.scrollToPosition(position);
    }

    protected void onChildAttachedToWindow(View child) {
        if (getChildPosition(child) == getTargetPosition()) {
            mTargetView = child;
            if (DEBUG) {
                Log.d(TAG, "smooth scroll target view has been attached");
            }
        }
    }

    /**
     * Normalizes the vector.
     * @param scrollVector The vector that points to the target scroll position
     */
    protected void normalize(PointF scrollVector) {
        final float magnitude = (float) Math.sqrt(scrollVector.x * scrollVector.x
                + scrollVector.y * scrollVector.y);
        scrollVector.x /= magnitude;
        scrollVector.y /= magnitude;
    }

    /**
     * Called when smooth scroll is started. This might be a good time to do setup.
     */
    protected abstract void onStart();

    /**
     * Called when smooth scroller is stopped. This is a good place to cleanup your state etc.
     * @see #stop()
     */
    protected abstract void onStop();

    /**
     * <p>RecyclerView will call this method each time it scrolls until it can find the target
     * position in the layout.</p>
     * <p>SmoothScroller should check dx, dy and if scroll should be changed, update the
     * provided {@link Action} to define the next scroll.</p>
     *
     * @param dx        Last scroll amount horizontally
     * @param dy        Last scroll amount vertically
     * @param state     Transient state of RecyclerView
     * @param action    If you want to trigger a new smooth scroll and cancel the previous one,
     *                  update this object.
     */
    protected abstract void onSeekTargetStep(int dx, int dy, State state, Action action);

    /**
     * Called when the target position is laid out. This is the last callback SmoothScroller
     * will receive and it should update the provided {@link Action} to define the scroll
     * details towards the target view.
     * @param targetView    The view element which render the target position.
     * @param state         Transient state of RecyclerView
     * @param action        Action instance that you should update to define final scroll action
     *                      towards the targetView
     */
    protected abstract void onTargetFound(View targetView, State state, Action action);

    /**
     * Holds information about a smooth scroll request by a {@link SmoothScroller}.
     */
    public static class Action {

        public static final int UNDEFINED_DURATION = Integer.MIN_VALUE;

        private int mDx;

        private int mDy;

        private int mDuration;

        private int mJumpToPosition = NO_POSITION;

        private Interpolator mInterpolator;

        private boolean mChanged = false;

        // we track this variable to inform custom implementer if they are updating the action
        // in every animation callback
        private int mConsecutiveUpdates = 0;

        /**
         * @param dx Pixels to scroll horizontally
         * @param dy Pixels to scroll vertically
         */
        public Action(int dx, int dy) {
            this(dx, dy, UNDEFINED_DURATION, null);
        }

        /**
         * @param dx       Pixels to scroll horizontally
         * @param dy       Pixels to scroll vertically
         * @param duration Duration of the animation in milliseconds
         */
        public Action(int dx, int dy, int duration) {
            this(dx, dy, duration, null);
        }

        /**
         * @param dx           Pixels to scroll horizontally
         * @param dy           Pixels to scroll vertically
         * @param duration     Duration of the animation in milliseconds
         * @param interpolator Interpolator to be used when calculating scroll position in each
         *                     animation step
         */
        public Action(int dx, int dy, int duration, Interpolator interpolator) {
            mDx = dx;
            mDy = dy;
            mDuration = duration;
            mInterpolator = interpolator;
        }

        /**
         * Instead of specifying pixels to scroll, use the target position to jump using
         * {@link RecyclerView#scrollToPosition(int)}.
         * <p>
         * You may prefer using this method if scroll target is really far away and you prefer
         * to jump to a location and smooth scroll afterwards.
         * <p>
         * Note that calling this method takes priority over other update methods such as
         * {@link #update(int, int, int, Interpolator)}, {@link #setX(float)},
         * {@link #setY(float)} and #{@link #setInterpolator(Interpolator)}. If you call
         * {@link #jumpTo(int)}, the other changes will not be considered for this animation
         * frame.
         *
         * @param targetPosition The target item position to scroll to using instant scrolling.
         */
        public void jumpTo(int targetPosition) {
            mJumpToPosition = targetPosition;
        }

        boolean hasJumpTarget() {
            return mJumpToPosition >= 0;
        }

        void runIfNecessary(RecyclerView recyclerView) {
            if (mJumpToPosition >= 0) {
                final int position = mJumpToPosition;
                mJumpToPosition = NO_POSITION;
                recyclerView.jumpToPositionForSmoothScroller(position);
                mChanged = false;
                return;
            }
            if (mChanged) {
                validate();
                if (mInterpolator == null) {
                    if (mDuration == UNDEFINED_DURATION) {
                        recyclerView.mViewFlinger.smoothScrollBy(mDx, mDy);
                    } else {
                        recyclerView.mViewFlinger.smoothScrollBy(mDx, mDy, mDuration);
                    }
                } else {
                    recyclerView.mViewFlinger.smoothScrollBy(
                            mDx, mDy, mDuration, mInterpolator);
                }
                mConsecutiveUpdates++;
                if (mConsecutiveUpdates > 10) {
                    // A new action is being set in every animation step. This looks like a bad
                    // implementation. Inform developer.
                    Log.e(TAG, "Smooth Scroll action is being updated too frequently. Make sure"
                            + " you are not changing it unless necessary");
                }
                mChanged = false;
            } else {
                mConsecutiveUpdates = 0;
            }
        }

        private void validate() {
            if (mInterpolator != null && mDuration < 1) {
                throw new IllegalStateException("If you provide an interpolator, you must"
                        + " set a positive duration");
            } else if (mDuration < 1) {
                throw new IllegalStateException("Scroll duration must be a positive number");
            }
        }

        public int getDx() {
            return mDx;
        }

        public void setDx(int dx) {
            mChanged = true;
            mDx = dx;
        }

        public int getDy() {
            return mDy;
        }

        public void setDy(int dy) {
            mChanged = true;
            mDy = dy;
        }

        public int getDuration() {
            return mDuration;
        }

        public void setDuration(int duration) {
            mChanged = true;
            mDuration = duration;
        }

        public Interpolator getInterpolator() {
            return mInterpolator;
        }

        /**
         * Sets the interpolator to calculate scroll steps
         * @param interpolator The interpolator to use. If you specify an interpolator, you must
         *                     also set the duration.
         * @see #setDuration(int)
         */
        public void setInterpolator(Interpolator interpolator) {
            mChanged = true;
            mInterpolator = interpolator;
        }

        /**
         * Updates the action with given parameters.
         * @param dx Pixels to scroll horizontally
         * @param dy Pixels to scroll vertically
         * @param duration Duration of the animation in milliseconds
         * @param interpolator Interpolator to be used when calculating scroll position in each
         *                     animation step
         */
        public void update(int dx, int dy, int duration, Interpolator interpolator) {
            mDx = dx;
            mDy = dy;
            mDuration = duration;
            mInterpolator = interpolator;
            mChanged = true;
        }
    }

    /**
     * An interface which is optionally implemented by custom {@link RecyclerView.LayoutManager}
     * to provide a hint to a {@link SmoothScroller} about the location of the target position.
     */
    public interface ScrollVectorProvider {
        /**
         * Should calculate the vector that points to the direction where the target position
         * can be found.
         * <p>
         * This method is used by the {@link LinearSmoothScroller} to initiate a scroll towards
         * the target position.
         * <p>
         * The magnitude of the vector is not important. It is always normalized before being
         * used by the {@link LinearSmoothScroller}.
         * <p>
         * LayoutManager should not check whether the position exists in the adapter or not.
         *
         * @param targetPosition the target position to which the returned vector should point
         *
         * @return the scroll vector for a given position.
         */
        PointF computeScrollVectorForPosition(int targetPosition);
    }
}
