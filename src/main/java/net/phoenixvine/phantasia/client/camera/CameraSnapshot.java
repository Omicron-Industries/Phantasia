package net.phoenixvine.phantasia.client.camera;

public record CameraSnapshot(
                             float yaw,
                             float pitch,
                             float zoom,
                             float targetX,
                             float targetY,
                             float targetZ,
                             boolean playerOwned) {}
