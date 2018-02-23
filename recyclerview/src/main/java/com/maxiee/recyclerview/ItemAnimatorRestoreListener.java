package com.maxiee.recyclerview;

/**
 * Internal listener that manages items after animations finish. This is how items are
 * retained (not recycled) during animations, but allowed to be recycled afterwards.
 * It depends on the contract with the ItemAnimator to call the appropriate dispatch*Finished()
 * method on the animator's listener when it is done animating any item.
 */
class ItemAnimatorRestoreListener implements ItemAnimator.ItemAnimatorListener {

    ItemAnimatorRestoreListener() {}

    @Override
    public void onAnimationFinished(ViewHolder item) {
        item.setIsRecyclable(true);
        if (item.mShadowedHolder != null && item.mShadowingHolder == null) { // old vh
            item.mShadowedHolder = null;
        }
        // always null this because an OldViewHolder can never become NewViewHolder w/o being
        // recycled.
        item.mShadowingHolder = null;
        if (!item.shouldBeKeptAsChild()) {
            if (!removeAnimatingView(item.itemView) && item.isTmpDetached()) {
                removeDetachedView(item.itemView, false);
            }
        }
    }
}
