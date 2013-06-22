package se.emilsjolander.flipview;

import java.util.LinkedList;
import java.util.Queue;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.Scroller;

public class FlipView extends FrameLayout {

	public interface OnFlipListener {
		public void onFlippedToPage(FlipView v, int position, long id);
	}

	public interface OnOverFlipListener {
		public void onOverFlip(FlipView v, OverFlipMode mode,
				boolean overFlippingPrevious, float overFlipDistance,
				float flipDistancePerPage);
	}

	// wrapper class used when keeping track of active views
	static class Page {
		int position;
		View view;

		public Page(int position, View view) {
			this.position = position;
			this.view = view;
		}
	}

	private static final int PEAK_ANIM_DURATION = 600;// in ms
	private static final int MAX_SINGLE_PAGE_FLIP_ANIM_DURATION = 300;// in ms
	private static final int FLIP_DISTANCE_PER_PAGE = 180; // for normalizing
															// width/height
	private static final int MAX_SHADOW_ALPHA = 180;// out of 255
	private static final int MAX_SHADE_ALPHA = 130;// out of 255
	private static final int MAX_SHINE_ALPHA = 100;// out of 255
	private static final int INVALID_POINTER = -1;// value for no pointer
	private static final int VERTICAL_FLIP = 0;// constant used by the
												// attributes
	@SuppressWarnings("unused")
	private static final int HORIZONTAL_FLIP = 1;// constant used by the
													// attributes

	private DataSetObserver dataSetObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			dataSetChanged();
		}

		@Override
		public void onInvalidated() {
			dataSetInvalidated();
		}

	};

	private Scroller mScroller;
	private final Interpolator flipInterpolator = new DecelerateInterpolator();
	private ValueAnimator mPeakAnim;
	private TimeInterpolator mPeakInterpolator = new AccelerateDecelerateInterpolator();

	private boolean mIsFlippingVertically = true;
	private boolean mIsFlipping;
	private boolean mIsUnableToFlip;
	private boolean mIsFlippingEnabled = true;
	private boolean mLastTouchAllowed = true;
	private int mTouchSlop;
	private boolean mIsOverFlipping;

	// keep track of pointer
	private float mLastX = -1;
	private float mLastY = -1;
	private int mActivePointerId = INVALID_POINTER;

	// velocity stuff
	private VelocityTracker mVelocityTracker;
	private int mMinimumVelocity;
	private int mMaximumVelocity;

	// views get recycled after they have been pushed out of the active queue
	private Recycler mRecycler = new Recycler();

	// holds all views that are currently in use, hold max of 3 views as of now
	private Queue<Page> mActivePageQueue = new LinkedList<FlipView.Page>();

	private ListAdapter mAdapter;
	private int mPageCount = 0;

	private OnFlipListener mOnFlipListener;
	private OnOverFlipListener mOnOverFlipListener;

	private float mFlipDistance = 0;
	private int mCurrentPage = 0;
	private long mCurrentPageId = 0;

	private OverFlipMode mOverFlipMode;
	private OverFlipper mOverFlipper;

	// clipping rects
	private Rect mTopRect = new Rect();
	private Rect mBottomRect = new Rect();
	private Rect mRightRect = new Rect();
	private Rect mLeftRect = new Rect();

	// used for transforming the canvas
	private Camera mCamera = new Camera();
	private Matrix mMatrix = new Matrix();

	// paints drawn above views when flipping
	private Paint mShadowPaint = new Paint();
	private Paint mShadePaint = new Paint();
	private Paint mShinePaint = new Paint();

	public FlipView(Context context) {
		this(context, null);
	}

	public FlipView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FlipView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.FlipView);

		// 0 is vertical, 1 is horizontal
		mIsFlippingVertically = a.getInt(R.styleable.FlipView_orientation,
				VERTICAL_FLIP) == VERTICAL_FLIP;

		setOverFlipMode(OverFlipMode.values()[a.getInt(
				R.styleable.FlipView_overFlipMode, 0)]);

		a.recycle();

		init();
	}

	private void init() {
		final Context context = getContext();
		mScroller = new Scroller(context, flipInterpolator);
		final ViewConfiguration configuration = ViewConfiguration.get(context);
		mTouchSlop = configuration.getScaledPagingTouchSlop();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

		mShadowPaint.setColor(Color.BLACK);
		mShadowPaint.setStyle(Style.FILL);
		mShadePaint.setColor(Color.BLACK);
		mShadePaint.setStyle(Style.FILL);
		mShinePaint.setColor(Color.WHITE);
		mShinePaint.setStyle(Style.FILL);
	}

	private void dataSetChanged() {
		final int currentPage = mCurrentPage;

		// if the adapter has stable ids, try to keep the page currently on
		// stable.
		if (mAdapter.hasStableIds()) {
			mCurrentPage = getNewPositionOfCurrentPage();
		}
		mCurrentPageId = mAdapter.getItemId(mCurrentPage);

		mPageCount = mAdapter.getCount();
		removeAllViews();
		mActivePageQueue.clear();
		mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
		if (mCurrentPage != currentPage) {
			flipTo(mCurrentPage);
		}
		addView(viewForPage(mCurrentPage));
	}

	private int getNewPositionOfCurrentPage() {
		// check if id is on same position, this is because it will
		// often be that and this way you do not need to iterate the whole
		// dataset. If it is the same position, you are done.
		if (mCurrentPageId == mAdapter.getItemId(mCurrentPage)) {
			return mCurrentPage;
		}

		// iterate the dataset and look for the correct id. If it
		// exists, set that position as the current position.
		for (int i = 0; i < mAdapter.getCount(); i++) {
			if (mCurrentPageId == mAdapter.getItemId(i)) {
				return i;
			}
		}

		// Id no longer is dataset, keep current page
		return mCurrentPage;
	}

	private void dataSetInvalidated() {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(dataSetObserver);
			mAdapter = null;
		}
		mRecycler = new Recycler();
		removeAllViews();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = getDefaultSize(0, widthMeasureSpec);
		int height = getDefaultSize(0, heightMeasureSpec);

		measureChildren(widthMeasureSpec, heightMeasureSpec);

		setMeasuredDimension(width, height);
	}

	@Override
	protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
		int width = getDefaultSize(0, widthMeasureSpec);
		int height = getDefaultSize(0, heightMeasureSpec);

		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width,
				MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height,
				MeasureSpec.EXACTLY);
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			measureChild(child, childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec,
			int parentHeightMeasureSpec) {
		child.measure(parentWidthMeasureSpec, parentHeightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		layoutChildren();

		mTopRect.top = 0;
		mTopRect.left = 0;
		mTopRect.right = getWidth();
		mTopRect.bottom = getHeight() / 2;

		mBottomRect.top = getHeight() / 2;
		mBottomRect.left = 0;
		mBottomRect.right = getWidth();
		mBottomRect.bottom = getHeight();

		mLeftRect.top = 0;
		mLeftRect.left = 0;
		mLeftRect.right = getWidth() / 2;
		mLeftRect.bottom = getHeight();

		mRightRect.top = 0;
		mRightRect.left = getWidth() / 2;
		mRightRect.right = getWidth();
		mRightRect.bottom = getHeight();
	}

	private void layoutChildren() {
		final int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			final View child = getChildAt(i);
			layoutChild(child);
		}
	}

	private void layoutChild(View child) {
		child.layout(0, 0, getWidth(), getHeight());
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {

		if (!mIsFlippingEnabled) {
			return false;
		}

		final int action = ev.getAction() & MotionEvent.ACTION_MASK;

		if (action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_UP) {
			mIsFlipping = false;
			mIsUnableToFlip = false;
			mActivePointerId = INVALID_POINTER;
			if (mVelocityTracker != null) {
				mVelocityTracker.recycle();
				mVelocityTracker = null;
			}
			return false;
		}

		if (action != MotionEvent.ACTION_DOWN) {
			if (mIsFlipping) {
				return true;
			} else if (mIsUnableToFlip) {
				return false;
			}
		}

		switch (action) {
		case MotionEvent.ACTION_MOVE:
			final int activePointerId = mActivePointerId;
			if (activePointerId == INVALID_POINTER) {
				break;
			}

			final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
					activePointerId);
			if (pointerIndex == -1) {
				mActivePointerId = INVALID_POINTER;
				break;
			}

			final float x = MotionEventCompat.getX(ev, pointerIndex);
			final float dx = x - mLastX;
			final float xDiff = Math.abs(dx);
			final float y = MotionEventCompat.getY(ev, pointerIndex);
			final float dy = y - mLastY;
			final float yDiff = Math.abs(dy);

			if ((mIsFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff)
					|| (!mIsFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff)) {
				mIsFlipping = true;
				mLastX = x;
				mLastY = y;
			} else if ((mIsFlippingVertically && xDiff > mTouchSlop)
					|| (!mIsFlippingVertically && yDiff > mTouchSlop)) {
				mIsUnableToFlip = true;
			}
			break;

		case MotionEvent.ACTION_DOWN:
			mActivePointerId = ev.getAction()
					& MotionEvent.ACTION_POINTER_INDEX_MASK;
			mLastX = MotionEventCompat.getX(ev, mActivePointerId);
			mLastY = MotionEventCompat.getY(ev, mActivePointerId);

			mIsFlipping = !mScroller.isFinished() | mPeakAnim != null;
			mIsUnableToFlip = false;
			mLastTouchAllowed = true;

			break;
		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			break;
		}

		if (!mIsFlipping) {
			trackVelocity(ev);
		}

		return mIsFlipping;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		if (!mIsFlippingEnabled || !mIsFlipping && !mLastTouchAllowed) {
			return false;
		}

		final int action = ev.getAction();

		if (action == MotionEvent.ACTION_UP
				|| action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_OUTSIDE) {
			mLastTouchAllowed = false;
		} else {
			mLastTouchAllowed = true;
		}

		trackVelocity(ev);

		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:

			// start flipping emediettly if interrupting some sort of animation
			if (endScroll() || endPeak()) {
				mIsFlipping = true;
			}

			// Remember where the motion event started
			mLastX = ev.getX();
			mLastY = ev.getY();
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			break;
		case MotionEvent.ACTION_MOVE:
			if (!mIsFlipping) {
				final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
						mActivePointerId);
				if (pointerIndex == -1) {
					mActivePointerId = INVALID_POINTER;
					break;
				}
				final float x = MotionEventCompat.getX(ev, pointerIndex);
				final float xDiff = Math.abs(x - mLastX);
				final float y = MotionEventCompat.getY(ev, pointerIndex);
				final float yDiff = Math.abs(y - mLastY);
				if ((mIsFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff)
						|| (!mIsFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff)) {
					mIsFlipping = true;
					mLastX = x;
					mLastY = y;
				}
			}
			if (mIsFlipping) {
				// Scroll to follow the motion event
				final int activePointerIndex = MotionEventCompat
						.findPointerIndex(ev, mActivePointerId);
				if (activePointerIndex == -1) {
					mActivePointerId = INVALID_POINTER;
					break;
				}
				final float x = MotionEventCompat.getX(ev, activePointerIndex);
				final float deltaX = mLastX - x;
				final float y = MotionEventCompat.getY(ev, activePointerIndex);
				final float deltaY = mLastY - y;
				mLastX = x;
				mLastY = y;

				float deltaFlipDistance = 0;
				if (mIsFlippingVertically) {
					deltaFlipDistance = deltaY;
				} else {
					deltaFlipDistance = deltaX;
				}

				deltaFlipDistance /= ((isFlippingVertically() ? getHeight()
						: getWidth()) / FLIP_DISTANCE_PER_PAGE);
				mFlipDistance += deltaFlipDistance;

				final int minFlipDistance = 0;
				final int maxFlipDistance = (mPageCount - 1)
						* FLIP_DISTANCE_PER_PAGE;
				final boolean isOverFlipping = mFlipDistance < minFlipDistance
						|| mFlipDistance > maxFlipDistance;
				if (isOverFlipping) {
					mIsOverFlipping = true;
					mFlipDistance = mOverFlipper.calculate(mFlipDistance,
							minFlipDistance, maxFlipDistance);
					if (mOnOverFlipListener != null) {
						float overFlip = mOverFlipper.getTotalOverFlip();
						mOnOverFlipListener.onOverFlip(this, mOverFlipMode,
								overFlip < 0, Math.abs(overFlip), FLIP_DISTANCE_PER_PAGE);
					}
				} else if (mIsOverFlipping) {
					mIsOverFlipping = false;
					if (mOnOverFlipListener != null) {
						// TODO in the future should only notify flip distance 0
						// on the correct edge (previous/next)
						mOnOverFlipListener.onOverFlip(this, mOverFlipMode,
								false, 0, FLIP_DISTANCE_PER_PAGE);
						mOnOverFlipListener.onOverFlip(this, mOverFlipMode,
								true, 0, FLIP_DISTANCE_PER_PAGE);
					}
				}

				invalidate();
			}
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (mIsFlipping) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

				int velocity = 0;
				if (isFlippingVertically()) {
					velocity = (int) VelocityTrackerCompat.getYVelocity(
							velocityTracker, mActivePointerId);
				} else {
					velocity = (int) VelocityTrackerCompat.getXVelocity(
							velocityTracker, mActivePointerId);
				}
				smoothFlipTo(getNextPage(velocity));

				mActivePointerId = INVALID_POINTER;
				endFlip();

				mOverFlipper.overFlipEnded();
			}
			break;
		case MotionEventCompat.ACTION_POINTER_DOWN: {
			final int index = MotionEventCompat.getActionIndex(ev);
			final float x = MotionEventCompat.getX(ev, index);
			final float y = MotionEventCompat.getY(ev, index);
			mLastX = x;
			mLastY = y;
			mActivePointerId = MotionEventCompat.getPointerId(ev, index);
			break;
		}
		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			final int index = MotionEventCompat.findPointerIndex(ev,
					mActivePointerId);
			final float x = MotionEventCompat.getX(ev, index);
			final float y = MotionEventCompat.getY(ev, index);
			mLastX = x;
			mLastY = y;
			break;
		}
		if (mActivePointerId == INVALID_POINTER) {
			mLastTouchAllowed = false;
		}
		return true;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {

		if (mPageCount < 1) {
			return;
		}

		boolean needsInvalidate = false;

		if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
			mFlipDistance = mScroller.getCurrY();
			needsInvalidate = true;
		}

		if (mIsFlipping || !mScroller.isFinished() || mPeakAnim != null) {
			drawPreviousHalf(canvas);
			drawNextHalf(canvas);
			drawFlippingHalf(canvas);
		} else {
			endScroll();
			final int currentPage = getCurrentPageFloor();
			if (mCurrentPage != currentPage) {
				postRemoveView(getChildAt(0));
			}
			final View v = viewForPage(currentPage);
			if (mCurrentPage != currentPage) {
				postAddView(v);
				postFlippedToPage(currentPage);
				mCurrentPage = currentPage;
				mCurrentPageId = mAdapter.getItemId(mCurrentPage);
			}
			setDrawWithLayer(v, false);
			v.draw(canvas);
		}

		// if overflip is GLOW mode and the edge effects needed drawing, make
		// sure to invalidate
		needsInvalidate |= mOverFlipper.draw(canvas);

		if (needsInvalidate) {
			// always invalidate whole screen as it is needed 99% of the time.
			// This is because of the shadows and shines put on the non-flipping
			// pages
			invalidate();
		}
	}

	/**
	 * draw top/left half
	 * 
	 * @param canvas
	 */
	private void drawPreviousHalf(Canvas canvas) {
		final View v = viewForPage(getCurrentPageFloor());

		canvas.save();
		canvas.clipRect(isFlippingVertically() ? mTopRect : mLeftRect);

		// if the view does not exist, skip drawing it
		if (v != null) {
			setDrawWithLayer(v, true);
			v.draw(canvas);
		}

		drawPreviousShadow(canvas);
		canvas.restore();
	}

	/**
	 * draw top/left half shadow
	 * 
	 * @param canvas
	 */
	private void drawPreviousShadow(Canvas canvas) {
		final float degreesFlipped = getDegreesFlipped();
		if (degreesFlipped > 90) {
			final int alpha = (int) (((degreesFlipped - 90) / 90f) * MAX_SHADOW_ALPHA);
			mShadowPaint.setAlpha(alpha);
			canvas.drawPaint(mShadowPaint);
		}
	}

	/**
	 * draw bottom/right half
	 * 
	 * @param canvas
	 */
	private void drawNextHalf(Canvas canvas) {
		final View v = viewForPage(getCurrentPageCeil());

		canvas.save();
		canvas.clipRect(isFlippingVertically() ? mBottomRect : mRightRect);

		// if the view does not exist, skip drawing it
		if (v != null) {
			setDrawWithLayer(v, true);
			v.draw(canvas);
		}

		drawNextShadow(canvas);
		canvas.restore();
	}

	/**
	 * draw bottom/right half shadow
	 * 
	 * @param canvas
	 */
	private void drawNextShadow(Canvas canvas) {
		final float degreesFlipped = getDegreesFlipped();
		if (degreesFlipped < 90) {
			final int alpha = (int) ((Math.abs(degreesFlipped - 90) / 90f) * MAX_SHADOW_ALPHA);
			mShadowPaint.setAlpha(alpha);
			canvas.drawPaint(mShadowPaint);
		}
	}

	private void drawFlippingHalf(Canvas canvas) {
		final View v = viewForPage(getCurrentPageRound());

		setDrawWithLayer(v, true);
		final float degreesFlipped = getDegreesFlipped();
		canvas.save();
		mCamera.save();

		if (degreesFlipped > 90) {
			canvas.clipRect(isFlippingVertically() ? mTopRect : mLeftRect);
			if (mIsFlippingVertically) {
				mCamera.rotateX(degreesFlipped - 180);
			} else {
				mCamera.rotateY(180 - degreesFlipped);
			}
		} else {
			canvas.clipRect(isFlippingVertically() ? mBottomRect : mRightRect);
			if (mIsFlippingVertically) {
				mCamera.rotateX(degreesFlipped);
			} else {
				mCamera.rotateY(-degreesFlipped);
			}
		}

		mCamera.getMatrix(mMatrix);

		positionMatrix();
		canvas.concat(mMatrix);

		v.draw(canvas);

		drawFlippingShadeShine(canvas);

		mCamera.restore();
		canvas.restore();
	}

	/**
	 * will draw a shade if flipping on the previous(top/left) half and a shine
	 * if flipping on the next(bottom/right) half
	 * 
	 * @param canvas
	 */
	private void drawFlippingShadeShine(Canvas canvas) {
		final float degreesFlipped = getDegreesFlipped();
		if (degreesFlipped < 90) {
			final int alpha = (int) ((degreesFlipped / 90f) * MAX_SHINE_ALPHA);
			mShinePaint.setAlpha(alpha);
			canvas.drawRect(isFlippingVertically() ? mBottomRect : mRightRect,
					mShinePaint);
		} else {
			final int alpha = (int) ((Math.abs(degreesFlipped - 180) / 90f) * MAX_SHADE_ALPHA);
			mShadePaint.setAlpha(alpha);
			canvas.drawRect(isFlippingVertically() ? mTopRect : mLeftRect,
					mShadePaint);
		}
	}

	/**
	 * Enable a hardware layer for the view.
	 * 
	 * @param v
	 * @param drawWithLayer
	 */
	private void setDrawWithLayer(View v, boolean drawWithLayer) {
		if (v.getLayerType() != LAYER_TYPE_HARDWARE && drawWithLayer) {
			v.setLayerType(LAYER_TYPE_HARDWARE, null);
		} else if (v.getLayerType() != LAYER_TYPE_NONE && !drawWithLayer) {
			v.setLayerType(LAYER_TYPE_NONE, null);
		}
	}

	private void positionMatrix() {
		mMatrix.preScale(0.25f, 0.25f);
		mMatrix.postScale(4.0f, 4.0f);
		mMatrix.preTranslate(-getWidth() / 2, -getHeight() / 2);
		mMatrix.postTranslate(getWidth() / 2, getHeight() / 2);
	}

	private float getDegreesFlipped() {
		float localFlipDistance = mFlipDistance % FLIP_DISTANCE_PER_PAGE;

		// fix for negative modulo. always want a positve flip degree
		if (localFlipDistance < 0) {
			localFlipDistance += FLIP_DISTANCE_PER_PAGE;
		}

		return (localFlipDistance / FLIP_DISTANCE_PER_PAGE) * 180;
	}

	private View viewForPage(int page) {

		// if the requested page is outside of the adapters scope, return null.
		// This should only happen when over flipping with mode RUBBER_BAND
		if (page < 0 || page >= mPageCount) {
			return null;
		}

		final int viewType = mAdapter.getItemViewType(page);

		// check if view needed is in active views, if so order to front and
		// return this view
		View v = getActiveView(page);
		if (v != null) {
			return v;
		}

		// get(and remove) a convertview with correct viewtype from recycled
		// views
		v = mRecycler.getScrapView(page, viewType);

		// pass that view (can be null) into adapter to fill the view
		v = mAdapter.getView(page, v, this);

		// insert that view into active views pushing the least used active view
		// into recycled views
		addToActiveView(v, page, viewType);

		// measure and layout view
		measureAndLayoutChild(v);

		// return view
		return v;
	}

	private void measureAndLayoutChild(View v) {
		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getWidth(),
				MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(getHeight(),
				MeasureSpec.EXACTLY);
		measureChild(v, childWidthMeasureSpec, childHeightMeasureSpec);
		layoutChild(v);
	}

	private void addToActiveView(View v, int page, int viewType) {
		mActivePageQueue.add(new Page(page, v));
		if (mActivePageQueue.size() > 3) {
			final View view = mActivePageQueue.remove().view;
			mRecycler.addScrapView(view, page, viewType);
		}
	}

	private View getActiveView(int position) {
		Page page = null;
		for (Page p : mActivePageQueue) {
			if (p.position == position) {
				page = p;
			}
		}
		if (page != null) {
			mActivePageQueue.remove(page);
			mActivePageQueue.add(page);
			return page.view;
		}
		return null;
	}

	private void postAddView(final View v) {
		post(new Runnable() {

			@Override
			public void run() {
				addView(v);
			}
		});
	}

	private void postRemoveView(final View v) {
		post(new Runnable() {

			@Override
			public void run() {
				removeView(v);
			}
		});
	}

	private void postFlippedToPage(final int page) {
		post(new Runnable() {

			@Override
			public void run() {
				if (mOnFlipListener != null) {
					mOnFlipListener.onFlippedToPage(FlipView.this, page,
							mAdapter.getItemId(page));
				}
			}
		});
	}

	private void onSecondaryPointerUp(MotionEvent ev) {
		final int pointerIndex = MotionEventCompat.getActionIndex(ev);
		final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
		if (pointerId == mActivePointerId) {
			// This was our active pointer going up. Choose a new
			// active pointer and adjust accordingly.
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mLastX = MotionEventCompat.getX(ev, newPointerIndex);
			mActivePointerId = MotionEventCompat.getPointerId(ev,
					newPointerIndex);
			if (mVelocityTracker != null) {
				mVelocityTracker.clear();
			}
		}
	}

	/**
	 * 
	 * @param deltaFlipDistance
	 *            The distance to flip.
	 * @return The duration for a flip, bigger deltaFlipDistance = longer
	 *         duration. The increase if duration gets smaller for bigger values
	 *         of deltaFlipDistance.
	 */
	private int getFlipDuration(int deltaFlipDistance) {
		float distance = Math.abs(deltaFlipDistance);
		return (int) (MAX_SINGLE_PAGE_FLIP_ANIM_DURATION * Math.sqrt(distance
				/ FLIP_DISTANCE_PER_PAGE));
	}

	/**
	 * 
	 * @param velocity
	 * @return the page you should "land" on
	 */
	private int getNextPage(int velocity) {
		int nextPage;
		if (velocity > mMinimumVelocity) {
			nextPage = getCurrentPageFloor();
		} else if (velocity < -mMinimumVelocity) {
			nextPage = getCurrentPageCeil();
		} else {
			nextPage = getCurrentPageRound();
		}
		return Math.min(Math.max(nextPage, 0), mPageCount - 1);
	}

	private int getCurrentPageRound() {
		return Math.round(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
	}

	private int getCurrentPageFloor() {
		return (int) Math.floor(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
	}

	private int getCurrentPageCeil() {
		return (int) Math.ceil(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
	}

	/**
	 * 
	 * @return true if ended a flip
	 */
	private boolean endFlip() {
		final boolean wasflipping = mIsFlipping;
		mIsFlipping = false;
		mIsUnableToFlip = false;
		mLastTouchAllowed = false;

		if (mVelocityTracker != null) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
		return wasflipping;
	}

	/**
	 * 
	 * @return true if ended a scroll
	 */
	private boolean endScroll() {
		final boolean wasScrolling = !mScroller.isFinished();
		mScroller.abortAnimation();
		return wasScrolling;
	}

	/**
	 * 
	 * @return true if ended a peak
	 */
	private boolean endPeak() {
		final boolean wasPeaking = mPeakAnim != null;
		if (mPeakAnim != null) {
			mPeakAnim.cancel();
			mPeakAnim = null;
		}
		return wasPeaking;
	}

	private void peak(boolean next, boolean once) {
		final float baseFlipDistance = mCurrentPage * FLIP_DISTANCE_PER_PAGE;
		if (next) {
			mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance,
					baseFlipDistance + FLIP_DISTANCE_PER_PAGE / 4);
		} else {
			mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance,
					baseFlipDistance - FLIP_DISTANCE_PER_PAGE / 4);
		}
		mPeakAnim.setInterpolator(mPeakInterpolator);
		mPeakAnim.addUpdateListener(new AnimatorUpdateListener() {

			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				mFlipDistance = (Float) animation.getAnimatedValue();
				invalidate();
			}
		});
		mPeakAnim.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				endPeak();
			}
		});
		mPeakAnim.setDuration(PEAK_ANIM_DURATION);
		mPeakAnim.setRepeatMode(ValueAnimator.REVERSE);
		mPeakAnim.setRepeatCount(once ? 1 : ValueAnimator.INFINITE);
		mPeakAnim.start();
	}

	private void trackVelocity(MotionEvent ev) {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);
	}

	/* ---------- API ---------- */

	/**
	 * 
	 * @param adapter
	 *            a regular ListAdapter, not all methods if the list adapter are
	 *            used by the flipview
	 * 
	 */
	public void setAdapter(ListAdapter adapter) {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(dataSetObserver);
			mAdapter = null;
		}
		removeAllViews();
		mActivePageQueue.clear();
		if (adapter != null) {
			mAdapter = adapter;
			mPageCount = mAdapter.getCount();
			mCurrentPageId = mAdapter.getItemId(mCurrentPage);
			mAdapter.registerDataSetObserver(dataSetObserver);
			mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
			addView(viewForPage(mCurrentPage));
		} else {
			mPageCount = 0;
		}
	}

	public ListAdapter getAdapter() {
		return mAdapter;
	}

	public int getPageCount() {
		return mPageCount;
	}

	public int getCurrentPage() {
		return mCurrentPage;
	}

	public void flipTo(int page) {
		if (page < 0 || page > mPageCount - 1) {
			throw new IllegalArgumentException("That page does not exist");
		}
		mFlipDistance = page * FLIP_DISTANCE_PER_PAGE;
		invalidate();
	}

	public void flipBy(int delta) {
		flipTo(mCurrentPage + delta);
	}

	public void smoothFlipTo(int page) {
		if (page < 0 || page > mPageCount - 1) {
			throw new IllegalArgumentException("That page does not exist");
		}
		final int start = (int) mFlipDistance;
		final int delta = page * FLIP_DISTANCE_PER_PAGE - start;

		mScroller.startScroll(0, start, 0, delta, getFlipDuration(delta));
		invalidate();
	}

	public void smoothFlipBy(int delta) {
		smoothFlipTo(mCurrentPage + delta);
	}

	/**
	 * Hint that there is a next page will do nothing if there is no next page
	 * 
	 * @param once
	 *            if true, only peak once. else peak until user interacts with
	 *            view
	 */
	public void peakNext(boolean once) {
		if (mCurrentPage < mPageCount - 1) {
			peak(true, once);
		}
	}

	/**
	 * Hint that there is a previous page will do nothing if there is no
	 * previous page
	 * 
	 * @param once
	 *            if true, only peak once. else peak until user interacts with
	 *            view
	 */
	public void peakPrevious(boolean once) {
		if (mCurrentPage > 0) {
			peak(false, once);
		}
	}

	/**
	 * 
	 * @return true if the view is flipping vertically, can only be set via xml
	 *         attribute "orientation"
	 */
	public boolean isFlippingVertically() {
		return mIsFlippingVertically;
	}

	/**
	 * The OnFlipListener will notify you when a page has been fully turned.
	 * 
	 * @param onFlipListener
	 */
	public void setOnFlipListener(OnFlipListener onFlipListener) {
		mOnFlipListener = onFlipListener;
	}

	/**
	 * The OnOverFlipListener will notify of over flipping. This is a great
	 * listener to have when implementing pull-to-refresh
	 * 
	 * @param onOverFlipListener
	 */
	public void setOnOverFlipListener(OnOverFlipListener onOverFlipListener) {
		this.mOnOverFlipListener = onOverFlipListener;
	}

	/**
	 * 
	 * @return the overflip mode of this flipview. Default is GLOW
	 */
	public OverFlipMode getOverFlipMode() {
		return mOverFlipMode;
	}

	/**
	 * Set the overflip mode of the flipview. GLOW is the standard seen in all
	 * andriod lists. RUBBER_BAND is more like iOS lists which list you flip
	 * past the first/last page but adding friction, like a rubber band.
	 * 
	 * @param overFlipMode
	 */
	public void setOverFlipMode(OverFlipMode overFlipMode) {
		this.mOverFlipMode = overFlipMode;
		mOverFlipper = OverFlipperFactory.create(this, mOverFlipMode);
	}

}
