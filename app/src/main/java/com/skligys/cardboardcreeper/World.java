package com.skligys.cardboardcreeper;

import android.content.res.Resources;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.skligys.cardboardcreeper.model.Block;
import com.skligys.cardboardcreeper.model.Chunk;
import com.skligys.cardboardcreeper.model.Point3;
import com.skligys.cardboardcreeper.perlin.Generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/** Holds a randomly generated hilly landscape of blocks and Steve. */
class World {
  private static final String TAG = "World";

  /**
   * Do several physics iterations per frame to avoid falling through the floor when dt is large.
   */
  private static final int PHYSICS_ITERATIONS_PER_FRAME = 5;

  /** Perlin 3d noise based world generator. */
  private final Generator generator;

  private final Object blocksLock = new Object();
  /** All blocks in the world. */
  private final Set<Block> blocks = new HashSet<Block>();
  /** Maps chunk coordinates to a list of blocks inside the chunk. */
  private final Map<Chunk, List<Block>> chunkBlocks = new HashMap<Chunk, List<Block>>();

  /** OpenGL support for drawing grass blocks. */
  private final SquareMesh squareMesh = new SquareMesh();
  private final Performance performance = new Performance();
  private final Steve steve;
  private final Physics physics = new Physics();

  /** Pre-allocated temporary matrix. */
  private final float[] viewProjectionMatrix = new float[16];

  private static interface ChunkChange {}

  private static class ChunkLoad implements ChunkChange {
    private final Chunk chunk;

    ChunkLoad(Chunk chunk) {
      this.chunk = chunk;
    }
  }

  private static class ChunkUnload implements ChunkChange {
    private final Chunk chunk;

    ChunkUnload(Chunk chunk) {
      this.chunk = chunk;
    }
  }

  private final BlockingDeque<ChunkChange> chunkChanges = new LinkedBlockingDeque<ChunkChange>();
  private final Thread chunkLoader;

  World() {
    long start = SystemClock.uptimeMillis();
    generator = new Generator(new Random().nextInt());

    List<Chunk> preloadedChunks;
    synchronized(blocksLock) {
      preloadedChunks = preloadChunks();
      for (Chunk chunk : preloadedChunks) {
        squareMesh.load(chunk, shownBlocks(chunkBlocks.get(chunk)), blocks);
      }
    }

    long elapsed = SystemClock.uptimeMillis() - start;
    synchronized(blocksLock) {
      Log.i(TAG, "Generated " + chunkBlocks.keySet().size() + " chunks in " + elapsed + "ms");
    }

    int startX = Chunk.CHUNK_SIZE / 2;
    int startZ = Chunk.CHUNK_SIZE / 2;
    steve = new Steve(startPosition(startX, startZ));

    // Start the thread to load neighboring chunks in the background.
    chunkLoader = createChunkLoader();
    chunkLoader.start();

    // Schedule neighboring chunks to load in the background.
    Chunk currChunk = steve.currentChunk();
    Set<Chunk> chunksToLoad = neighboringChunks(currChunk);
    chunksToLoad.removeAll(preloadedChunks);
    for (Chunk chunk : chunksToLoad) {
      chunkChanges.add(new ChunkLoad(chunk));
    }
  }

  private List<Chunk> preloadChunks() {
    // Generate a stack of chunks around the starting position (8, 8), other chunks will be loaded
    // in the background.
    int minYChunk = Generator.minElevation() / Chunk.CHUNK_SIZE;
    int maxYChunk = (Generator.maxElevation() + Chunk.CHUNK_SIZE - 1) / Chunk.CHUNK_SIZE;

    List<Chunk> preloadedChunks = new ArrayList<Chunk>();
    for (int y = minYChunk; y <= maxYChunk; ++y) {
      Chunk chunk = new Chunk(0, y, 0);
      loadChunk(chunk);
      preloadedChunks.add(chunk);
    }
    return preloadedChunks;
  }

  /** Adds blocks within a single chunk generated based on 3d Perlin noise. */
  private void loadChunk(Chunk chunk) {
    if (chunkBlocks.keySet().contains(chunk)) {
      return;
    }

    List<Block> blocksInChunk = generator.generateChunk(chunk);
    Log.i(TAG, "Loading chunk " + chunk + ", " + blocksInChunk.size() + " blocks");
    addChunkBlocks(chunk, blocksInChunk);
  }

  private void addChunkBlocks(Chunk chunk, List<Block> blocksInChunk) {
    blocks.addAll(blocksInChunk);
    chunkBlocks.put(chunk, blocksInChunk);
  }

  private void unloadChunk(Chunk chunk) {
    List<Block> blocksInChunk = chunkBlocks.get(chunk);
    if (blocksInChunk == null) {
      return;
    }
    Log.i(TAG, "Unloading chunk " + chunk + ", " + blocksInChunk.size() + " blocks");
    chunkBlocks.remove(chunk);
    blocks.removeAll(blocksInChunk);
  }

  /** Finds the highest solid block with given xz coordinates and returns it. */
  private Block startPosition(int x, int z) {
    return new Block(x, highestSolidY(x, z), z);
  }

  /** Given (x,z) coordinates, finds and returns the highest y so that (x,y,z) is a solid block. */
  private int highestSolidY(int x, int z) {
    int maxY = Generator.minElevation();
    int chunkX = x / Chunk.CHUNK_SIZE;
    int chunkZ = z / Chunk.CHUNK_SIZE;
    synchronized(blocksLock) {
      for (Chunk chunk : chunkBlocks.keySet()) {
        if (chunk.x != chunkX || chunk.z != chunkZ) {
          continue;
        }
        for (Block block : chunkBlocks.get(chunk)) {
          if (block.x != x || block.z != z) {
            continue;
          }
          if (block.y > maxY) {
            maxY = block.y;
          }
        }
      }
    }
    return maxY;
  }

  private List<Block> shownBlocks(List<Block> blocks) {
    List<Block> result = new ArrayList<Block>();
    if (blocks == null) {
      return result;
    }

    for (Block block : blocks) {
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
  private boolean exposed(Block block) {
    return !blocks.contains(new Block(block.x - 1, block.y, block.z)) ||
        !blocks.contains(new Block(block.x + 1, block.y, block.z)) ||
        !blocks.contains(new Block(block.x, block.y - 1, block.z)) ||
        !blocks.contains(new Block(block.x, block.y + 1, block.z)) ||
        !blocks.contains(new Block(block.x, block.y, block.z - 1)) ||
        !blocks.contains(new Block(block.x, block.y, block.z + 1));
  }

  private static final int SHOWN_CHUNK_RADIUS = 3;

  /** Returns chunks within some radius of center, but only those containing any blocks. */
  private Set<Chunk> neighboringChunks(Chunk center) {
    Set<Chunk> result = new HashSet<Chunk>();
    for (int dx = -SHOWN_CHUNK_RADIUS; dx <= SHOWN_CHUNK_RADIUS; ++dx) {
      for (int dy = -SHOWN_CHUNK_RADIUS; dy <= SHOWN_CHUNK_RADIUS; ++dy) {
        for (int dz = -SHOWN_CHUNK_RADIUS; dz <= SHOWN_CHUNK_RADIUS; ++dz) {
          if (!chunkShown(dx, dy, dz)) {
            continue;
          }
          Chunk chunk = center.plus(new Chunk(dx, dy, dz));
          result.add(chunk);
        }
      }
    }
    return result;
  }

  private static boolean chunkShown(int dx, int dy, int dz) {
    return dx * dx + dy * dy + dz * dz <= SHOWN_CHUNK_RADIUS * SHOWN_CHUNK_RADIUS;
  }

  /** Asynchronous chunk loader. */
  private Thread createChunkLoader() {
    Runnable runnable = new Runnable() {
      @Override public void run() {
        while (true) {
          try {
            ChunkChange cc = chunkChanges.takeFirst();
            if (cc instanceof ChunkLoad) {
              Chunk chunk = ((ChunkLoad) cc).chunk;
              synchronized(blocksLock) {
                loadChunk(chunk);
                squareMesh.load(chunk, shownBlocks(chunkBlocks.get(chunk)), blocks);
              }
            } else if (cc instanceof ChunkUnload) {
              Chunk chunk = ((ChunkUnload) cc).chunk;
              synchronized(blocksLock) {
                unloadChunk(chunk);
                squareMesh.unload(chunk);
              }
            } else {
              throw new RuntimeException("Unknown ChunkChange subtype: " + cc.getClass().getName());
            }
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }

          SystemClock.sleep(1);
        }
      }
    };
    return new Thread(runnable);
  }

  void surfaceCreated(Resources resources) {
    squareMesh.surfaceCreated(resources);
  }

  void draw(float[] projectionMatrix) {
    // This has to be first to have up to date tick timestamp for FPS computation.
    float dt = Math.min(performance.tick(), 0.2f);

    performance.startPhysics();
    Point3 eyePosition = null;
    synchronized(blocksLock) {
      // Do several physics iterations per frame to avoid falling through the floor when dt is large.
      for (int i = 0; i < PHYSICS_ITERATIONS_PER_FRAME; ++i) {
        // Physics needs all blocks in the world to compute collisions.
        eyePosition = physics.updateEyePosition(steve, dt / PHYSICS_ITERATIONS_PER_FRAME, blocks);
      }
    }
    performance.endPhysics();

    Chunk beforeChunk = steve.currentChunk();
    Chunk afterChunk = new Chunk(eyePosition);
    if (!afterChunk.equals(beforeChunk)) {
      queueChunkLoads(beforeChunk, afterChunk);
      steve.setCurrentChunk(afterChunk);
    }

    performance.startRendering();
    Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, steve.viewMatrix(), 0);
    squareMesh.draw(viewProjectionMatrix);
    performance.endRendering();

    float fps = performance.fps();
    if (fps > 0.0f) {
      Point3 position = steve.position();
      String status;
      synchronized(blocksLock) {
        status = String.format("%f FPS (%f-%f), " +
                "(%f, %f, %f), " +
                "%d / %d chunks, %d blocks, " +
                "physics: %dms, render: %dms",
            fps, performance.minFps(), performance.maxFps(),
            position.x, position.y, position.z,
            squareMesh.chunksLoaded(), chunkBlocks.keySet().size(), blocks.size(),
            performance.physicsSpent(), performance.renderSpent());
      }
      Log.i(TAG, status);
    }
    performance.done();
  }

  private void queueChunkLoads(Chunk beforeChunk, Chunk afterChunk) {
    Set<Chunk> beforeShownChunks = neighboringChunks(beforeChunk);
    Set<Chunk> afterShownChunks = neighboringChunks(afterChunk);

    // chunksToLoad = afterShownChunks \ beforeShownChunks
    // chunksToUnload = beforeShownChunks \ afterShownChunks
    for (Chunk chunk : setDiff(afterShownChunks, beforeShownChunks)) {
      chunkChanges.add(new ChunkLoad(chunk));
    }
    for (Chunk chunk : setDiff(beforeShownChunks, afterShownChunks)) {
      chunkChanges.add(new ChunkUnload(chunk));
    }
  }

  private static <T> Set<T> setDiff(Set<T> s1, Set<T> s2) {
    Set<T> result = new HashSet<T>(s1);
    result.removeAll(s2);
    return result;
  }

  void drag(float dx, float dy) {
    steve.rotate(dx, dy);
  }

  void walk(boolean start) {
    steve.walk(start);
  }
}
