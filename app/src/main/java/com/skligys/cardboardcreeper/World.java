package com.skligys.cardboardcreeper;

import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;

/** Holds blocks. */
class World {
  // Very silly stairs as far as an eye can see.
  private static final short[] BLOCKS = {
      -6, 0, -3,
      -5, 0, -3,
      -4, 0, -3,
      -3, 0, -3,
      -2, 0, -3,
      -1, 0, -3,
      0, 0, -3,
      1, 0, -3,
      2, 0, -3,
      3, 0, -3,
      4, 0, -3,
      5, 0, -3,
      6, 0, -3,

      -7, 1, -4,
      -6, 1, -4,
      -5, 1, -4,
      -4, 1, -4,
      -3, 1, -4,
      -2, 1, -4,
      -1, 1, -4,
      0, 1, -4,
      1, 1, -4,
      2, 1, -4,
      3, 1, -4,
      4, 1, -4,
      5, 1, -4,
      6, 1, -4,
      7, 1, -4,

      -7, 2, -5,
      -6, 2, -5,
      -5, 2, -5,
      -4, 2, -5,
      -3, 2, -5,
      -2, 2, -5,
      -1, 2, -5,
      0, 2, -5,
      1, 2, -5,
      2, 2, -5,
      3, 2, -5,
      4, 2, -5,
      5, 2, -5,
      6, 2, -5,
      7, 2, -5,

      -9, 3, -6,
      -8, 3, -6,
      -7, 3, -6,
      -6, 3, -6,
      -5, 3, -6,
      -4, 3, -6,
      -3, 3, -6,
      -2, 3, -6,
      -1, 3, -6,
      0, 3, -6,
      1, 3, -6,
      2, 3, -6,
      3, 3, -6,
      4, 3, -6,
      5, 3, -6,
      6, 3, -6,
      7, 3, -6,
      8, 3, -6,
      9, 3, -6,

      -11, 4, -7,
      -10, 4, -7,
      -9, 4, -7,
      -8, 4, -7,
      -7, 4, -7,
      -6, 4, -7,
      -5, 4, -7,
      -4, 4, -7,
      -3, 4, -7,
      -2, 4, -7,
      -1, 4, -7,
      0, 4, -7,
      1, 4, -7,
      2, 4, -7,
      3, 4, -7,
      4, 4, -7,
      5, 4, -7,
      6, 4, -7,
      7, 4, -7,
      8, 4, -7,
      9, 4, -7,
      10, 4, -7,
      11, 4, -7,
  };

  private final Cube cube;
  private final List<BlockLocation> blocks = new ArrayList<BlockLocation>();

  World() {
    cube = new Cube();

    if (BLOCKS.length % 3 != 0) {
      ExceptionHelper.failIllegalArgument(
          "Blocks should contain triples of coords but length was: %d", BLOCKS.length);
    }
    for (int i = 0; i < BLOCKS.length; i += 3) {
      blocks.add(new BlockLocation(BLOCKS[i], BLOCKS[i + 1], BLOCKS[i + 2]));
    }
  }

  public void surfaceCreated(Resources resources) {
    cube.surfaceCreated(resources);
  }

  public void draw(float[] viewProjectionMatrix) {
    for (BlockLocation block : blocks) {
      cube.draw(viewProjectionMatrix, block.x, block.y, block.z);
    }
  }
}