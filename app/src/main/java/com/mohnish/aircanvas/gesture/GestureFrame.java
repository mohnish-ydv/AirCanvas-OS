package com.mohnish.aircanvas.gesture;

import java.util.List;

public final class GestureFrame {
    public final List<List<LandmarkPoint>> hands;
    public final List<GestureEvent> events;
    public final String pose;

    public GestureFrame(
            List<List<LandmarkPoint>> hands,
            List<GestureEvent> events,
            String pose
    ) {
        // GestureEngine replaces smoothed hand lists instead of mutating them.
        // Copying just the outer list keeps each frame stable without copying
        // another 42 references on every two-hand camera frame.
        this.hands = List.copyOf(hands);
        this.events = List.copyOf(events);
        this.pose = pose;
    }
}
