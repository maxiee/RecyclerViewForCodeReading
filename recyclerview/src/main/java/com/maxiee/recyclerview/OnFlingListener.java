package com.maxiee.recyclerview;

/**
 * This class defines the behavior of fling if the developer wishes to handle it.
 * <p>
 * Subclasses of {@link OnFlingListener} can be used to implement custom fling behavior.
 *
 * @see #setOnFlingListener(OnFlingListener)
 */
public abstract class OnFlingListener {

    /**
     * Override this to handle a fling given the velocities in both x and y directions.
     * Note that this method will only be called if the associated {@link LayoutManager}
     * supports scrolling and the fling is not handled by nested scrolls first.
     *
     * @param velocityX the fling velocity on the X axis
     * @param velocityY the fling velocity on the Y axis
     *
     * @return true if the fling was handled, false otherwise.
     */
    public abstract boolean onFling(int velocityX, int velocityY);
}
