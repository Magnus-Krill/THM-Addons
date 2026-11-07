/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AxisViewerTest {
    @Test
    void periodicAlignmentKeepsTheWorkingDirectionAtIntersectionsAndEndpoints() {
        for (var direction : HorizontalDirection.values()) {
            var alignment = AxisViewer.nearestStartAlignment(0.5, 0.5, direction.opposite().yaw, true, true, direction);
            assertNotNull(alignment);
            assertEquals(direction, alignment.direction());
        }
        var edge = AxisViewer.nearestStartAlignment(125008.0, 50000.7, 90, true, true, HorizontalDirection.East);
        assertEquals(HorizontalDirection.East, edge.direction());
        assertEquals(125000, edge.x());
    }

    @Test
    void alignmentChecksLateralDriftRatherThanNormalForwardMovement() {
        var cardinal = new AxisViewer.StartAlignment(0, 0, HorizontalDirection.South);
        assertEquals(0, cardinal.crossTrackDistance(0.5, 123.8));
        assertEquals(0.6, cardinal.crossTrackDistance(1.1, 123.8), 1e-9);
        var diagonal = new AxisViewer.StartAlignment(0, 0, HorizontalDirection.SouthEast);
        assertEquals(0, diagonal.crossTrackDistance(50.9, 50.9));
        assertEquals(Math.sqrt(0.5), diagonal.crossTrackDistance(51.9, 50.9), 1e-9);
    }

    @Test
    void startupAlignmentPicksABlockCenterAndFollowsThePlayersFacing() {
        assertEquals(new AxisViewer.StartAlignment(0, 90, HorizontalDirection.North),
            AxisViewer.nearestStartAlignment(2.8, 90.1, 175, false, true));
        assertEquals(new AxisViewer.StartAlignment(-101, -101, HorizontalDirection.NorthWest),
            AxisViewer.nearestStartAlignment(-101.3, -100.6, 133, false, true));
        assertEquals(new AxisViewer.StartAlignment(0, 0, HorizontalDirection.SouthWest),
            AxisViewer.nearestStartAlignment(0.5, 0.5, 45, false, true));
        assertEquals(new AxisViewer.StartAlignment(0, 0, HorizontalDirection.South),
            AxisViewer.nearestStartAlignment(0.5, 0.5, 25, false, false));
    }

    @Test
    void startupAlignmentIncludesNetherRingsDiamondsGridAndExtensions() {
        assertEquals(new AxisViewer.StartAlignment(200, 86, HorizontalDirection.North),
            AxisViewer.nearestStartAlignment(201.5, 86.2, 180, true, true));
        assertEquals(new AxisViewer.StartAlignment(400, 600, HorizontalDirection.SouthWest),
            AxisViewer.nearestStartAlignment(400.8, 601.4, 48, true, true));
        assertEquals(new AxisViewer.StartAlignment(5000, 42123, HorizontalDirection.South),
            AxisViewer.nearestStartAlignment(5001.4, 42123.3, 0, true, true));
        assertEquals(new AxisViewer.StartAlignment(90000, 50000, HorizontalDirection.East),
            AxisViewer.nearestStartAlignment(90000.4, 50001.2, -90, true, true));

        var edge = AxisViewer.nearestStartAlignment(125008.0, 50000.7, -90, true, true);
        assertEquals(125000, edge.x());
        assertEquals(50000, edge.z());
        assertNotEquals(HorizontalDirection.East, edge.direction());
        assertNotEquals(50000, AxisViewer.nearestStartAlignment(90000.4, 50001.2, -90, false, true).z());
    }

    @Test
    void rewardEligibilityIncludesEveryRingAndDiamondInAllQuadrants() {
        for (int radius : AxisViewer.RING_ROADS) {
            for (int sign : new int[] {-1, 1}) {
                assertTrue(AxisViewer.isOnHighway(sign * radius + 0.5, radius * 0.37 + 0.5, true));
                assertTrue(AxisViewer.isOnHighway(radius * 0.37 + 0.5, sign * radius + 0.5, true));
                assertEquals("ring", AxisViewer.highwayType(sign * radius + 0.5, radius * 0.37 + 0.5, true, HorizontalDirection.North));
                assertEquals("ring", AxisViewer.highwayType(radius * 0.37 + 0.5, sign * radius + 0.5, true, HorizontalDirection.East));
            }
        }
        for (int radius : AxisViewer.DIAMONDS) {
            for (int sx : new int[] {-1, 1}) {
                for (int sz : new int[] {-1, 1}) {
                    assertTrue(AxisViewer.isOnHighway(sx * radius * 0.37 + 0.5, sz * radius * 0.63 + 0.5, true));
                    assertEquals("diamond", AxisViewer.highwayType(sx * radius * 0.37 + 0.5, sz * radius * 0.63 + 0.5, true,
                        sx == sz ? HorizontalDirection.NorthEast : HorizontalDirection.SouthEast));
                }
            }
        }
    }

    @Test
    void reportsOnlyTheSharedRoadIdForTheBuildSegment() {
        var start = new Vec3(62500.5, 120, 1000.5);
        assertEquals("ring-62500", AxisViewer.highwayRoute(start, new Vec3(62500.5, 120, 2000.5), true, HorizontalDirection.South));
        assertEquals("other", AxisViewer.highwayRoute(start, new Vec3(75000.5, 120, 2000.5), true, HorizontalDirection.South));
        assertEquals("other", AxisViewer.highwayRoute(start, new Vec3(-62500.5, 120, 2000.5), true, HorizontalDirection.South));
        assertEquals("other", AxisViewer.highwayRoute(new Vec3(15000000.5, 120, 1000.5),
            new Vec3(15000000.5, 120, 2000.5), true, HorizontalDirection.South));
        assertEquals("diamond-25000", AxisViewer.highwayRoute(new Vec3(5000.5, 120, 20000.5),
            new Vec3(10000.5, 120, 15000.5), true, HorizontalDirection.NorthEast));
        assertEquals("grid", AxisViewer.highwayRoute(new Vec3(5000.5, 120, 30000.5),
            new Vec3(5000.5, 120, 20000.5), true, HorizontalDirection.North));
        assertEquals("grid", AxisViewer.highwayRoute(new Vec3(50000.5, 120, 45000.5),
            new Vec3(50000.5, 120, 90000.5), true, HorizontalDirection.South));
    }

    @Test
    void rewardEligibilityIncludesAxesGridAndExtensionsButNotUnmappedTerrain() {
        assertTrue(AxisViewer.isOnHighway(0.5, 1234567.5, false));
        assertTrue(AxisViewer.isOnHighway(-1234566.5, 1234567.5, false));
        assertTrue(AxisViewer.isOnHighway(5000.5, 42123.5, true));
        assertTrue(AxisViewer.isOnHighway(-42122.5, -4999.5, true));
        assertTrue(AxisViewer.isOnHighway(50000.5, 124999.5, true));
        assertTrue(AxisViewer.isOnHighway(-124998.5, -49999.5, true));
        assertTrue(AxisViewer.isOnHighway(62504.5, 23123.5, true), "allow the highway's width");
        assertFalse(AxisViewer.isOnHighway(62506.5, 23123.5, true));
        assertFalse(AxisViewer.isOnHighway(54321.5, 12345.5, true));
        assertFalse(AxisViewer.isOnHighway(5000.5, 51123.5, true), "grid roads stop at 50k");
        assertFalse(AxisViewer.isOnHighway(50000.5, 125006.5, true), "extensions stop at 125k");
        assertFalse(AxisViewer.isOnHighway(62500.5, 23123.5, false), "rings are Nether-only");
        assertFalse(AxisViewer.isOnHighway(Double.NaN, 0.5, true));
        assertFalse(AxisViewer.isOnHighway(Double.POSITIVE_INFINITY, 0.5, true));
    }

    @Test
    void roadTypesKeepMainRingDiamondAndOtherSeparateFromOfficialEligibility() {
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                assertEquals("main", AxisViewer.highwayType(sx * 15000 + 0.5, sz * 15000 + 0.5, true, sx == sz ? HorizontalDirection.SouthEast : HorizontalDirection.SouthWest));
                assertEquals("ring", AxisViewer.highwayType(sx * 62500 + 0.5, sz * 23123 + 0.5, true, HorizontalDirection.North));
                assertEquals("diamond", AxisViewer.highwayType(sx * 370 + 0.5, sz * 630 + 0.5, true, sx == sz ? HorizontalDirection.NorthEast : HorizontalDirection.SouthEast));
                assertEquals("other", AxisViewer.highwayType(sx * 5000 + 0.5, sz * 42123 + 0.5, true, HorizontalDirection.North));
                assertEquals("other", AxisViewer.highwayType(sx * 54321 + 0.5, sz * 12345 + 0.5, true, HorizontalDirection.North));
            }
        }
        assertTrue(AxisViewer.isOnHighway(5000.5, 42123.5, true), "other includes official grid roads");
        assertFalse(AxisViewer.isOnHighway(54321.5, 12345.5, true), "other also includes unofficial roads");
        assertEquals("main", AxisViewer.highwayType(0.5, 62500.5, true, HorizontalDirection.North), "main wins at road intersections");
        assertEquals("ring", AxisViewer.highwayType(0.5, 62500.5, true, HorizontalDirection.East), "ring crossing a main axis");
        assertEquals("ring", AxisViewer.highwayType(62500.5, 62500.5, true, HorizontalDirection.North), "ring crossing a main diagonal");
        assertEquals("main", AxisViewer.highwayType(62500.5, 62500.5, true, HorizontalDirection.SouthEast));
        assertEquals("diamond", AxisViewer.highwayType(0.5, 1000.5, true, HorizontalDirection.NorthEast), "diamond vertex on a main axis");
        assertEquals("diamond", AxisViewer.highwayType(500.5, 500.5, true, HorizontalDirection.NorthEast), "diamond crossing a ring and main diagonal");
        assertEquals("other", AxisViewer.highwayType(370.5, 630.5, true, HorizontalDirection.SouthEast), "crossing a diamond is not building along it");
        assertEquals("other", AxisViewer.highwayType(62500.5, 23123.5, true, null));
        assertEquals("other", AxisViewer.highwayType(62500.5, 23123.5, false, HorizontalDirection.North));
        assertEquals("ring", AxisViewer.highwayType(62504.5, 23123.5, true, HorizontalDirection.North));
        assertEquals("other", AxisViewer.highwayType(62506.5, 23123.5, true, HorizontalDirection.North));
        assertEquals("other", AxisViewer.highwayType(Double.NaN, 0.5, true, HorizontalDirection.North));
        assertEquals("other", AxisViewer.highwayType(Double.POSITIVE_INFINITY, 0.5, true, HorizontalDirection.North));
        assertEquals("other", AxisViewer.highwayType(30000005.5, 0.5, true, HorizontalDirection.North));
    }

    @Test
    void highwayRadiiCoverXaeroPlusAndKeepExistingThmDiamonds() {
        assertArrayEquals(new int[] {
            200, 500, 750, 1000, 1500, 2000, 2500, 3131, 3500, 5000, 6250, 7500,
            10000, 12500, 15000, 20000, 25000, 50000, 55000, 62500, 75000, 100000,
            125000, 250000, 500000, 750000, 1000000, 1250000, 1568852, 1875000,
            2500000, 3750000, 30000000
        }, AxisViewer.RING_ROADS.toIntArray());
        assertArrayEquals(new int[] {
            1000, 2000, 2500, 5000, 10000, 25000, 50000, 125000, 250000, 500000, 3750000
        }, AxisViewer.DIAMONDS.toIntArray());
    }

    @Test
    void worldSpanningAxesAreClippedToTheCameraWindowInEitherDirection() {
        double[] output = new double[4];
        assertTrue(clip(-30_000_000, 0.5, 30_000_000, 0.5, output));
        assertArrayEquals(new double[] {-128, 0.5, 128, 0.5}, output, 1e-6);
        assertTrue(clip(0.5, 30_000_000, 0.5, -30_000_000, output));
        assertArrayEquals(new double[] {0.5, 128, 0.5, -128}, output, 1e-6);
    }

    @Test
    void bothDiagonalsRetainTheirTrueCenterAlignment() {
        double[] output = new double[4];
        assertTrue(clip(-30_000_000 + 0.5, -30_000_000 + 0.5,
            30_000_000 + 0.5, 30_000_000 + 0.5, output));
        assertArrayEquals(new double[] {-128, -128, 128, 128}, output, 1e-6);
        assertTrue(clip(-30_000_000, 30_000_001, 30_000_000, -29_999_999, output));
        assertArrayEquals(new double[] {-127, 128, 128, -127}, output, 1e-6);
    }

    @Test
    void invisibleRoadsAreRejectedEvenWhenTheirBoundingBoxOverlaps() {
        double[] output = new double[4];
        assertFalse(clip(-50_000, 5000.5, 50_000, 5000.5, output));
        assertFalse(clip(5000.5, -50_000, 5000.5, 50_000, output));
        assertFalse(AxisViewer.clipLine(-100, 130, 130, -100, -10, -10, 10, 10, output));
    }

    @Test
    void gridAndExtensionEndpointsAreNeverExtendedByClipping() {
        double[] output = new double[4];
        assertTrue(AxisViewer.clipLine(50_000.5, 50_000.5, 125_000.5, 50_000.5,
            124_872, 49_872, 125_128, 50_128, output));
        assertArrayEquals(new double[] {124_872, 50_000.5, 125_000.5, 50_000.5}, output, 1e-6);
        assertFalse(AxisViewer.clipLine(5000.5, -50_000 + 0.5, 5000.5, 50_000.5,
            4872, 51_000, 5128, 51_256, output));
    }

    @Test
    void clippingFollowsDistantCameraPositionsAndHandlesBorderScaleRoads() {
        double[] output = new double[4];
        assertTrue(AxisViewer.clipLine(30_000_000.5, -30_000_000 + 0.5,
            30_000_000.5, 30_000_000.5, 29_999_872, -128, 30_000_128, 128, output));
        assertArrayEquals(new double[] {30_000_000.5, -128, 30_000_000.5, 128}, output, 1e-6);
        assertTrue(AxisViewer.clipLine(-125_000 + 0.5, -50_000 + 0.5,
            -50_000 + 0.5, -50_000 + 0.5, -125_128, -50_128, -124_872, -49_872, output));
        assertArrayEquals(new double[] {-124_999.5, -49_999.5, -124_872, -49_999.5}, output, 1e-6);
    }

    @Test
    void shortRoadsInsideTheWindowKeepTheirEndpointsAndBoundaryLinesRemainVisible() {
        double[] output = new double[4];
        assertTrue(clip(-10, 20, 30, -40, output));
        assertArrayEquals(new double[] {-10, 20, 30, -40}, output);
        assertTrue(clip(-1000, 128, 1000, 128, output));
        assertArrayEquals(new double[] {-128, 128, 128, 128}, output, 1e-6);
    }

    private static boolean clip(double x1, double z1, double x2, double z2, double[] output) {
        return AxisViewer.clipLine(x1, z1, x2, z2, -128, -128, 128, 128, output);
    }
}
