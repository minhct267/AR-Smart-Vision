package com.google.ar.core.examples.java.common.samplerender;

import android.opengl.GLES30;
import android.util.Log;
import java.nio.Buffer;

class GpuBuffer {
  private static final String TAG = GpuBuffer.class.getSimpleName();
  public static final int INT_SIZE = 4;
  public static final int FLOAT_SIZE = 4;
  private final int target;
  private final int numberOfBytesPerEntry;
  private final int[] bufferId = {0};
  private int size;
  private int capacity;

  public GpuBuffer(int target, int numberOfBytesPerEntry, Buffer entries) {
    if (entries != null) {
      if (!entries.isDirect()) {
        throw new IllegalArgumentException("If non-null, entries buffer must be a direct buffer");
      }
      if (entries.limit() == 0) {
        entries = null;
      }
    }

    this.target = target;
    this.numberOfBytesPerEntry = numberOfBytesPerEntry;

    if (entries == null) {
      this.size = 0;
      this.capacity = 0;
    } else {
      this.size = entries.limit();
      this.capacity = entries.limit();
    }

    try {
      GLES30.glBindVertexArray(0);
      GLError.maybeThrowGLException("Failed to unbind vertex array", "glBindVertexArray");

      GLES30.glGenBuffers(1, bufferId, 0);
      GLError.maybeThrowGLException("Failed to generate buffers", "glGenBuffers");

      GLES30.glBindBuffer(target, bufferId[0]);
      GLError.maybeThrowGLException("Failed to bind buffer object", "glBindBuffer");

      if (entries != null) {
        entries.rewind();
        GLES30.glBufferData(
            target, entries.limit() * numberOfBytesPerEntry, entries, GLES30.GL_DYNAMIC_DRAW);
      }

      GLError.maybeThrowGLException("Failed to populate buffer object", "glBufferData");

    } catch (Throwable t) {
      free();
      throw t;
    }
  }

  public void set(Buffer entries) {
    if (entries == null || entries.limit() == 0) {
      size = 0;
      return;
    }

    if (!entries.isDirect()) {
      throw new IllegalArgumentException("If non-null, entries buffer must be a direct buffer");
    }

    GLES30.glBindBuffer(target, bufferId[0]);
    GLError.maybeThrowGLException("Failed to bind vertex buffer object", "glBindBuffer");

    entries.rewind();

    if (entries.limit() <= capacity) {
      GLES30.glBufferSubData(target, 0, entries.limit() * numberOfBytesPerEntry, entries);
      GLError.maybeThrowGLException("Failed to populate vertex buffer object", "glBufferSubData");
      size = entries.limit();

    } else {
      GLES30.glBufferData(target, entries.limit() * numberOfBytesPerEntry, entries, GLES30.GL_DYNAMIC_DRAW);
      GLError.maybeThrowGLException("Failed to populate vertex buffer object", "glBufferData");
      size = entries.limit();
      capacity = entries.limit();
    }
  }

  public void free() {
    if (bufferId[0] != 0) {
      GLES30.glDeleteBuffers(1, bufferId, 0);
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free buffer object", "glDeleteBuffers");
      bufferId[0] = 0;
    }
  }

  public int getBufferId() {
    return bufferId[0];
  }

  public int getSize() {
    return size;
  }
}
