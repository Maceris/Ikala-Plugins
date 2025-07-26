package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.DrawFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import org.joml.Vector2f;

public class DrawList {

    public int getDrawListFlags() {
        // TODO(ches) implement this
        return 0;
    }

    public void setDrawListFlags(int flags) {
        // TODO(ches) implement this
    }

    public void addDrawListFlags(int flags) {
        this.setDrawListFlags(this.getDrawListFlags() | flags);
    }

    public void removeDrawListFlags(int flags) {
        this.setDrawListFlags(this.getDrawListFlags() & ~flags);
    }

    public boolean hasDrawListFlags(int flags) {
        return (this.getDrawListFlags() & flags) != 0;
    }

    public void pushClipRect(float minX, float minY, float maxX, float maxY) {
        // TODO(ches) implement this
    }

    public void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        // TODO(ches) implement this
    }

    public void pushClipRectFullScreen() {
        // TODO(ches) implement this
    }

    public void popClipRect() {
        // TODO(ches) implement this
    }

    public void pushTextureId(int id) {
        // TODO(ches) implement this
    }

    public void popTextureId() {
        // TODO(ches) implement this
    }

    public Vector2f getClipRectMin() {
        Vector2f value = new Vector2f();
        this.getClipRectMin(value);
        return value;
    }

    public void getClipRectMin(Vector2f output) {
        // TODO(ches) implement this
    }

    public float getClipRectMinX() {
        // TODO(ches) implement this
        return 0;
    }

    public float getClipRectMinY() {
        // TODO(ches) implement this
        return 0;
    }

    public Vector2f getClipRectMax() {
        Vector2f value = new Vector2f();
        this.getClipRectMax(value);
        return value;
    }

    public void getClipRectMax(Vector2f output) {
        // TODO(ches) implement this
    }

    public float getClipRectMaxX() {
        // TODO(ches) implement this
        return 0;
    }

    public float getClipRectMaxY() {
        // TODO(ches) implement this
        return 0;
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color) {
        addLine(p1X, p1Y, p2X, p2Y, color, 1.0f);
    }

    public void addLine(float p1X, float p1Y, float p2X, float p2Y, int color, float thickness) {
        // TODO(ches) implement this
    }

    public void addRect(float minX, float minY, float maxX, float maxY, int color) {
        addRect(minX, minY, maxX, maxY, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL, 1.0f);
    }

    public void addRect(float minX, float minY, float maxX, float maxY, int color, float rounding) {
        addRect(minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL, 1.0f);
    }

    public void addRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        addRect(minX, minY, maxX, maxY, color, rounding, drawFlagsRoundingCorners, 1.0f);
    }

    public void addRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addRectFilled(float minX, float minY, float maxX, float maxY, int color) {
        addRectFilled(minX, minY, maxX, maxY, color, 0.0f, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void addRectFilled(
            float minX, float minY, float maxX, float maxY, int color, float rounding) {
        addRectFilled(minX, minY, maxX, maxY, color, rounding, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void addRectFilled(
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        // TODO(ches) implement this
    }

    public void addRectFilledMultiColor(
            float minX,
            float minY,
            float maxX,
            float maxY,
            long colorUpperLeft,
            long colorUpperRight,
            long colorBottomRight,
            long colorBottomLeft) {
        // TODO(ches) implement this
    }

    public void addQuad(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color) {
        addQuad(p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, color, 1.0f);
    }

    public void addQuad(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addQuadFilled(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color) {
        // TODO(ches) implement this
    }

    public void addTriangle(
            float p1X, float p1Y, float p2X, float p2Y, float p3X, float p3Y, int color) {
        addTriangle(p1X, p1Y, p2X, p2Y, p3X, p3Y, color, 1.0f);
    }

    public void addTriangle(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addTriangleFilled(
            float p1X, float p1Y, float p2X, float p2Y, float p3X, float p3Y, int color) {
        // TODO(ches) implement this
    }

    public void addCircle(float centerX, float centerY, float radius, int color) {
        addCircle(centerX, centerY, radius, color, 0, 1.0f);
    }

    public void addCircle(float centerX, float centerY, float radius, int color, int segmentCount) {
        addCircle(centerX, centerY, radius, color, segmentCount, 1.0f);
    }

    public void addCircle(
            float centerX,
            float centerY,
            float radius,
            int color,
            int segmentCount,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addCircleFilled(float centerX, float centerY, float radius, int color) {
        addCircleFilled(centerX, centerY, radius, color, 0);
    }

    public void addCircleFilled(
            float centerX, float centerY, float radius, int color, int segmentCount) {
        // TODO(ches) implement this
    }

    public void addNgon(float centerX, float centerY, float radius, int color, int segmentCount) {
        addNgon(centerX, centerY, radius, color, segmentCount, 1.0f);
    }

    public void addNgon(
            float centerX,
            float centerY,
            float radius,
            int color,
            int segmentCount,
            float thickness) {
        // TODO(ches) implement this
    }

    public void addNgonFilled(
            float centerX, float centerY, float radius, int color, int segmentCount) {
        // TODO(ches) implement this
    }

    public void addText(float posX, float posY, int color, String text) {
        // TODO(ches) implement this
    }

    public void addText(Font Font, float fontSize, float posX, float posY, int color, String text) {
        // TODO(ches) implement this
    }

    public void addText(
            Font Font,
            float fontSize,
            float posX,
            float posY,
            int color,
            String text,
            float wrapWidth) {
        // TODO(ches) implement this
    }

    public void addText(
            Font Font,
            float fontSize,
            float posX,
            float posY,
            int color,
            String text,
            float wrapWidth,
            float cpuFineClipRectX,
            float cpuFineClipRectY,
            float cpuFineClipRectZ,
            float cpuFineClipRectV) {
        // TODO(ches) implement this
    }

    public void addPolyline(
            Vector2f[] points, int pointCount, int color, boolean closed, float thickness) {
        // TODO(ches) implement this
    }

    public void addConvexPolyFilled(Vector2f[] points, int pointCount, int color) {
        // TODO(ches) implement this
    }

    public void addBezierCubic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color,
            float thickness) {
        addBezierCubic(p1X, p1Y, p2X, p2Y, p3X, p3Y, p4X, p4Y, color, thickness, 0);
    }

    public void addBezierCubic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            int color,
            float thickness,
            int segmentCount) {
        // TODO(ches) implement this
    }

    public void addBezierQuadratic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float thickness) {
        addBezierQuadratic(p1X, p1Y, p2X, p2Y, p3X, p3Y, color, thickness, 0);
    }

    public void addBezierQuadratic(
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            int color,
            float thickness,
            int segmentCount) {
        // TODO(ches) implement this
    }

    public void addImage(int textureID, float minX, float minY, float maxX, float maxY) {
        addImage(textureID, minX, minY, maxX, maxY, 0, 0, 1, 1, Color.WHITE);
    }

    public void addImage(
            int textureID, float minX, float minY, float maxX, float maxY, float minU, float minV) {
        addImage(textureID, minX, minY, maxX, maxY, minU, minV, 1, 1, Color.WHITE);
    }

    public void addImage(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV) {
        addImage(textureID, minX, minY, maxX, maxY, minU, minV, maxU, maxV, Color.WHITE);
    }

    public void addImage(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int color) {
        // TODO(ches) implement this
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                0,
                0,
                1,
                0,
                1,
                1,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                1,
                0,
                1,
                1,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                u2,
                v2,
                1,
                1,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2,
            float u3,
            float v3) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                u2,
                v2,
                u3,
                v3,
                0,
                1,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2,
            float u3,
            float v3,
            float u4,
            float v4) {
        addImageQuad(
                textureID,
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                p4X,
                p4Y,
                u1,
                v1,
                u2,
                v2,
                u3,
                v3,
                u4,
                v4,
                Color.WHITE);
    }

    public void addImageQuad(
            int textureID,
            float p1X,
            float p1Y,
            float p2X,
            float p2Y,
            float p3X,
            float p3Y,
            float p4X,
            float p4Y,
            float u1,
            float v1,
            float u2,
            float v2,
            float u3,
            float v3,
            float u4,
            float v4,
            int color) {
        // TODO(ches) implement this
        // TODO(ches) wow this function signature sucks, can we do this using static cached default
        // vecs?
    }

    public void addImageRounded(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int color,
            float rounding) {
        addImageRounded(
                textureID,
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                color,
                rounding,
                DrawFlags.ROUND_CORNERS_ALL);
    }

    public void addImageRounded(
            int textureID,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float minU,
            float minV,
            float maxU,
            float maxV,
            int color,
            float rounding,
            int drawFlagsRoundingCorners) {
        // TODO(ches) implement this
    }

    public void pathClear() {
        // TODO(ches) implement this
    }

    public void pathLineTo(float posX, float posY) {
        // TODO(ches) implement this
    }

    public void pathLineToMergeDuplicate(float posX, float posY) {
        // TODO(ches) implement this
    }

    public void pathFillConvex(int color) {
        // TODO(ches) implement this
    }

    public void pathStroke(int color, boolean closed) {
        pathStroke(color, closed, 1.0f);
    }

    public void pathStroke(int color, boolean closed, float thickness) {
        // TODO(ches) implement this
    }

    public void pathArcTo(float centerX, float centerY, float radius, float minA, float maxA) {
        pathArcTo(centerX, centerY, radius, minA, maxA, 10);
    }

    public void pathArcTo(
            float centerX, float centerY, float radius, float minA, float maxA, int segmentCount) {
        // TODO(ches) implement this
    }

    public void pathArcToFast(
            float centerX, float centerY, float radius, int minAMult12, int maxAMult12) {
        // TODO(ches) implement this
    }

    public void pathBezierCubicCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, float p4X, float p4Y) {
        pathBezierCubicCurveTo(p2X, p2Y, p3X, p3Y, p4X, p4Y, 0);
    }

    public void pathBezierCubicCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, float p4X, float p4Y, int segmentCount) {
        // TODO(ches) implement this
    }

    public void pathBezierQuadraticCurveTo(float p2X, float p2Y, float p3X, float p3Y) {
        pathBezierQuadraticCurveTo(p2X, p2Y, p3X, p3Y, 0);
    }

    public void pathBezierQuadraticCurveTo(
            float p2X, float p2Y, float p3X, float p3Y, int segmentCount) {
        // TODO(ches) implement this
    }

    public void pathRect(float minX, float minY, float maxX, float maxY) {
        pathRect(minX, minY, maxX, maxY, 0.0f, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void pathRect(float minX, float minY, float maxX, float maxY, float rounding) {
        pathRect(minX, minY, maxX, maxY, rounding, DrawFlags.ROUND_CORNERS_ALL);
    }

    public void pathRect(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float rounding,
            int drawFlagsRoundingCorners) {
        // TODO(ches) implement this
    }

    public void primReserve(int indexCount, int vertexCount) {
        // TODO(ches) implement this
    }

    public void primUnreserve(int indexCount, int vertexCount) {
        // TODO(ches) implement this
    }

    public void primRect(float aX, float aY, float bX, float bY, int color) {
        // TODO(ches) implement this
    }

    public void primRectUV(
            float aX,
            float aY,
            float bX,
            float bY,
            float uA,
            float vA,
            float uB,
            float vB,
            int color) {
        // TODO(ches) implement this
    }

    public void primQuadUV(
            float aX,
            float aY,
            float bX,
            float bY,
            float cX,
            float cY,
            float dX,
            float dY,
            float uA,
            float vA,
            float uB,
            float vB,
            float uC,
            float vC,
            float uD,
            float vD,
            int color) {
        // TODO(ches) implement this
    }

    public void primWriteVtx(float posX, float posY, float u, float v, int color) {
        // TODO(ches) implement this
    }

    public void primVtx(float posX, float posY, float u, float v, int color) {
        // TODO(ches) implement this
    }
}
