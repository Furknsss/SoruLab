# Test reader and S Pen improvements
Based on latest PR #50 commit 2387a64, not main.

- Measured phone/tablet page layout: narrower paper margins, 16dp packing gap, balanced writing areas across every question, including the final one.
- Eraser uses a real eraser glyph instead of AutoFixOff. Toolbar primary controls have 44dp touch targets.
- S Pen primary/secondary hardware buttons select temporary erasing for each input segment; releasing restores the selected pen without changing its toolbar selection. A mid-contact transition commits each tool separately.
- Pixel erasing replays a Clear path over saved ink and preserves the untouched remainder of a stroke.
- Test builder: wider responsive dialog and more compact spacing.
- Existing question/image snapshots and per-page ink keys retained.

Android source: https://developer.android.com/reference/android/view/MotionEvent
Hardware button support requires the device to deliver stylus button events to the app. Physical Samsung S Pen testing has not been performed here.

Long-question follow-up: full cropped images now retain their reading width and extend into vertical scrolling. Disk and base64 image decoding use a reading-width/pixel-budget policy; long narrow scans are not reduced solely because of their height. Margin scans use a row buffer rather than a second full bitmap-sized integer array. Regression coverage checks access to the final answer and thin marks on the edges of large scans.
