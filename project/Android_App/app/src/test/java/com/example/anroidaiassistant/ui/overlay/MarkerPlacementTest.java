package com.example.anroidaiassistant.ui.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MarkerPlacementTest {
    @Test
    public void horizontallyCentersMarkerOnTarget() {
        MarkerPlacement.Position position = MarkerPlacement.calculate(
                200, 400, 28, 34, 32, 1080, 1920, 4
        );

        assertEquals(186, position.left);
    }

    @Test
    public void pinTipLandsOnTargetCenterWithoutArtificialGap() {
        MarkerPlacement.Position position = MarkerPlacement.calculate(
                200, 400, 28, 34, 32, 1080, 1920, 4
        );

        assertEquals(400, position.top + 32);
    }

    @Test
    public void positionsTargetsConsistentlyNearTopMiddleAndBottom() {
        MarkerPlacement.Position nearTop = MarkerPlacement.calculate(
                100, 20, 28, 34, 32, 1080, 1920, 4
        );
        MarkerPlacement.Position middle = MarkerPlacement.calculate(
                100, 960, 28, 34, 32, 1080, 1920, 4
        );
        MarkerPlacement.Position nearBottom = MarkerPlacement.calculate(
                100, 1900, 28, 34, 32, 1080, 1920, 4
        );

        assertEquals(4, nearTop.top);
        assertEquals(928, middle.top);
        assertEquals(1868, nearBottom.top);
    }

    @Test
    public void pinGeometryTracksActualMarkerHeight() {
        int markerHeight = 50;
        int tipOffset = markerHeight - 2;

        MarkerPlacement.Position position = MarkerPlacement.calculate(
                200, 500, 40, markerHeight, tipOffset, 1080, 1920, 4
        );

        assertEquals(500, position.top + tipOffset);
    }
}
