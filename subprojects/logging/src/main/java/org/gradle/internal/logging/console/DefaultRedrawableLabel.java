/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.api.Action;
import org.gradle.internal.logging.text.Span;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultRedrawableLabel implements RedrawableLabel {
    private final Cursor writePos;  // Relative coordination system
    private final AnsiExecutor ansiExecutor;
    private List<Span> spans = Collections.EMPTY_LIST;
    private List<Span> writtenSpans = Collections.EMPTY_LIST;
    private int absolutePositionRow = 0;  // Absolute coordination system
    private int previousWriteRow = absolutePositionRow;
    private boolean isVisible = true;
    private boolean previousVisibility = isVisible;

    DefaultRedrawableLabel(AnsiExecutor ansiExecutor, Cursor writePos) {
        this.ansiExecutor = ansiExecutor;
        this.writePos = writePos;
    }

    @Override
    public void setText(String text) {
        setText(new Span(text));
    }

    @Override
    public void setText(List<Span> spans) {
        this.spans = spans;
    }

    @Override
    public void setText(Span... spans) {
        setText(Arrays.asList(spans));
    }

    public Cursor getWritePosition() {
        return writePos;
    }

    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public boolean isOverlappingWith(Cursor cursor) {
        return cursor.row == writePos.row && writePos.col > cursor.col;
    }

    @Override
    public void redraw() {
        if (writePos.row < 0) {
            // Does not need to be redrawn if component is out of bound
            return;
        }

        if (!isVisible && previousVisibility) {
            if (previousWriteRow == absolutePositionRow && writtenSpans.equals(Collections.EMPTY_LIST)) {
                // Does not need to be redrawn
                return;
            }

            ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
                @Override
                public void execute(AnsiContext ansi) {
                    ansi.eraseAll();
                }
            });

            writtenSpans = Collections.EMPTY_LIST;
        }

        if (isVisible) {
            if (previousWriteRow == absolutePositionRow && writtenSpans.equals(spans)) {
                // Does not need to be redrawn
                return;
            }

            final int writtenTextLength = writePos.col;

            writePos.col = 0;
            ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
                @Override
                public void execute(AnsiContext ansi) {
                    int textLength = 0;
                    for (Span span : spans) {
                        ansi.withStyle(span.getStyle(), writeText(span.getText()));

                        textLength += span.getText().length();
                    }

                    if (previousWriteRow != absolutePositionRow
                        || (previousWriteRow == absolutePositionRow && textLength < writtenTextLength)) {
                        ansi.eraseForward();
                    }
                }
            });

            writtenSpans = spans;
            previousWriteRow = absolutePositionRow;
        }
    }

    private static Action<AnsiContext> writeText(final String text) {
        return new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                ansi.a(text);
            }
        };
    }

    // Only for relative positioning
    public void newLineAdjustment() {
        writePos.row++;
    }

    // According to absolute positioning
    public void scrollBy(int rows) {
        writePos.row -= rows;
        absolutePositionRow += rows;
    }

    // According to absolute positioning
    public void scrollUpBy(int rows) {
        scrollBy(-rows);
    }

    // According to absolute positioning
    public void scrollDownBy(int rows) {
        scrollBy(rows);
    }
}