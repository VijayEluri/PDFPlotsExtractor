/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 *
 * @author piotr
 */
public class IntervalTreeTest extends TestCase {

    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;
    private static final int OPERATION_GETINTERSECTING = 2;
    private static Random randomGenerator;

    public IntervalTreeTest() {
        super();
    }


    @Override
    public void setUp() throws Exception{
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception{
        super.tearDown();
    }

    private void printAddRemoveTest(int intBeginning, int intEnding, List<int[]> recordedIntervals, List<int[]> recordedRemovedIntervals) {
        System.out.println("     Problem with the tree ! ");
        int testIdentifer = randomGenerator.nextInt(1000);
        System.out.println("playSimpleSaintyTest(" + intBeginning + ", " + intEnding + ", new int[][]{");
        int num = 0;
        for (int[] tmpInt : recordedIntervals) {
//                        System.out.println("   " + tmpInt[2] + " -> (" + tmpInt[0] + ", " + tmpInt[1] + ")");
            System.out.print("    new int[]{ " + tmpInt[0] + "," + tmpInt[1] + ", " + tmpInt[2] + "}");
            num++;
            if (num != recordedIntervals.size()) {
                System.out.println(", ");
            } else {
                System.out.println("");
            }
        }

        System.out.println("}, new int[][]{");

        num = 0;
        for (int[] tmpInt : recordedRemovedIntervals) {
            System.out.print("    new int[]{ " + tmpInt[0] + "," + tmpInt[1] + ", " + tmpInt[2] + "}");
            num++;
            if (num != recordedIntervals.size()) {
                System.out.println(", ");
            } else {
                System.out.println("");
            }
        }

        System.out.println("}, \"test" + testIdentifer + "\");");
    }

    /**
     * Test of adding a large number of random intervals ... supposed to find errors
     * that have hidden in a regular testing. This test outputs a code generating a failing test
     */
    public void disabledtestManyIntervals() {
        int b = 0;
        int e = 0;
        IntervalTree<Integer> instance = null;
        int nextNumber = 0; // next number associated with an interval
        int trialNumber = 1000000;
        int intervalsNumber = 5;
        int intervalsToQuery = 4;

        while (trialNumber > 0) {
            int endInterval = 100; // randomGenerator.nextInt(1000000000);
            int beginInterval = 0;// endInterval - randomGenerator.nextInt(2000000000);
            instance = new IntervalTree<Integer>(beginInterval, endInterval);
            checkTreeSainty(instance);

            nextNumber = 0;

            List<int[]> recordedIntervals = new ArrayList<int[]>();
            List<int[]> recordedRemovedIntervals = new LinkedList<int[]>();
            List<int[]> recordedQueriedIntervals = new LinkedList<int[]>();

            for (int i = 0; i < intervalsNumber; i++) {
                do {
                    e = randomGenerator.nextInt(endInterval - beginInterval - 1) + beginInterval + 1;
                    b = randomGenerator.nextInt(endInterval - beginInterval - 1) + beginInterval + 1;
                } while (e == b);
                if (e < b) {
                    int c = e;
                    e = b;
                    b = c;
                }

                instance.addInterval(b, e, nextNumber);
                recordedIntervals.add(new int[]{b, e, nextNumber});

                if (!isTreeSane(instance)) {
                    printAddRemoveTest(beginInterval, endInterval, recordedIntervals, recordedRemovedIntervals);
                }
                checkTreeSainty(instance);

                nextNumber++;
            }
            // now removing all the intervals we added before

            for (int[] interval : recordedIntervals) {
                if (!instance.isIntervalPresentInTree(interval[0], interval[1], interval[2])) {
                    printAddRemoveTest(beginInterval, endInterval, recordedIntervals, recordedRemovedIntervals);
                    System.out.println("an interval scheduled for removal is not present (" + interval[0] + ", " + interval[1] + ") <- " + interval[2]);
                }
                instance.removeInterval(interval[0], interval[1], interval[2]);
                recordedRemovedIntervals.add(new int[]{interval[0], interval[1], interval[2]});

                if (!isTreeSane(instance)) {
                    printAddRemoveTest(beginInterval, endInterval, recordedIntervals, recordedRemovedIntervals);
                }
                checkTreeSainty(instance);
            }

            // now we are randomly querying the set !

            for (int i = 0; i < intervalsToQuery; i++) {
                int qe = 0;
                int qb = 0;
                do {
                    qe = randomGenerator.nextInt(endInterval - beginInterval - 1) + beginInterval + 1;
                    qb = randomGenerator.nextInt(endInterval - beginInterval - 1) + beginInterval + 1;
                } while (qe == qb);
                if (qe < qb) {
                    int c = qe;
                    qe = qb;
                    qb = c;
                }
                recordedQueriedIntervals.add(new int[]{qb, qe});
                instance.getIntersectingIntervals(qb, qe);
            }
            trialNumber--;
        }
    }

    public void testSmallTestYeldToBeFailing() {
        playSimpleSaintyTest(0, 100, new int[][]{
                    new int[]{15, 25, 0},
                    new int[]{30, 91, 1},
                    new int[]{30, 99, 2},
                    new int[]{86, 99, 3},
                    new int[]{33, 55, 4}
                }, new int[][]{
                    new int[]{15, 25, 0},}, "test742");

        playSimpleSaintyTest(0, 100, new int[][]{
                    new int[]{6, 23, 0},
                    new int[]{24, 35, 1},
                    new int[]{24, 63, 2},
                    new int[]{24, 72, 3},
                    new int[]{25, 48, 4}
                }, new int[][]{
                    new int[]{6, 23, 0},}, "test649");
        
        playSimpleSaintyTest(0, 100, new int[][]{
                    new int[]{12, 20, 0},
                    new int[]{27, 92, 1},
                    new int[]{27, 92, 2},
                    new int[]{53, 94, 3},
                    new int[]{35, 39, 4}
                }, new int[][]{
                    new int[]{12, 20, 0},}, "test111");
    }

    /**
     * Test of addInterval method, of class IntervalTree.
     */
    public void testAddInterval() {
        IntervalTree<Integer> instance = new IntervalTree<Integer>(-100, 100);
        checkTreeSainty(instance);

        instance.addInterval(0, 0, 1); // trying to add an empty interval
        checkTreeSainty(instance);

        instance.addInterval(1, 2, 2);
        checkTreeSainty(instance);

    }

    /**
     * A test of adding subsequent (in space) intervals... allows to see how well balanced is the resulting tree
     */
    public void testChainedIntervals() {
        int b = 0;
        int e = 0;
        IntervalTree<Integer> instance = null;
        int nextNumber = 0; // next number associated with an interval


        instance = new IntervalTree<Integer>(0, 1000);
        checkTreeSainty(instance);

        for (int i = 0; i < 20; i++) {
            instance.addInterval(i, i + 1, i);
            checkTreeSainty(instance);
        }

        try {
            Images.writeImageToFile(instance.renderTree(), new File("c:\\intervalTrees\\test_long_chain_balanced.png"));
        } catch (IOException ex) {
            Logger.getLogger(IntervalTree.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     *                  1           2           3           4
     *    (----------)(****)(----)(****)(----)(****)(----)(****)(-----------)
     *  -1000        0     10    20    30    40    50    60    70          1000
     */
    public void testRegularInitiallyDisjointIntervals() {
        IntervalTree<Integer> instance = new IntervalTree<Integer>(-1000, 1000);
        checkTreeSainty(instance);

        instance.addInterval(0, 10, 1);
        checkTreeSainty(instance);

        instance.addInterval(20, 30, 2);
        checkTreeSainty(instance);

        instance.addInterval(40, 50, 3);
        checkTreeSainty(instance);

        instance.addInterval(60, 70, 4);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(1, new int[]{0, 10});
                put(2, new int[]{20, 30});
                put(3, new int[]{40, 50});
            }
        }, instance.getIntersectingIntervals(-1, 55));

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(1, new int[]{0, 10});
                put(2, new int[]{20, 30});
                put(3, new int[]{40, 50});
            }
        }, instance.getIntersectingIntervals(5, 45));

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(1, new int[]{0, 10});
            }
        }, instance.getIntersectingIntervals(5, 6));

        assertIntervals(new HashMap<Integer, int[]>() {

            {
            }
        }, instance.getIntersectingIntervals(10, 11));

        instance.removeInterval(20, 30, 2);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(1, new int[]{0, 10});
                put(3, new int[]{40, 50});
            }
        }, instance.getIntersectingIntervals(5, 45));

        instance.addInterval(39, 51, 14);
        checkTreeSainty(instance);

        instance.addInterval(38, 56, 15);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(1, new int[]{0, 10});
                put(3, new int[]{40, 50});
                put(14, new int[]{39, 51});
                put(15, new int[]{38, 56});

            }
        }, instance.getIntersectingIntervals(5, 45));

        instance.removeInterval(40, 50, 3);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(1, new int[]{0, 10});
                put(14, new int[]{39, 51});
                put(15, new int[]{38, 56});

            }
        }, instance.getIntersectingIntervals(5, 45));

        instance.removeInterval(0, 10, 1);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(14, new int[]{39, 51});
                put(15, new int[]{38, 56});

            }
        }, instance.getIntersectingIntervals(5, 45));

        instance.addInterval(56, 66, 16);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(14, new int[]{39, 51});
                put(15, new int[]{38, 56});
                put(16, new int[]{56, 66});
            }
        }, instance.getIntersectingIntervals(5, 57));

        instance.addInterval(38, 66, 17);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(14, new int[]{39, 51});
                put(15, new int[]{38, 56});
                put(16, new int[]{56, 66});
                put(17, new int[]{38, 66});
            }
        }, instance.getIntersectingIntervals(5, 57));

        instance.addInterval(10, 66, 18);
        checkTreeSainty(instance);

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(14, new int[]{39, 51});
                put(15, new int[]{38, 56});
                put(16, new int[]{56, 66});
                put(17, new int[]{38, 66});
                put(18, new int[]{10, 66});
            }
        }, instance.getIntersectingIntervals(5, 57));

        assertIntervals(new HashMap<Integer, int[]>() {

            {
                put(16, new int[]{56, 66});
                put(17, new int[]{38, 66});
                put(18, new int[]{10, 66});
            }
        }, instance.getIntersectingIntervals(56, 57));
        checkTreeSainty(instance);

        // now trying to remove all the intervals by iterating over them

        Map<Integer, int[]> intervals = instance.getAllIntervals();

        for (Integer i : intervals.keySet()) {
            try {
                Images.writeImageToFile(instance.renderTree(), new File("c:\\intervalTrees\\before_removal_of_" + i + "_" + intervals.get(i)[0] + "_" + intervals.get(i)[1] + ".png"));
            } catch (IOException ex) {
                Logger.getLogger(IntervalTree.class.getName()).log(Level.SEVERE, null, ex);
            }
            instance.removeInterval(intervals.get(i)[0], intervals.get(i)[1], i);
            checkTreeSainty(instance);


        }
        assertIntervals(new HashMap<Integer, int[]>() {

            {
            }
        }, instance.getIntersectingIntervals(56, 57));

        checkTreeSainty(instance);

    }

    private void assertIntervals(Map<Integer, int[]> expectedIntervals,
            Map<Integer, int[]> obtainedIntervals) {
        Map<Integer, Boolean> usedIntervals = new HashMap<Integer, Boolean>();
        String intervalsPresent = "";

        for (Integer i : obtainedIntervals.keySet()) {
            intervalsPresent += " " + i + " [" + obtainedIntervals.get(i)[0] + ", " + obtainedIntervals.get(i)[1] + "]";
        }

        assertEquals("The number of intersecting intervals is incorrect. the resulting intervals are " + intervalsPresent + " ",
                expectedIntervals.size(), obtainedIntervals.size());

        for (Integer key : expectedIntervals.keySet()) {
            assertTrue("The interval identifier " + key
                    + " not present among intersecting intervals",
                    obtainedIntervals.containsKey(key));
            assertEquals("Wrong beginning of the interval " + key + " ",
                    expectedIntervals.get(key)[0],
                    obtainedIntervals.get(key)[0]);
            assertEquals("Wrong ending of the interval " + key + " ",
                    expectedIntervals.get(key)[1],
                    obtainedIntervals.get(key)[1]);
        }
    }

    public void simpleThreeIntervalsTest() {
        playSimpleSaintyTest(0, 10, new int[][]{
                    new int[]{3, 9, 0},
                    new int[]{1, 7, 1},
                    new int[]{6, 9, 2}
                }, new int[][]{}, "reassigning_intervals_after_rotations");
    }

    public void simpleFourIntervalsTest() {
        playSimpleSaintyTest(0, 100, new int[][]{
                    new int[]{37, 46, 0},
                    new int[]{27, 86, 1},
                    new int[]{25, 38, 2},
                    new int[]{16, 92, 3}
                }, new int[][]{}, "simple_four_test");
    }

    public void anotherSimpleTest() {
        playSimpleSaintyTest(0, 100, new int[][]{
                    new int[]{30, 98, 0},
                    new int[]{58, 73, 1},
                    new int[]{55, 83, 2}
                }, new int[][]{}, "test189");
    }

    public void testMixingAddingAndRemovals() {
        playTest(36, 125, new int[][]{
                    new int[]{OPERATION_ADD, 86, 108, 0},
                    new int[]{OPERATION_ADD, 63, 101, 1},
                    new int[]{OPERATION_ADD, 85, 98, 2},
                    new int[]{OPERATION_ADD, 121, 122, 3},
                    new int[]{OPERATION_REMOVE, 63, 101, 1},
                    new int[]{OPERATION_REMOVE, 85, 98, 2},
                    new int[]{OPERATION_ADD, 63, 106, 4}},
                "testMixedAddsRemovals");
    }

    /**
     * playing a test
     * a test consists of list of operations.
     * each operation can be of a type
     * "0" -> add new interval
     * "1" -> remove interval
     * "2" -> get intersecting intervals
     */
    private void playTest(int min, int max, int[][] operations, String testIdentifier) {
        IntervalTree<Integer> tree = new IntervalTree<Integer>(min, max);
        int stepNumber = 0;
        for (int[] operation : operations) {
            switch (operation[0]) {
                case OPERATION_ADD:
                    tree.addInterval(operation[1], operation[2], operation[3]);
                    break;
                case OPERATION_REMOVE:
                    tree.removeInterval(operation[1], operation[2], operation[3]);
                    break;
                case OPERATION_GETINTERSECTING:
                    tree.getIntersectingIntervals(operation[1], operation[2]);
                    break;
                default:
                    System.out.println("invalid test !");
            }

            try {
                Images.writeImageToFile(tree.renderTree(), new File("c:\\intervalTrees\\test_" + testIdentifier + "_step_" + stepNumber + ".png"));
            } catch (IOException ex) {
                System.out.println("epic failure");
            }

            checkTreeSainty(tree);
            stepNumber++;
        }
    }

    private void playSimpleSaintyTest(int min, int max, int[][] intervals, int[][] removals, String testIdentifier) {
        IntervalTree<Integer> tree = new IntervalTree<Integer>(min, max);
        int step_number = 1;
        try {
            Images.writeImageToFile(tree.renderTree(), new File("c:\\intervalTrees\\test_" + testIdentifier + "_step_0.png"));
        } catch (IOException ex) {
            System.out.println("epic failure");
        }

        for (int[] interval : intervals) {
            tree.addInterval(interval[0], interval[1], interval[2]);

            try {
                Images.writeImageToFile(tree.renderTree(), new File("c:\\intervalTrees\\test_" + testIdentifier + "_step_" + step_number + ".png"));
            } catch (IOException ex) {
                System.out.println("epic failure");
            }
            step_number++;

            checkTreeSainty(tree);
        }

        for (int[] interval : removals) {
            tree.removeInterval(interval[0], interval[1], interval[2]);
            try {
                Images.writeImageToFile(tree.renderTree(), new File("c:\\intervalTrees\\test_" + testIdentifier + "_step_" + step_number + ".png"));
            } catch (IOException ex) {
                System.out.println("epic failure");
            }
            step_number++;

            checkTreeSainty(tree);
        }
    }

    /**
     * A standard sanity check
     * @param tree
     */
    public static void checkTreeSainty(IntervalTree<Integer> tree) {

        // 1) check if every node has a correct parent/ children
        Stack<IntervalTree<Integer>.IntervalTreeNode> nodesToVisit = new Stack<IntervalTree<Integer>.IntervalTreeNode>();
        nodesToVisit.push(tree.root);

        while (!nodesToVisit.empty()) {
            IntervalTree<Integer>.IntervalTreeNode currentNode = nodesToVisit.pop();
            assertTrue("Some node has incorrect linking", tree.isTreeNodeCorrect(currentNode));
            for (Integer storedObject : currentNode.associatedObjects) {
                assertNotNull("Some objects are present in a tree node but are not present in the associative table ", tree.intervalsStored.get(storedObject));
            }
            if (currentNode.firstChild != null) {
                nodesToVisit.push(currentNode.firstChild);
            }
            if (currentNode.secondChild != null) {
                nodesToVisit.push(currentNode.secondChild);
            }
        }

        // 2) check if every interval that is claimed to be present, is really there

        for (Integer i : tree.intervalsStored.keySet()) {
            int[] tmpInt = tree.intervalsStored.get(i);

            assertNotNull("A null mapping in the descriptor of present intervals", tmpInt);
            assertTrue("The interval (" + tmpInt[0] + ", " + tmpInt[1]
                    + ") is not present in the tree even if it is preent in the hash map !",
                    tree.isIntervalPresentInTree(tmpInt[0], tmpInt[1], i));

        }

        // 3) check if every interval stored in a regular tree way is also stored in the associative table

        // TODO: implement this check if something does not work
    }

    /**
     * A standard sanity check
     * @param tree
     */
    public static Boolean isTreeSane(IntervalTree<Integer> tree) {

        // 1) check if every node has a correct parent/ children
        Stack<IntervalTree<Integer>.IntervalTreeNode> nodesToVisit = new Stack<IntervalTree<Integer>.IntervalTreeNode>();
        nodesToVisit.push(tree.root);

        while (!nodesToVisit.empty()) {
            IntervalTree<Integer>.IntervalTreeNode currentNode = nodesToVisit.pop();
            if (!tree.isTreeNodeCorrect(currentNode)) {
                return false;
            }

            // now checking all the stored objects -> they should also appear in the global associative table

            for (Integer storedObject : currentNode.associatedObjects) {
                if (tree.intervalsStored.get(storedObject) == null) {
                    return false;
                }
            }

            if (currentNode.firstChild != null) {
                nodesToVisit.push(currentNode.firstChild);
            }

            if (currentNode.secondChild != null) {
                nodesToVisit.push(currentNode.secondChild);
            }

        }

        // 2) check if every interval that is claimed to be present, is really there

        for (Integer i : tree.intervalsStored.keySet()) {
            int[] tmpInt = tree.intervalsStored.get(i);
            if (tmpInt == null) {
                return false;
            }
            if (!tree.isIntervalPresentInTree(tmpInt[0], tmpInt[1], i)) {
                return false;
            }
        }


        // TODO: implement this check if something does not work
        return true;
    }
}
