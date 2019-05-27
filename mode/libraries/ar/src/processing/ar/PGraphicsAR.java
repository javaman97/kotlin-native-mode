/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.ar;

import android.opengl.GLES11Ext;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
import processing.core.PSurface;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

public class PGraphicsAR extends PGraphics3D {
  // Convenience reference to the AR surface. It is the same object one gets from PApplet.getSurface().
  protected PSurfaceAR surfar;

  protected BackgroundRenderer backgroundRenderer;

  protected float[] projmtx = new float[16];
  protected float[] viewmtx = new float[16];

  protected ArrayList<Plane> trackPlanes = new ArrayList<Plane>();
  protected HashMap<Plane, float[]> trackMatrices = new HashMap<Plane, float[]>();
  protected HashMap<Integer, Plane> trackMap = new HashMap<Integer, Plane>();
  protected Plane selPlane;

  protected ArrayList<Plane> newPlanes = new ArrayList<Plane>();
  protected ArrayList<Plane> updatedPlanes = new ArrayList<Plane>();

  protected ArrayList<Anchor> anchors = new ArrayList<Anchor>();
  protected int selAnchor;

  public PGraphicsAR() {
  }


  @Override
  public PSurface createSurface(AppComponent appComponent, SurfaceHolder surfaceHolder, boolean reset) {
    if (reset) pgl.resetFBOLayer();
    surfar = new PSurfaceAR(this, appComponent, surfaceHolder);
    return surfar;
  }


  @Override
  protected PGL createPGL(PGraphicsOpenGL pGraphicsOpenGL) {
    return new PGLES(pGraphicsOpenGL);
  }


  @Override
  public void beginDraw() {
    super.beginDraw();
    updateView();

    // Always clear the screen and draw the background
    background(0);
    backgroundRenderer.draw(surfar.frame);
  }

  public void endDraw() {
    cleanup();
    super.endDraw();
  }


  @Override
  public void camera(float eyeX, float eyeY, float eyeZ,
                     float centerX, float centerY, float centerZ,
                     float upX, float upY, float upZ) {
    PGraphics.showWarning("The camera cannot be set in AR");
  }


  @Override
  public void perspective(float fov, float aspect, float zNear, float zFar) {
    PGraphics.showWarning("Perspective cannot be set in AR");
  }


  @Override
  protected void defaultCamera() {
    // do nothing
  }


  @Override
  protected void defaultPerspective() {
    // do nothing
  }


  @Override
  protected void saveState() {
  }


  @Override
  protected void restoreState() {
  }


  @Override
  protected void restoreSurface() {
  }


  protected void updateView() {
    if (projmtx != null && viewmtx != null) {

      // Fist, set all matrices to identity
      resetProjection();
      resetMatrix();

      // Apply the projection matrix
      applyProjection(projmtx[0], projmtx[4], projmtx[8], projmtx[12],
                      projmtx[1], projmtx[5], projmtx[9], projmtx[13],
                      projmtx[2], projmtx[6], projmtx[10], projmtx[14],
                      projmtx[3], projmtx[7], projmtx[11], projmtx[15]);

      // make modelview = view
      applyMatrix(viewmtx[0], viewmtx[4], viewmtx[8], viewmtx[12],
                  viewmtx[1], viewmtx[5], viewmtx[9], viewmtx[13],
                  viewmtx[2], viewmtx[6], viewmtx[10], viewmtx[14],
                  viewmtx[3], viewmtx[7], viewmtx[11], viewmtx[15]);
    }
  }


  @Override
  public int trackableCount() {
    return trackPlanes.size();
  }


  @Override
  public int trackableId(int i) {
    return trackPlanes.get(i).hashCode();
  }


  @Override
  public int trackableType(int i) {
    Plane plane = trackPlanes.get(i);
    if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) {
      return PAR.PLANE_FLOOR;
    } else if (plane.getType() == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
      return PAR.PLANE_CEILING;
    } else if (plane.getType() == Plane.Type.VERTICAL) {
      return PAR.PLANE_WALL;
    }
    return PAR.UNKNOWN;
  }

  @Override
  public int trackableStatus(int i) {
    Plane plane = trackPlanes.get(i);

    if (newPlanes.contains(plane)) {
      return PAR.CREATED;
    } else if (updatedPlanes.contains(plane)) {
      return PAR.UPDATED;
    } else if (plane.getTrackingState() == TrackingState.TRACKING) {
      return PAR.TRACKING;
    } else if (plane.getTrackingState() == TrackingState.PAUSED) {
      return PAR.PAUSED;
    } else if (plane.getTrackingState() == TrackingState.STOPPED) {
      return PAR.STOPPED;
    }

    return 0;
  }

  @Override
  public boolean trackableSelected(int i) {
    return trackPlanes.indexOf(selPlane) == i;
  }

  @Override
  public float trackableExtentX(int i) {
    return trackPlanes.get(i).getExtentX();
  }


  @Override
  public float trackableExtentZ(int i) {
    return trackPlanes.get(i).getExtentZ();
  }

  @Override
  public float[] getTrackablePolygon(int i) {
    return getTrackablePolygon(i, null);
  }

  @Override
  public float[] getTrackablePolygon(int i, float[] points) {
    Plane plane = trackPlanes.get(i);
    FloatBuffer buffer = plane.getPolygon();
    buffer.rewind();
    if (points == null || points.length < buffer.capacity()) {
      points = new float[buffer.capacity()];
    }
    buffer.get(points, 0, buffer.capacity());
    return points;
  }

  @Override
  public PMatrix3D getTrackableMatrix(int i) {
    return getTrackableMatrix(i, null);
  }


  @Override
  public PMatrix3D getTrackableMatrix(int i, PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }

    Plane plane = trackPlanes.get(i);
    float[] mat = trackMatrices.get(plane);
    target.set(mat[0], mat[4], mat[8], mat[12],
               mat[1], mat[5], mat[9], mat[13],
               mat[2], mat[6], mat[10], mat[14],
               mat[3], mat[7], mat[11], mat[15]);

    return target;
  }


  @Override
  public int anchorCount() {
    return anchors.size();
  }


//  @Override
//  public boolean anchorPaused(int id) {
//    return surfar.anchors.get(id).getTrackingState() == TrackingState.PAUSED;
//  }
//
//  @Override
//  public boolean anchorStopped(int id) {
//    return surfar.anchors.get(id).getTrackingState() == TrackingState.TRACKING;
//  }


  public int anchorId(int i) {
    return i;
  }

  public int anchorStatus(int id) {
    Anchor anchor = anchors.get(id);

    if (anchor.getTrackingState() == TrackingState.PAUSED) {
      return PAR.PAUSED;
    } else if (anchor.getTrackingState() == TrackingState.TRACKING) {
      return PAR.TRACKING;
    } else if (anchor.getTrackingState() == TrackingState.STOPPED) {
      return PAR.STOPPED;
    }

    return 0;
  }


  @Override
  public int createAnchor() {
    if (selAnchor == -1) {
      selAnchor = anchors.size();
      anchors.add(null);
    } else {
      PGraphics.showWarning("Selection anchor already created");
    }
    return selAnchor;
  }

  protected float[] pointIn = new float[3];
  protected float[] pointOut = new float[3];

  @Override
  public int createAnchor(int trackId, float x, float y, float z) {
    Plane plane = trackMap.get(trackId);
    Pose planePose = plane.getCenterPose();
    pointIn[0] = x;
    pointIn[1] = y;
    pointIn[2] = z;
    planePose.transformPoint(pointIn, 0, pointOut, 0);
    Pose anchorPose = Pose.makeTranslation(pointOut);
    Anchor anchor = plane.createAnchor(anchorPose);
    anchors.add(anchor);
    return anchors.size() - 1;
  }


  @Override
  public void deleteAnchor(int id) {
  }


  @Override
  public PMatrix3D getAnchorMatrix(int id) {
    return getAnchorMatrix(id, null);
  }


  protected float[] anchorMatrix = new float[16];

  @Override
  public PMatrix3D getAnchorMatrix(int id, PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    Anchor anchor = anchors.get(id);
    anchor.getPose().toMatrix(anchorMatrix, 0);
    target.set(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
               anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
               anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
               anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15]);
    return target;
  }


  @Override
  public void anchor(int id) {
    Anchor anchor = anchors.get(id);
    anchor.getPose().toMatrix(anchorMatrix, 0);

      // now, modelview = view * anchor
      applyMatrix(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
                  anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
                  anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
                  anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15]);
  }

  @Override
  public void lights() {
    // TODO <---------------------------------------------------------------------------------------
    super.lights();
  }


  protected void createBackgroundRenderer() {
    backgroundRenderer = new BackgroundRenderer(surfar.getActivity());
  }

  protected void setCameraTexture(Session session) {
    session.setCameraTextureName(backgroundRenderer.getTextureId());
  }

  protected void updateMatrices(Camera camera) {
    camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);
    camera.getViewMatrix(viewmtx, 0);
  }


  protected void updateTrackables(Frame frame, ArrayBlockingQueue<MotionEvent> taps) {

    Collection<Plane> planes = frame.getUpdatedTrackables(Plane.class);

    for (Plane plane: planes) {
      if (plane.getSubsumedBy() != null) continue;
      float[] mat;
      if (trackMatrices.containsKey(plane)) {
        mat = trackMatrices.get(plane);
      } else {
        mat = new float[16];
        trackMatrices.put(plane, mat);
        trackPlanes.add(plane);
        trackMap.put(plane.hashCode(), plane);
        newPlanes.add(plane);
        System.out.println("-------------> ADDED TRACKING PLANE " + plane.hashCode());
      }
      Pose pose = plane.getCenterPose();
      pose.toMatrix(mat, 0);
      updatedPlanes.add(plane);
    }

    // Remove stopped and subsummed trackables
    for (int i = trackPlanes.size() - 1; i >= 0; i--) {
      Plane plane = trackPlanes.get(i);
      if (plane.getTrackingState() == TrackingState.STOPPED || plane.getSubsumedBy() != null) {
        trackPlanes.remove(i);
        trackMatrices.remove(plane);
        trackMap.remove(plane.hashCode());
        System.out.println("-------------> REMOVED TRACKING PLANE " + plane.hashCode());
      }
    }

    // Determine selected plane
    MotionEvent tap = taps.poll();
    if (tap != null) {
      for (HitResult hit : frame.hitTest(tap)) {
        Trackable trackable = hit.getTrackable();
        if (trackable instanceof Plane) {
          Plane plane = (Plane)trackable;
          if (trackPlanes.contains(plane) && plane.isPoseInPolygon(hit.getHitPose())) {
            selPlane = plane;
            if (-1 < selAnchor) {
              anchors.get(selAnchor).detach();
              anchors.set(selAnchor, hit.createAnchor());
            }
            System.out.println("-------------> SELECTED TRACKING PLANE " + trackPlanes.indexOf(selPlane) );
            break;
          }
        }
      }
    }
  }


  protected void cleanup() {
    updatedPlanes.clear();
    newPlanes.clear();
  }
}
