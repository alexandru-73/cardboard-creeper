package com.skligys.cardboardcreeper;

import android.content.res.Resources;
import android.opengl.Matrix;
import android.util.Log;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Holds a randomly generated hilly landscape of blocks and Steve. */
class World {
  private static final String TAG = "World";
  /**
   * Do several physics iterations per frame to avoid falling through the floor when dt is large.
   */
  private static final int PHYSICS_ITERATIONS_PER_FRAME = 5;

  // World size in x and z directions.
  private final int xSize;
  private final int zSize;
  /** OpenGL support for drawing grass blocks. */
  private final SquareMesh squareMesh;
  /** Center points of all blocks. */
  private final Set<Point3Int> blocks;
  private final Performance performance = new Performance();
  private final Steve steve = new Steve();
  private final Physics physics = new Physics();
  /** Pre-allocated temporary matrix. */
  private final float[] viewProjectionMatrix = new float[16];

  World(int xSize, int zSize) {
    this.xSize = xSize;
    this.zSize = zSize;
    blocks = randomHills();
    // Pre-create the mesh out of only exposedBlocks.
    squareMesh = new SquareMesh(exposedBlocks());
  }

  private Set<Point3Int> randomHills() {
    Set<Point3Int> result = new HashSet<Point3Int>();

    // Create a layer of grass everywhere.
    for (int x = -xSize / 2; x <= xSize / 2; ++x) {
      for (int z = -zSize / 2; z <= zSize / 2; ++z) {
        result.add(new Point3Int(x, 0, z));
//        if (x == -xSize / 2 || x == xSize / 2 || z == -zSize / 2 || z == zSize / 2) {
//          for (int y = 1; y <= 3; ++y) {
//            result.add(new Point3Int(x, y, z));
//          }
//        }
      }
    }

    // Random hills.  4 hills for 16x16 size.
    Random r = new Random();
    for (int i = 0; i < xSize * zSize / 64; ++i) {
      int xCenter = randomInt(r, -xSize / 2, xSize / 2);
      int zCenter = randomInt(r, -zSize / 2, zSize / 2);
      int height = randomInt(r, 1, 6);
      int radius = randomInt(r, 5, 9);
      for (int y = 1; y <= height; ++y) {
        for (int x = xCenter - radius; x <= xCenter + radius; ++x) {
          for (int z = zCenter - radius; z <= zCenter + radius; ++z) {
            if (insideBounds(x, z) &&
                insideHill(x, z, xCenter, zCenter, radius) &&
                !insideCenterCylinder(x, z, 5)) {
              result.add(new Point3Int(x, y, z));
            }
          }
        }
        // Taper the hill as you go up.
        --radius;
      }
    }

    return result;
  }

  private static int randomInt(Random r, int min, int max) {
    return r.nextInt(max - min + 1) + min;
  }

  private boolean insideBounds(int x, int z) {
    return x >= -xSize / 2 && x <= xSize / 2 && z >= -zSize / 2 && z <= zSize / 2;
  }

  private boolean insideHill(int x, int z, int xCenter, int zCenter, int radius) {
    return (x - xCenter) * (x - xCenter) + (z - zCenter) * (z - zCenter) <= radius * radius;
  }

  private boolean insideCenterCylinder(int x, int z, int radius) {
    return x * x + z * z <= radius * radius;
  }

  /**
   * Looks through all blocks and returns only those that are exposed, i.e. not completely
   * surrounded on all sides.
   */
  private Set<Point3Int> exposedBlocks() {
    Set<Point3Int> result = new HashSet<Point3Int>();
    for (Point3Int block : blocks) {
      if (exposed(block)) {
        result.add(block);
      }
    }
    return result;
  }

  /**
   * Checks all 6 faces of the given block and returns true if at least one face is not covered
   * by another block in {@code blocks}.
   */
  private boolean exposed(Point3Int block) {
    return !blocks.contains(new Point3Int(block.x - 1, block.y, block.z)) ||
        !blocks.contains(new Point3Int(block.x + 1, block.y, block.z)) ||
        !blocks.contains(new Point3Int(block.x, block.y - 1, block.z)) ||
        !blocks.contains(new Point3Int(block.x, block.y + 1, block.z)) ||
        !blocks.contains(new Point3Int(block.x, block.y, block.z - 1)) ||
        !blocks.contains(new Point3Int(block.x, block.y, block.z + 1));
  }

  void surfaceCreated(Resources resources) {
    squareMesh.surfaceCreated(resources);
  }

  void draw(float[] projectionMatrix) {
    // This has to be first to have up to date tick timestamp for FPS computation.
    float dt = Math.min(performance.tick(), 0.2f);

    performance.startPhysics();
    // Do several physics iterations per frame to avoid falling through the floor when dt is large.
    for (int i = 0; i < PHYSICS_ITERATIONS_PER_FRAME; ++i) {
      // Physics needs all blocks in the world to compute collisions.
      physics.updateEyePosition(steve, dt / PHYSICS_ITERATIONS_PER_FRAME, blocks);
    }
    performance.endPhysics();

    performance.startRendering();
    Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, steve.viewMatrix(), 0);
    squareMesh.draw(viewProjectionMatrix);
    performance.endRendering();

    float fps = performance.fps();
    if (fps > 0.0f) {
      Point3 position = steve.position();
      String status = String.format("%f FPS, (%f, %f, %f), %d blocks, physics: %dms, render: %dms",
          fps, position.x, position.y, position.z, blocks.size(), performance.physicsSpent(),
          performance.renderSpent());
      Log.i(TAG, status);
    }
    performance.done();
  }

  void drag(float dx, float dy) {
    steve.rotate(dx, dy);
  }

  void walk(boolean start) {
    steve.walk(start);
  }
}
