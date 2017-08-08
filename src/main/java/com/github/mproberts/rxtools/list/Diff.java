package com.github.mproberts.rxtools.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 * Modified August 2017 by mproberts
 */
public class Diff
{
    private static final Comparator<Snake> SNAKE_COMPARATOR = new Comparator<Snake>() {
        @Override
        public int compare(Snake o1, Snake o2) {
            int cmpX = o1.x - o2.x;
            return cmpX == 0 ? o1.y - o2.y : cmpX;
        }
    };

    public static <T> List<Change> calculateDiff(final List<T> original, final List<T> updated, boolean detectMoves) {

        EqualsComparator<Integer> compare = new EqualsComparator<Integer>() {
            @Override
            public boolean isEqualTo(Integer indexOriginal, Integer indexUpdated) {
                T a = original.get(indexOriginal);
                T b = updated.get(indexUpdated);

                return a == b || (a != null && a.equals(b));
            }
        };
        final int oldSize = original.size();
        final int newSize = updated.size();
        final List<Snake> snakes = new ArrayList<>();
        // instead of a recursive implementation, we keep our own stack to avoid potential stack
        // overflow exceptions
        final List<Range> stack = new ArrayList<>();
        stack.add(new Range(0, oldSize, 0, newSize));
        final int max = oldSize + newSize + Math.abs(oldSize - newSize);
        // allocate forward and backward k-lines. K lines are diagonal lines in the matrix. (see the
        // paper for details)
        // These arrays lines keep the max reachable position for each k-line.
        final int[] forward = new int[max * 2];
        final int[] backward = new int[max * 2];
        // We pool the ranges to avoid allocations for each recursive call.
        final List<Range> rangePool = new ArrayList<>();
        while (!stack.isEmpty()) {
            final Range range = stack.remove(stack.size() - 1);
            final Snake snake = diffPartial(original, updated, compare, range.oldListStart, range.oldListEnd,
                    range.newListStart, range.newListEnd, forward, backward, max);
            if (snake != null) {
                if (snake.size > 0) {
                    snakes.add(snake);
                }
                // offset the snake to convert its coordinates from the Range's area to global
                snake.x += range.oldListStart;
                snake.y += range.newListStart;
                // add new ranges for left and right
                final Range left = rangePool.isEmpty() ? new Range() : rangePool.remove(
                        rangePool.size() - 1);
                left.oldListStart = range.oldListStart;
                left.newListStart = range.newListStart;
                if (snake.isReverse) {
                    left.oldListEnd = snake.x;
                    left.newListEnd = snake.y;
                } else {
                    if (snake.isRemove) {
                        left.oldListEnd = snake.x - 1;
                        left.newListEnd = snake.y;
                    } else {
                        left.oldListEnd = snake.x;
                        left.newListEnd = snake.y - 1;
                    }
                }
                stack.add(left);
                // re-use range for right
                //noinspection UnnecessaryLocalVariable
                final Range right = range;
                if (snake.isReverse) {
                    if (snake.isRemove) {
                        right.oldListStart = snake.x + snake.size + 1;
                        right.newListStart = snake.y + snake.size;
                    } else {
                        right.oldListStart = snake.x + snake.size;
                        right.newListStart = snake.y + snake.size + 1;
                    }
                } else {
                    right.oldListStart = snake.x + snake.size;
                    right.newListStart = snake.y + snake.size;
                }
                stack.add(right);
            } else {
                rangePool.add(range);
            }
        }
        // sort snakes
        Collections.sort(snakes, SNAKE_COMPARATOR);
        return new DiffResult<>(original, updated, snakes, forward, backward, detectMoves, compare).toChangeList();
    }

    private static <T> Snake diffPartial(List<T> original, List<T> updated, EqualsComparator<Integer> compare, int startOld, int endOld,
                                     int startNew, int endNew, int[] forward, int[] backward, int kOffset) {
        final int oldSize = endOld - startOld;
        final int newSize = endNew - startNew;
        if (endOld - startOld < 1 || endNew - startNew < 1) {
            return null;
        }
        final int delta = oldSize - newSize;
        final int dLimit = (oldSize + newSize + 1) / 2;
        Arrays.fill(forward, kOffset - dLimit - 1, kOffset + dLimit + 1, 0);
        Arrays.fill(backward, kOffset - dLimit - 1 + delta, kOffset + dLimit + 1 + delta, oldSize);
        final boolean checkInFwd = delta % 2 != 0;
        for (int d = 0; d <= dLimit; d++) {
            for (int k = -d; k <= d; k += 2) {
                // find forward path
                // we can reach k from k - 1 or k + 1. Check which one is further in the graph
                int x;
                final boolean removal;
                if (k == -d || k != d && forward[kOffset + k - 1] < forward[kOffset + k + 1]) {
                    x = forward[kOffset + k + 1];
                    removal = false;
                } else {
                    x = forward[kOffset + k - 1] + 1;
                    removal = true;
                }
                // set y based on x
                int y = x - k;
                // move diagonal as long as items match
                while (x < oldSize && y < newSize &&
                        compare.isEqualTo(startOld + x, startNew + y)) {
                    x++;
                    y++;
                }
                forward[kOffset + k] = x;
                if (checkInFwd && k >= delta - d + 1 && k <= delta + d - 1) {
                    if (forward[kOffset + k] >= backward[kOffset + k]) {
                        Snake outSnake = new Snake();
                        outSnake.x = backward[kOffset + k];
                        outSnake.y = outSnake.x - k;
                        outSnake.size = forward[kOffset + k] - backward[kOffset + k];
                        outSnake.isRemove = removal;
                        outSnake.isReverse = false;
                        return outSnake;
                    }
                }
            }
            for (int k = -d; k <= d; k += 2) {
                // find isReverse path at k + delta, in isReverse
                final int backwardK = k + delta;
                int x;
                final boolean removal;
                if (backwardK == d + delta || backwardK != -d + delta
                        && backward[kOffset + backwardK - 1] < backward[kOffset + backwardK + 1]) {
                    x = backward[kOffset + backwardK - 1];
                    removal = false;
                } else {
                    x = backward[kOffset + backwardK + 1] - 1;
                    removal = true;
                }
                // set y based on x
                int y = x - backwardK;
                // move diagonal as long as items match
                while (x > 0 && y > 0
                        && compare.isEqualTo(startOld + x - 1, startNew + y - 1)) {
                    x--;
                    y--;
                }
                backward[kOffset + backwardK] = x;
                if (!checkInFwd && k + delta >= -d && k + delta <= d) {
                    if (forward[kOffset + backwardK] >= backward[kOffset + backwardK]) {
                        Snake outSnake = new Snake();
                        outSnake.x = backward[kOffset + backwardK];
                        outSnake.y = outSnake.x - backwardK;
                        outSnake.size =
                                forward[kOffset + backwardK] - backward[kOffset + backwardK];
                        outSnake.isRemove = removal;
                        outSnake.isReverse = true;
                        return outSnake;
                    }
                }
            }
        }
        throw new IllegalStateException("DiffUtil hit an unexpected case while trying to calculate"
                + " the optimal path. Please make sure your data is not changing during the"
                + " diff calculation.");
    }

    static class Snake {
        int x;
        int y;
        int size;
        boolean isRemove;
        boolean isReverse;
    }

    static class Range {
        int oldListStart, oldListEnd;
        int newListStart, newListEnd;
        public Range() {
        }
        public Range(int oldListStart, int oldListEnd, int newListStart, int newListEnd) {
            this.oldListStart = oldListStart;
            this.oldListEnd = oldListEnd;
            this.newListStart = newListStart;
            this.newListEnd = newListEnd;
        }
    }
    /**
     * This class holds the information about the result of a
     * <p>
     * You can consume the updates in a DiffResult via
     */
    private static class DiffResult<T> {
        private static final int FLAG_MOVED = 4 << 1;
        // If this is an addition from the new list, it means the item is actually removed from an
        // earlier position and its move will be dispatched when we process the matching isRemove
        // from the old list.
        // If this is a isRemove from the old list, it means the item is actually added back to an
        // earlier index in the new list and we'll dispatch its move when we are processing that
        // addition.
        private static final int FLAG_IGNORE = FLAG_MOVED << 1;

        private static final int FLAG_OFFSET = 5;
        private static final int FLAG_MASK = (1 << FLAG_OFFSET) - 1;
        // The Myers' snakes. At this point, we only care about their diagonal sections.
        private final List<Snake> _snakes;
        // The list to keep oldItemStatuses. As we traverse old items, we assign flags to them
        // which also includes whether they were a real isRemove or a move (and its new index).
        private final int[] _oldItemStatuses;
        // The list to keep newItemStatuses. As we traverse new items, we assign flags to them
        // which also includes whether they were a real addition or a move(and its old index).
        private final int[] _newItemStatuses;
        // The callback that was given to calcualte diff method.
        private final int _oldListSize;
        private final int _newListSize;
        private final boolean _detectMoves;
        /**
         * @param snakes The list of Myers' snakes
         * @param oldItemStatuses An int[] that can be re-purposed to keep metadata
         * @param newItemStatuses An int[] that can be re-purposed to keep metadata
         * @param detectMoves True if this DiffResult will try to detect moved items
         */
        DiffResult(List<T> original, List<T> updated, List<Snake> snakes, int[] oldItemStatuses,
                   int[] newItemStatuses, boolean detectMoves, EqualsComparator<Integer> compare) {
            _snakes = snakes;
            _oldItemStatuses = oldItemStatuses;
            _newItemStatuses = newItemStatuses;
            Arrays.fill(_oldItemStatuses, 0);
            Arrays.fill(_newItemStatuses, 0);
            _oldListSize = original.size();
            _newListSize = updated.size();
            _detectMoves = detectMoves;
            addRootSnake();
            findMatchingItems(compare);
        }
        /**
         * We always add a Snake to 0/0 so that we can run loops from end to beginning and be done
         * when we run out of snakes.
         */
        private void addRootSnake() {
            Snake firstSnake = _snakes.isEmpty() ? null : _snakes.get(0);
            if (firstSnake == null || firstSnake.x != 0 || firstSnake.y != 0) {
                Snake root = new Snake();
                root.x = 0;
                root.y = 0;
                root.isRemove = false;
                root.size = 0;
                root.isReverse = false;
                _snakes.add(0, root);
            }
        }
        /**
         * This method traverses each addition / isRemove and tries to match it to a previous
         * isRemove / addition. This is how we detect move operations.
         * <p>
         * This class also flags whether an item has been changed or not.
         * <p>
         * DiffUtil does this pre-processing so that if it is running on a big list, it can be moved
         * to background thread where most of the expensive stuff will be calculated and kept in
         * the statuses maps. DiffResult uses this pre-calculated information while dispatching
         * the updates (which is probably being called on the main thread).
         */
        private void findMatchingItems(EqualsComparator<Integer> compare) {
            int posOld = _oldListSize;
            int posNew = _newListSize;
            // traverse the matrix from right bottom to 0,0.
            for (int i = _snakes.size() - 1; i >= 0; i--) {
                final Snake snake = _snakes.get(i);
                final int endX = snake.x + snake.size;
                final int endY = snake.y + snake.size;
                if (_detectMoves) {
                    while (posOld > endX) {
                        // this is a isRemove. Check remaining snakes to see if this was added before
                        findAddition(posOld, posNew, i, compare);
                        posOld--;
                    }
                    while (posNew > endY) {
                        // this is an addition. Check remaining snakes to see if this was removed
                        // before
                        findRemoval(posOld, posNew, i, compare);
                        posNew--;
                    }
                }
                for (int j = 0; j < snake.size; j++) {
                    // matching items. Check if it is changed or not
                    final int oldItemPos = snake.x + j;
                    final int newItemPos = snake.y + j;
                    _oldItemStatuses[oldItemPos] = (newItemPos << FLAG_OFFSET);
                    _newItemStatuses[newItemPos] = (oldItemPos << FLAG_OFFSET);
                }
                posOld = snake.x;
                posNew = snake.y;
            }
        }
        private void findAddition(int x, int y, int snakeIndex, EqualsComparator<Integer> compare) {
            if (_oldItemStatuses[x - 1] != 0) {
                return; // already set by a latter item
            }
            findMatchingItem(x, y, snakeIndex, compare, false);
        }
        private void findRemoval(int x, int y, int snakeIndex, EqualsComparator<Integer> compare) {
            if (_newItemStatuses[y - 1] != 0) {
                return; // already set by a latter item
            }
            findMatchingItem(x, y, snakeIndex, compare, true);
        }
        /**
         * Finds a matching item that is before the given coordinates in the matrix
         * (before : left and above).
         *
         * @param x The x position in the matrix (position in the old list)
         * @param y The y position in the matrix (position in the new list)
         * @param snakeIndex The current snake index
         * @param removal True if we are looking for a isRemove, false otherwise
         *
         * @return True if such item is found.
         */
        private boolean findMatchingItem(final int x, final int y, final int snakeIndex, EqualsComparator<Integer> compare,
                                         final boolean removal) {
            final int itemPosition;
            int curX;
            int curY;
            if (removal) {
                itemPosition = y - 1;
                curX = x;
                curY = y - 1;
            } else {
                itemPosition = x - 1;
                curX = x - 1;
                curY = y;
            }
            for (int i = snakeIndex; i >= 0; i--) {
                final Snake snake = _snakes.get(i);
                final int endX = snake.x + snake.size;
                final int endY = snake.y + snake.size;
                if (removal) {
                    // check removals for a match
                    for (int pos = curX - 1; pos >= endX; pos--) {
                        if (compare.isEqualTo(pos, itemPosition)) {
                            // found!
                            _newItemStatuses[itemPosition] = (pos << FLAG_OFFSET) | FLAG_IGNORE;
                            _oldItemStatuses[pos] = (itemPosition << FLAG_OFFSET) | FLAG_MOVED;
                            return true;
                        }
                    }
                } else {
                    // check for additions for a match
                    for (int pos = curY - 1; pos >= endY; pos--) {
                        if (compare.isEqualTo(itemPosition, pos)) {
                            // found
                            _oldItemStatuses[x - 1] = (pos << FLAG_OFFSET) | FLAG_IGNORE;
                            _newItemStatuses[pos] = ((x - 1) << FLAG_OFFSET) | FLAG_MOVED;
                            return true;
                        }
                    }
                }
                curX = snake.x;
                curY = snake.y;
            }
            return false;
        }
        /**
         * Dispatches update operations to the given Callback.
         * <p>
         * These updates are atomic such that the first update call effects every update call that
         * comes after it (the same as RecyclerView).
         */
        public List<Change> toChangeList() {
            // These are add/remove ops that are converted to moves. We track their positions until
            // their respective update operations are processed.
            final List<PostponedUpdate> postponedUpdates = new ArrayList<>();
            List<Change> changes = new ArrayList<>();
            int posOld = _oldListSize;
            int posNew = _newListSize;
            for (int snakeIndex = _snakes.size() - 1; snakeIndex >= 0; snakeIndex--) {
                final Snake snake = _snakes.get(snakeIndex);
                final int snakeSize = snake.size;
                final int endX = snake.x + snakeSize;
                final int endY = snake.y + snakeSize;
                if (endX < posOld) {
                    changes.addAll(dispatchRemovals(postponedUpdates, endX, posOld - endX, endX));
                }
                if (endY < posNew) {
                    changes.addAll(dispatchAdditions(postponedUpdates, endX, posNew - endY,
                            endY));
                }
                posOld = snake.x;
                posNew = snake.y;
            }

            return changes;
        }
        private static PostponedUpdate removePostponedUpdate(List<PostponedUpdate> updates,
                                                             int pos, boolean removal) {
            for (int i = updates.size() - 1; i >= 0; i--) {
                final PostponedUpdate update = updates.get(i);
                if (update.posInOwnerList == pos && update.removal == removal) {
                    updates.remove(i);
                    for (int j = i; j < updates.size(); j++) {
                        // offset other ops since they swapped positions
                        updates.get(j).currentPos += removal ? 1 : -1;
                    }
                    return update;
                }
            }
            return null;
        }
        private List<Change> dispatchAdditions(List<PostponedUpdate> postponedUpdates,
                                       int start, int count, int globalIndex) {
            List<Change> changes = new ArrayList<>();
            if (!_detectMoves) {
                for (int i = 0; i < count; ++i) {
                    changes.add(Change.inserted(start + i));
                }
                return changes;
            }
            for (int i = count - 1; i >= 0; i--) {
                int status = _newItemStatuses[globalIndex + i] & FLAG_MASK;
                switch (status) {
                    case 0: // real addition
                        changes.add(Change.inserted(start));
                        for (PostponedUpdate update : postponedUpdates) {
                            update.currentPos += 1;
                        }
                        break;
                    case FLAG_MOVED:
                        final int pos = _newItemStatuses[globalIndex + i] >> FLAG_OFFSET;
                        final PostponedUpdate update = removePostponedUpdate(postponedUpdates, pos,
                                true);
                        // the item was moved from that position
                        //noinspection ConstantConditions
                        changes.add(Change.moved(update.currentPos, start));
                        break;
                    case FLAG_IGNORE: // ignoring this
                        postponedUpdates.add(new PostponedUpdate(globalIndex + i, start, false));
                        break;
                    default:
                        throw new IllegalStateException(
                                "unknown flag for pos " + (globalIndex + i) + " " + Long
                                        .toBinaryString(status));
                }
            }

            return changes;
        }
        private List<Change> dispatchRemovals(List<PostponedUpdate> postponedUpdates,
                                      int start, int count, int globalIndex) {
            List<Change> changes = new ArrayList<>();
            if (!_detectMoves) {
                for (int i = 0; i < count; ++i) {
                    changes.add(Change.removed(start));
                }
                return changes;
            }
            for (int i = count - 1; i >= 0; i--) {
                final int status = _oldItemStatuses[globalIndex + i] & FLAG_MASK;
                switch (status) {
                    case 0: // real isRemove
                        changes.add(Change.removed(start + i));
                        for (PostponedUpdate update : postponedUpdates) {
                            update.currentPos -= 1;
                        }
                        break;
                    case FLAG_MOVED:
                        final int pos = _oldItemStatuses[globalIndex + i] >> FLAG_OFFSET;
                        final PostponedUpdate update = removePostponedUpdate(postponedUpdates, pos,
                                false);
                        // the item was moved to that position. we do -1 because this is a move not
                        // add and removing current item offsets the target move by 1
                        //noinspection ConstantConditions
                        changes.add(Change.moved(start + i, update.currentPos - 1));
                        break;
                    case FLAG_IGNORE: // ignoring this
                        postponedUpdates.add(new PostponedUpdate(globalIndex + i, start + i, true));
                        break;
                    default:
                        throw new IllegalStateException("unknown flag for pos " + (globalIndex + i) + " " + Long.toBinaryString(status));
                }
            }

            return changes;
        }
    }
    /**
     * Represents an update that we skipped because it was a move.
     * <p>
     * When an update is skipped, it is tracked as other updates are dispatched until the matching
     * add/remove operation is found at which point the tracked position is used to dispatch the
     * update.
     */
    private static class PostponedUpdate {
        int posInOwnerList;
        int currentPos;
        boolean removal;
        public PostponedUpdate(int posInOwnerList, int currentPos, boolean removal) {
            this.posInOwnerList = posInOwnerList;
            this.currentPos = currentPos;
            this.removal = removal;
        }
    }

    private interface EqualsComparator<T>
    {
        boolean isEqualTo(T a, T b);
    }
}