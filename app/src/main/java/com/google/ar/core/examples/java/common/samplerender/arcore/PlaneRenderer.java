package com.google.ar.core.examples.java.common.samplerender.arcore;

import android.opengl.Matrix;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.samplerender.IndexBuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Shader.BlendFactor;
import com.google.ar.core.examples.java.common.samplerender.Texture;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaneRenderer {
  private static final String TAG = PlaneRenderer.class.getSimpleName();
  private static final String VERTEX_SHADER_NAME = "shaders/plane.vert";
  private static final String FRAGMENT_SHADER_NAME = "shaders/plane.frag";
  private static final String TEXTURE_NAME = "models/trigrid.png";
  private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
  private static final int BYTES_PER_INT = Integer.SIZE / 8;
  private static final int COORDS_PER_VERTEX = 3;
  private static final int VERTS_PER_BOUNDARY_VERT = 2;
  private static final int INDICES_PER_BOUNDARY_VERT = 3;
  private static final int INITIAL_BUFFER_BOUNDARY_VERTS = 64;
  private static final int INITIAL_VERTEX_BUFFER_SIZE_BYTES = BYTES_PER_FLOAT * COORDS_PER_VERTEX * VERTS_PER_BOUNDARY_VERT * INITIAL_BUFFER_BOUNDARY_VERTS;
  private static final int INITIAL_INDEX_BUFFER_SIZE_BYTES = BYTES_PER_INT * INDICES_PER_BOUNDARY_VERT * INDICES_PER_BOUNDARY_VERT * INITIAL_BUFFER_BOUNDARY_VERTS;
  private static final float FADE_RADIUS_M = 0.25f;
  private static final float DOTS_PER_METER = 10.0f;
  private static final float EQUILATERAL_TRIANGLE_SCALE = (float) (1 / Math.sqrt(3));
  private static final float[] GRID_CONTROL = {0.2f, 0.4f, 2.0f, 1.5f};
  private final Mesh mesh;
  private final IndexBuffer indexBufferObject;
  private final VertexBuffer vertexBufferObject;
  private final Shader shader;

  private FloatBuffer vertexBuffer =
      ByteBuffer.allocateDirect(INITIAL_VERTEX_BUFFER_SIZE_BYTES)
          .order(ByteOrder.nativeOrder())
          .asFloatBuffer();

  private IntBuffer indexBuffer =
      ByteBuffer.allocateDirect(INITIAL_INDEX_BUFFER_SIZE_BYTES)
          .order(ByteOrder.nativeOrder())
          .asIntBuffer();

  private final float[] viewMatrix = new float[16];
  private final float[] modelMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];
  private final float[] planeAngleUvMatrix = new float[4];
  private final float[] normalVector = new float[3];
  private final Map<Plane, Integer> planeIndexMap = new HashMap<>();

  public PlaneRenderer(SampleRender render) throws IOException {
    Texture texture = Texture.createFromAsset(render, TEXTURE_NAME, Texture.WrapMode.REPEAT, Texture.ColorFormat.LINEAR);
    shader =
        Shader.createFromAssets(render, VERTEX_SHADER_NAME, FRAGMENT_SHADER_NAME, null)
            .setTexture("u_Texture", texture)
            .setVec4("u_GridControl", GRID_CONTROL)
            .setBlend(BlendFactor.DST_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_ALPHA)
            .setDepthWrite(false);

    indexBufferObject = new IndexBuffer(render, null);
    vertexBufferObject = new VertexBuffer(render, COORDS_PER_VERTEX, null);
    VertexBuffer[] vertexBuffers = {vertexBufferObject};
    mesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, indexBufferObject, vertexBuffers);
  }

  private void updatePlaneParameters(
      float[] planeMatrix, float extentX, float extentZ, FloatBuffer boundary) {
    System.arraycopy(planeMatrix, 0, modelMatrix, 0, 16);
    if (boundary == null) {
      vertexBuffer.limit(0);
      indexBuffer.limit(0);
      return;
    }

    boundary.rewind();
    int boundaryVertices = boundary.limit() / 2;
    int numVertices;
    int numIndices;

    numVertices = boundaryVertices * VERTS_PER_BOUNDARY_VERT;
    numIndices = boundaryVertices * INDICES_PER_BOUNDARY_VERT;

    if (vertexBuffer.capacity() < numVertices * COORDS_PER_VERTEX) {
      int size = vertexBuffer.capacity();
      while (size < numVertices * COORDS_PER_VERTEX) {
        size *= 2;
      }
      vertexBuffer =
          ByteBuffer.allocateDirect(BYTES_PER_FLOAT * size)
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer();
    }
    vertexBuffer.rewind();
    vertexBuffer.limit(numVertices * COORDS_PER_VERTEX);

    if (indexBuffer.capacity() < numIndices) {
      int size = indexBuffer.capacity();
      while (size < numIndices) {
        size *= 2;
      }
      indexBuffer =
          ByteBuffer.allocateDirect(BYTES_PER_INT * size)
              .order(ByteOrder.nativeOrder())
              .asIntBuffer();
    }
    indexBuffer.rewind();
    indexBuffer.limit(numIndices);

    float xScale = Math.max((extentX - 2 * FADE_RADIUS_M) / extentX, 0.0f);
    float zScale = Math.max((extentZ - 2 * FADE_RADIUS_M) / extentZ, 0.0f);

    while (boundary.hasRemaining()) {
      float x = boundary.get();
      float z = boundary.get();
      vertexBuffer.put(x);
      vertexBuffer.put(z);
      vertexBuffer.put(0.0f);
      vertexBuffer.put(x * xScale);
      vertexBuffer.put(z * zScale);
      vertexBuffer.put(1.0f);
    }

    indexBuffer.put((short) ((boundaryVertices - 1) * 2));
    for (int i = 0; i < boundaryVertices; ++i) {
      indexBuffer.put((short) (i * 2));
      indexBuffer.put((short) (i * 2 + 1));
    }
    indexBuffer.put((short) 1);

    for (int i = 1; i < boundaryVertices / 2; ++i) {
      indexBuffer.put((short) ((boundaryVertices - 1 - i) * 2 + 1));
      indexBuffer.put((short) (i * 2 + 1));
    }
    if (boundaryVertices % 2 != 0) {
      indexBuffer.put((short) ((boundaryVertices / 2) * 2 + 1));
    }
  }

  public void drawPlanes(SampleRender render, Collection<Plane> allPlanes, Pose cameraPose, float[] cameraProjection) {
    List<SortablePlane> sortedPlanes = new ArrayList<>();

    for (Plane plane : allPlanes) {
      if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null) {
        continue;
      }

      float distance = calculateDistanceToPlane(plane.getCenterPose(), cameraPose);
      if (distance < 0) {
        continue;
      }

      sortedPlanes.add(new SortablePlane(distance, plane));
    }

    Collections.sort(
        sortedPlanes,
        new Comparator<SortablePlane>() {
          @Override
          public int compare(SortablePlane a, SortablePlane b) {
            return Float.compare(b.distance, a.distance);
          }
        });

    cameraPose.inverse().toMatrix(viewMatrix, 0);

    for (SortablePlane sortedPlane : sortedPlanes) {
      Plane plane = sortedPlane.plane;
      float[] planeMatrix = new float[16];

      plane.getCenterPose().toMatrix(planeMatrix, 0);
      plane.getCenterPose().getTransformedAxis(1, 1.0f, normalVector, 0);
      updatePlaneParameters(planeMatrix, plane.getExtentX(), plane.getExtentZ(), plane.getPolygon());

      Integer planeIndex = planeIndexMap.get(plane);
      if (planeIndex == null) {
        planeIndex = planeIndexMap.size();
        planeIndexMap.put(plane, planeIndex);
      }

      float angleRadians = planeIndex * 0.144f;
      float uScale = DOTS_PER_METER;
      float vScale = DOTS_PER_METER * EQUILATERAL_TRIANGLE_SCALE;

      planeAngleUvMatrix[0] = +(float) Math.cos(angleRadians) * uScale;
      planeAngleUvMatrix[1] = -(float) Math.sin(angleRadians) * vScale;
      planeAngleUvMatrix[2] = +(float) Math.sin(angleRadians) * uScale;
      planeAngleUvMatrix[3] = +(float) Math.cos(angleRadians) * vScale;

      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraProjection, 0, modelViewMatrix, 0);

      shader.setMat4("u_Model", modelMatrix);
      shader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
      shader.setMat2("u_PlaneUvMatrix", planeAngleUvMatrix);
      shader.setVec3("u_Normal", normalVector);

      vertexBufferObject.set(vertexBuffer);
      indexBufferObject.set(indexBuffer);

      render.draw(mesh, shader);
    }
  }

  private static class SortablePlane {
    final float distance;
    final Plane plane;

    SortablePlane(float distance, Plane plane) {
      this.distance = distance;
      this.plane = plane;
    }
  }

  public static float calculateDistanceToPlane(Pose planePose, Pose cameraPose) {
    float[] normal = new float[3];
    float cameraX = cameraPose.tx();
    float cameraY = cameraPose.ty();
    float cameraZ = cameraPose.tz();

    planePose.getTransformedAxis(1, 1.0f, normal, 0);

    return (cameraX - planePose.tx()) * normal[0]
        + (cameraY - planePose.ty()) * normal[1]
        + (cameraZ - planePose.tz()) * normal[2];
  }
}
