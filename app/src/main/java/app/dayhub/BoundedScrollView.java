package app.dayhub;

import android.content.Context;
import android.widget.ScrollView;

/** A scroll view that grows with its content but never taller than a set height, then scrolls. */
public final class BoundedScrollView extends ScrollView {
    private int maxHeight = Integer.MAX_VALUE;

    public BoundedScrollView(Context c) {
        super(c);
    }

    public void setMaxHeight(int px) {
        if (px != maxHeight) {
            maxHeight = px;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }
}
