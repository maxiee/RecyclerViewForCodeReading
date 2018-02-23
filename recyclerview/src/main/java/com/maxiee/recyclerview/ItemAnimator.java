/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maxiee.recyclerview;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ItemAnimator {

    private ItemAnimatorListener mListener = null;
    private ArrayList<ItemAnimatorFinishedListener> mFinishedListeners =
            new ArrayList<ItemAnimatorFinishedListener>();

    private long mAddDuration = 120;
    private long mRemoveDuration = 120;
    private long mMoveDuration = 250;
    private long mChangeDuration = 250;

    /**
     * Gets the current duration for which all move animations will run.
     *
     * @return The current move duration
     */
    public long getMoveDuration() {
        return mMoveDuration;
    }

    /**
     * Sets the duration for which all move animations will run.
     *
     * @param moveDuration The move duration
     */
    public void setMoveDuration(long moveDuration) {
        mMoveDuration = moveDuration;
    }

    /**
     * Gets the current duration for which all add animations will run.
     *
     * @return The current add duration
     */
    public long getAddDuration() {
        return mAddDuration;
    }

    /**
     * Sets the duration for which all add animations will run.
     *
     * @param addDuration The add duration
     */
    public void setAddDuration(long addDuration) {
        mAddDuration = addDuration;
    }

    /**
     * Gets the current duration for which all remove animations will run.
     *
     * @return The current remove duration
     */
    public long getRemoveDuration() {
        return mRemoveDuration;
    }

    /**
     * Sets the duration for which all remove animations will run.
     *
     * @param removeDuration The remove duration
     */
    public void setRemoveDuration(long removeDuration) {
        mRemoveDuration = removeDuration;
    }

    /**
     * Gets the current duration for which all change animations will run.
     *
     * @return The current change duration
     */
    public long getChangeDuration() {
        return mChangeDuration;
    }

    /**
     * Sets the duration for which all change animations will run.
     *
     * @param changeDuration The change duration
     */
    public void setChangeDuration(long changeDuration) {
        mChangeDuration = changeDuration;
    }

    /**
     * Internal only:
     * Sets the listener that must be called when the animator is finished
     * animating the item (or immediately if no animation happens). This is set
     * internally and is not intended to be set by external code.
     *
     * @param listener The listener that must be called.
     */
    void setListener(ItemAnimatorListener listener) {
        mListener = listener;
    }

    /**
     * Called by the RecyclerView before the layout begins. Item animator should record
     * necessary information about the View before it is potentially rebound, moved or removed.
     * <p>
     * The data returned from this method will be passed to the related <code>animate**</code>
     * methods.
     * <p>
     * Note that this method may be called after pre-layout phase if LayoutManager adds new
     * Views to the layout in pre-layout pass.
     * <p>
     * The default implementation returns an {@link ItemHolderInfo} which holds the bounds of
     * the View and the adapter change flags.
     *
     * @param state       The current State of RecyclerView which includes some useful data
     *                    about the layout that will be calculated.
     * @param viewHolder  The ViewHolder whose information should be recorded.
     * @param changeFlags Additional information about what changes happened in the Adapter
     *                    about the Item represented by this ViewHolder. For instance, if
     *                    item is deleted from the adapter, {@link #FLAG_REMOVED} will be set.
     * @param payloads    The payload list that was previously passed to
     *                    {@link Adapter#notifyItemChanged(int, Object)} or
     *                    {@link Adapter#notifyItemRangeChanged(int, int, Object)}.
     *
     * @return An ItemHolderInfo instance that preserves necessary information about the
     * ViewHolder. This object will be passed back to related <code>animate**</code> methods
     * after layout is complete.
     *
     * @see #recordPostLayoutInformation(State, ViewHolder)
     * @see #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     */
    public @NonNull
    ItemHolderInfo recordPreLayoutInformation(@NonNull State state,
                                              @NonNull ViewHolder viewHolder, @AdapterChanges int changeFlags,
                                              @NonNull List<Object> payloads) {
        return obtainHolderInfo().setFrom(viewHolder);
    }

    /**
     * Called by the RecyclerView after the layout is complete. Item animator should record
     * necessary information about the View's final state.
     * <p>
     * The data returned from this method will be passed to the related <code>animate**</code>
     * methods.
     * <p>
     * The default implementation returns an {@link ItemHolderInfo} which holds the bounds of
     * the View.
     *
     * @param state      The current State of RecyclerView which includes some useful data about
     *                   the layout that will be calculated.
     * @param viewHolder The ViewHolder whose information should be recorded.
     *
     * @return An ItemHolderInfo that preserves necessary information about the ViewHolder.
     * This object will be passed back to related <code>animate**</code> methods when
     * RecyclerView decides how items should be animated.
     *
     * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
     * @see #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     */
    public @NonNull ItemHolderInfo recordPostLayoutInformation(@NonNull State state,
                                                               @NonNull ViewHolder viewHolder) {
        return obtainHolderInfo().setFrom(viewHolder);
    }

    /**
     * Called by the RecyclerView when a ViewHolder has disappeared from the layout.
     * <p>
     * This means that the View was a child of the LayoutManager when layout started but has
     * been removed by the LayoutManager. It might have been removed from the adapter or simply
     * become invisible due to other factors. You can distinguish these two cases by checking
     * the change flags that were passed to
     * {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     * <p>
     * Note that when a ViewHolder both changes and disappears in the same layout pass, the
     * animation callback method which will be called by the RecyclerView depends on the
     * ItemAnimator's decision whether to re-use the same ViewHolder or not, and also the
     * LayoutManager's decision whether to layout the changed version of a disappearing
     * ViewHolder or not. RecyclerView will call
     * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateChange} instead of {@code animateDisappearance} if and only if the ItemAnimator
     * returns {@code false} from
     * {@link #canReuseUpdatedViewHolder(ViewHolder) canReuseUpdatedViewHolder} and the
     * LayoutManager lays out a new disappearing view that holds the updated information.
     * Built-in LayoutManagers try to avoid laying out updated versions of disappearing views.
     * <p>
     * If LayoutManager supports predictive animations, it might provide a target disappear
     * location for the View by laying it out in that location. When that happens,
     * RecyclerView will call {@link #recordPostLayoutInformation(State, ViewHolder)} and the
     * response of that call will be passed to this method as the <code>postLayoutInfo</code>.
     * <p>
     * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} when the animation
     * is complete (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it
     * decides not to animate the view).
     *
     * @param viewHolder    The ViewHolder which should be animated
     * @param preLayoutInfo The information that was returned from
     *                      {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     * @param postLayoutInfo The information that was returned from
     *                       {@link #recordPostLayoutInformation(State, ViewHolder)}. Might be
     *                       null if the LayoutManager did not layout the item.
     *
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    public abstract boolean animateDisappearance(@NonNull ViewHolder viewHolder,
                                                 @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo);

    /**
     * Called by the RecyclerView when a ViewHolder is added to the layout.
     * <p>
     * In detail, this means that the ViewHolder was <b>not</b> a child when the layout started
     * but has  been added by the LayoutManager. It might be newly added to the adapter or
     * simply become visible due to other factors.
     * <p>
     * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} when the animation
     * is complete (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it
     * decides not to animate the view).
     *
     * @param viewHolder     The ViewHolder which should be animated
     * @param preLayoutInfo  The information that was returned from
     *                       {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     *                       Might be null if Item was just added to the adapter or
     *                       LayoutManager does not support predictive animations or it could
     *                       not predict that this ViewHolder will become visible.
     * @param postLayoutInfo The information that was returned from {@link
     *                       #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     *
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    public abstract boolean animateAppearance(@NonNull ViewHolder viewHolder,
                                              @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo);

    /**
     * Called by the RecyclerView when a ViewHolder is present in both before and after the
     * layout and RecyclerView has not received a {@link Adapter#notifyItemChanged(int)} call
     * for it or a {@link Adapter#notifyDataSetChanged()} call.
     * <p>
     * This ViewHolder still represents the same data that it was representing when the layout
     * started but its position / size may be changed by the LayoutManager.
     * <p>
     * If the Item's layout position didn't change, RecyclerView still calls this method because
     * it does not track this information (or does not necessarily know that an animation is
     * not required). Your ItemAnimator should handle this case and if there is nothing to
     * animate, it should call {@link #dispatchAnimationFinished(ViewHolder)} and return
     * <code>false</code>.
     * <p>
     * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} when the animation
     * is complete (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it
     * decides not to animate the view).
     *
     * @param viewHolder     The ViewHolder which should be animated
     * @param preLayoutInfo  The information that was returned from
     *                       {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     * @param postLayoutInfo The information that was returned from {@link
     *                       #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     *
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    public abstract boolean animatePersistence(@NonNull ViewHolder viewHolder,
                                               @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo);

    /**
     * Called by the RecyclerView when an adapter item is present both before and after the
     * layout and RecyclerView has received a {@link Adapter#notifyItemChanged(int)} call
     * for it. This method may also be called when
     * {@link Adapter#notifyDataSetChanged()} is called and adapter has stable ids so that
     * RecyclerView could still rebind views to the same ViewHolders. If viewType changes when
     * {@link Adapter#notifyDataSetChanged()} is called, this method <b>will not</b> be called,
     * instead, {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)} will be
     * called for the new ViewHolder and the old one will be recycled.
     * <p>
     * If this method is called due to a {@link Adapter#notifyDataSetChanged()} call, there is
     * a good possibility that item contents didn't really change but it is rebound from the
     * adapter. {@link DefaultItemAnimator} will skip animating the View if its location on the
     * screen didn't change and your animator should handle this case as well and avoid creating
     * unnecessary animations.
     * <p>
     * When an item is updated, ItemAnimator has a chance to ask RecyclerView to keep the
     * previous presentation of the item as-is and supply a new ViewHolder for the updated
     * presentation (see: {@link #canReuseUpdatedViewHolder(ViewHolder, List)}.
     * This is useful if you don't know the contents of the Item and would like
     * to cross-fade the old and the new one ({@link DefaultItemAnimator} uses this technique).
     * <p>
     * When you are writing a custom item animator for your layout, it might be more performant
     * and elegant to re-use the same ViewHolder and animate the content changes manually.
     * <p>
     * When {@link Adapter#notifyItemChanged(int)} is called, the Item's view type may change.
     * If the Item's view type has changed or ItemAnimator returned <code>false</code> for
     * this ViewHolder when {@link #canReuseUpdatedViewHolder(ViewHolder, List)} was called, the
     * <code>oldHolder</code> and <code>newHolder</code> will be different ViewHolder instances
     * which represent the same Item. In that case, only the new ViewHolder is visible
     * to the LayoutManager but RecyclerView keeps old ViewHolder attached for animations.
     * <p>
     * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} for each distinct
     * ViewHolder when their animation is complete
     * (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it decides not to
     * animate the view).
     * <p>
     *  If oldHolder and newHolder are the same instance, you should call
     * {@link #dispatchAnimationFinished(ViewHolder)} <b>only once</b>.
     * <p>
     * Note that when a ViewHolder both changes and disappears in the same layout pass, the
     * animation callback method which will be called by the RecyclerView depends on the
     * ItemAnimator's decision whether to re-use the same ViewHolder or not, and also the
     * LayoutManager's decision whether to layout the changed version of a disappearing
     * ViewHolder or not. RecyclerView will call
     * {@code animateChange} instead of
     * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateDisappearance} if and only if the ItemAnimator returns {@code false} from
     * {@link #canReuseUpdatedViewHolder(ViewHolder) canReuseUpdatedViewHolder} and the
     * LayoutManager lays out a new disappearing view that holds the updated information.
     * Built-in LayoutManagers try to avoid laying out updated versions of disappearing views.
     *
     * @param oldHolder     The ViewHolder before the layout is started, might be the same
     *                      instance with newHolder.
     * @param newHolder     The ViewHolder after the layout is finished, might be the same
     *                      instance with oldHolder.
     * @param preLayoutInfo  The information that was returned from
     *                       {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     * @param postLayoutInfo The information that was returned from {@link
     *                       #recordPreLayoutInformation(State, ViewHolder, int, List)}.
     *
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    public abstract boolean animateChange(@NonNull ViewHolder oldHolder,
                                          @NonNull ViewHolder newHolder,
                                          @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo);

    @AdapterChanges static int buildAdapterChangeFlagsForAnimations(ViewHolder viewHolder) {
        int flags = viewHolder.mFlags & (FLAG_INVALIDATED | FLAG_REMOVED | FLAG_CHANGED);
        if (viewHolder.isInvalid()) {
            return FLAG_INVALIDATED;
        }
        if ((flags & FLAG_INVALIDATED) == 0) {
            final int oldPos = viewHolder.getOldPosition();
            final int pos = viewHolder.getAdapterPosition();
            if (oldPos != NO_POSITION && pos != NO_POSITION && oldPos != pos) {
                flags |= FLAG_MOVED;
            }
        }
        return flags;
    }

    /**
     * Called when there are pending animations waiting to be started. This state
     * is governed by the return values from
     * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateAppearance()},
     * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateChange()}
     * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animatePersistence()}, and
     * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateDisappearance()}, which inform the RecyclerView that the ItemAnimator wants to be
     * called later to start the associated animations. runPendingAnimations() will be scheduled
     * to be run on the next frame.
     */
    public abstract void runPendingAnimations();

    /**
     * Method called when an animation on a view should be ended immediately.
     * This could happen when other events, like scrolling, occur, so that
     * animating views can be quickly put into their proper end locations.
     * Implementations should ensure that any animations running on the item
     * are canceled and affected properties are set to their end values.
     * Also, {@link #dispatchAnimationFinished(ViewHolder)} should be called for each finished
     * animation since the animations are effectively done when this method is called.
     *
     * @param item The item for which an animation should be stopped.
     */
    public abstract void endAnimation(ViewHolder item);

    /**
     * Method called when all item animations should be ended immediately.
     * This could happen when other events, like scrolling, occur, so that
     * animating views can be quickly put into their proper end locations.
     * Implementations should ensure that any animations running on any items
     * are canceled and affected properties are set to their end values.
     * Also, {@link #dispatchAnimationFinished(ViewHolder)} should be called for each finished
     * animation since the animations are effectively done when this method is called.
     */
    public abstract void endAnimations();

    /**
     * Method which returns whether there are any item animations currently running.
     * This method can be used to determine whether to delay other actions until
     * animations end.
     *
     * @return true if there are any item animations currently running, false otherwise.
     */
    public abstract boolean isRunning();

    /**
     * Method to be called by subclasses when an animation is finished.
     * <p>
     * For each call RecyclerView makes to
     * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateAppearance()},
     * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animatePersistence()}, or
     * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateDisappearance()}, there
     * should
     * be a matching {@link #dispatchAnimationFinished(ViewHolder)} call by the subclass.
     * <p>
     * For {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateChange()}, subclass should call this method for both the <code>oldHolder</code>
     * and <code>newHolder</code>  (if they are not the same instance).
     *
     * @param viewHolder The ViewHolder whose animation is finished.
     * @see #onAnimationFinished(ViewHolder)
     */
    public final void dispatchAnimationFinished(ViewHolder viewHolder) {
        onAnimationFinished(viewHolder);
        if (mListener != null) {
            mListener.onAnimationFinished(viewHolder);
        }
    }

    /**
     * Called after {@link #dispatchAnimationFinished(ViewHolder)} is called by the
     * ItemAnimator.
     *
     * @param viewHolder The ViewHolder whose animation is finished. There might still be other
     *                   animations running on this ViewHolder.
     * @see #dispatchAnimationFinished(ViewHolder)
     */
    public void onAnimationFinished(ViewHolder viewHolder) {
    }

    /**
     * Method to be called by subclasses when an animation is started.
     * <p>
     * For each call RecyclerView makes to
     * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateAppearance()},
     * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animatePersistence()}, or
     * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateDisappearance()}, there should be a matching
     * {@link #dispatchAnimationStarted(ViewHolder)} call by the subclass.
     * <p>
     * For {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateChange()}, subclass should call this method for both the <code>oldHolder</code>
     * and <code>newHolder</code> (if they are not the same instance).
     * <p>
     * If your ItemAnimator decides not to animate a ViewHolder, it should call
     * {@link #dispatchAnimationFinished(ViewHolder)} <b>without</b> calling
     * {@link #dispatchAnimationStarted(ViewHolder)}.
     *
     * @param viewHolder The ViewHolder whose animation is starting.
     * @see #onAnimationStarted(ViewHolder)
     */
    public final void dispatchAnimationStarted(ViewHolder viewHolder) {
        onAnimationStarted(viewHolder);
    }

    /**
     * Called when a new animation is started on the given ViewHolder.
     *
     * @param viewHolder The ViewHolder which started animating. Note that the ViewHolder
     *                   might already be animating and this might be another animation.
     * @see #dispatchAnimationStarted(ViewHolder)
     */
    public void onAnimationStarted(ViewHolder viewHolder) {

    }

    /**
     * Like {@link #isRunning()}, this method returns whether there are any item
     * animations currently running. Additionally, the listener passed in will be called
     * when there are no item animations running, either immediately (before the method
     * returns) if no animations are currently running, or when the currently running
     * animations are {@link #dispatchAnimationsFinished() finished}.
     *
     * <p>Note that the listener is transient - it is either called immediately and not
     * stored at all, or stored only until it is called when running animations
     * are finished sometime later.</p>
     *
     * @param listener A listener to be called immediately if no animations are running
     * or later when currently-running animations have finished. A null listener is
     * equivalent to calling {@link #isRunning()}.
     * @return true if there are any item animations currently running, false otherwise.
     */
    public final boolean isRunning(ItemAnimatorFinishedListener listener) {
        boolean running = isRunning();
        if (listener != null) {
            if (!running) {
                listener.onAnimationsFinished();
            } else {
                mFinishedListeners.add(listener);
            }
        }
        return running;
    }

    /**
     * When an item is changed, ItemAnimator can decide whether it wants to re-use
     * the same ViewHolder for animations or RecyclerView should create a copy of the
     * item and ItemAnimator will use both to run the animation (e.g. cross-fade).
     * <p>
     * Note that this method will only be called if the {@link ViewHolder} still has the same
     * type ({@link Adapter#getItemViewType(int)}). Otherwise, ItemAnimator will always receive
     * both {@link ViewHolder}s in the
     * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)} method.
     * <p>
     * If your application is using change payloads, you can override
     * {@link #canReuseUpdatedViewHolder(ViewHolder, List)} to decide based on payloads.
     *
     * @param viewHolder The ViewHolder which represents the changed item's old content.
     *
     * @return True if RecyclerView should just rebind to the same ViewHolder or false if
     *         RecyclerView should create a new ViewHolder and pass this ViewHolder to the
     *         ItemAnimator to animate. Default implementation returns <code>true</code>.
     *
     * @see #canReuseUpdatedViewHolder(ViewHolder, List)
     */
    public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder) {
        return true;
    }

    /**
     * When an item is changed, ItemAnimator can decide whether it wants to re-use
     * the same ViewHolder for animations or RecyclerView should create a copy of the
     * item and ItemAnimator will use both to run the animation (e.g. cross-fade).
     * <p>
     * Note that this method will only be called if the {@link ViewHolder} still has the same
     * type ({@link Adapter#getItemViewType(int)}). Otherwise, ItemAnimator will always receive
     * both {@link ViewHolder}s in the
     * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)} method.
     *
     * @param viewHolder The ViewHolder which represents the changed item's old content.
     * @param payloads A non-null list of merged payloads that were sent with change
     *                 notifications. Can be empty if the adapter is invalidated via
     *                 {@link RecyclerView.Adapter#notifyDataSetChanged()}. The same list of
     *                 payloads will be passed into
     *                 {@link RecyclerView.Adapter#onBindViewHolder(ViewHolder, int, List)}
     *                 method <b>if</b> this method returns <code>true</code>.
     *
     * @return True if RecyclerView should just rebind to the same ViewHolder or false if
     *         RecyclerView should create a new ViewHolder and pass this ViewHolder to the
     *         ItemAnimator to animate. Default implementation calls
     *         {@link #canReuseUpdatedViewHolder(ViewHolder)}.
     *
     * @see #canReuseUpdatedViewHolder(ViewHolder)
     */
    public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder,
                                             @NonNull List<Object> payloads) {
        return canReuseUpdatedViewHolder(viewHolder);
    }

    /**
     * This method should be called by ItemAnimator implementations to notify
     * any listeners that all pending and active item animations are finished.
     */
    public final void dispatchAnimationsFinished() {
        final int count = mFinishedListeners.size();
        for (int i = 0; i < count; ++i) {
            mFinishedListeners.get(i).onAnimationsFinished();
        }
        mFinishedListeners.clear();
    }

    /**
     * Returns a new {@link ItemHolderInfo} which will be used to store information about the
     * ViewHolder. This information will later be passed into <code>animate**</code> methods.
     * <p>
     * You can override this method if you want to extend {@link ItemHolderInfo} and provide
     * your own instances.
     *
     * @return A new {@link ItemHolderInfo}.
     */
    public ItemHolderInfo obtainHolderInfo() {
        return new ItemHolderInfo();
    }

    /**
     * The interface to be implemented by listeners to animation events from this
     * ItemAnimator. This is used internally and is not intended for developers to
     * create directly.
     */
    interface ItemAnimatorListener {
        void onAnimationFinished(ViewHolder item);
    }

    /**
     * This interface is used to inform listeners when all pending or running animations
     * in an ItemAnimator are finished. This can be used, for example, to delay an action
     * in a data set until currently-running animations are complete.
     *
     * @see #isRunning(ItemAnimatorFinishedListener)
     */
    public interface ItemAnimatorFinishedListener {
        /**
         * Notifies when all pending or running animations in an ItemAnimator are finished.
         */
        void onAnimationsFinished();
    }
}
