package invenio.pdf.features;

import de.intarsys.tools.geometry.GeometryTools;
import invenio.pdf.core.ExtractorParameters;
import invenio.common.ExtractorGeometryTools;
import invenio.common.Pair;
import invenio.common.SpatialClusterManager;
import invenio.pdf.core.Operation;
import invenio.pdf.core.PDFPageManager;
import invenio.pdf.core.TextOperation;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A class containing algorithms performing some additional filtering of
 * plot candidates. This includes:
 *  - removing false positives
 *  - joining parts of the same plot.
 *  - including textual parts being part of a plot
 * @author piotr
 */
public class PlotHeuristics {

    public abstract class Predicate<T, S> {

        public abstract boolean evaluate(T v1, S v2);
    }

    /**
     * @param sourceAreas
     * @param candidates
     * @param condition
     * @return
     */
    public static Map<Rectangle, List<Operation>> includeAreas(
            Map<Rectangle, List<Operation>> sourceAreas,
            Map<Rectangle, List<Operation>> candidates,
            Predicate condition) {
        return sourceAreas;
    }

    /**
     * Removes areas that can not be plots because of a wrong aspect ratio
     * (plots can not for example be half of the page hight and few pixels broad
     * @param areas
     */
    public static Map<Rectangle, Pair<List<Operation>, Integer>> removeBasedOnAspectRatio(
            Map<Rectangle, Pair<List<Operation>, Integer>> areas) {

        Map<Rectangle, Pair<List<Operation>, Integer>> result =
                new HashMap<Rectangle, Pair<List<Operation>, Integer>>();

        ExtractorParameters parameters = ExtractorParameters.getExtractorParameters();

        double minAR = parameters.getMinimalAspectRatio();
        double maxAR = parameters.getMaximalAspectRatio();

        for (Rectangle curRec : areas.keySet()) {
            double aspectRatio = curRec.getWidth() / curRec.getHeight();
            if (minAR < aspectRatio && aspectRatio < maxAR) {
                result.put(curRec, areas.get(curRec)); // copying all the
                // operations belonging to a valid area
            }
        }

        return result;
    }

    /**
     * Remove irrelevant plots based on the number of graphical operations present inside.
     * This for example allows us to exclude tables and frames containing text.
     *
     * @return
     */
    public static Map<Rectangle, List<Operation>> removeBasedOnNumberOfOperations() {
        //Map<Rectangle, List<CSOperation>> result = new HashMap<Rectangle
        //TODO Implement... for the moment this heuristic seems not to be crucial
        //     for everything to work

        return null;
    }

    public static Map<Rectangle, Pair<List<Operation>, Integer>> removeFalsePlots(Map<Rectangle, Pair<List<Operation>, Integer>> areas) {
        // remove graphics with too small/too big aspect ratios
        return removeBasedOnAspectRatio(areas);
    }

    /**
     * Includes text parts that are more distant that margin settings would
     * allow, but are small enough and so, unlikely to be part of the text corpus
     * 
     * @param <T>
     * @param areas
     * @param manager
     * @return
     * @throws Exception
     */
    public static <T> Map<Rectangle, List<Operation>> includeLooseTextParts(
            Map<Rectangle, List<Operation>> areas, PDFPageManager<T> manager) throws Exception {
        return null;

    }

    /**
     * Takes a list of areas and includes all the text areas overlaping with them.
     * Possibly joins input areas
     * @param areas
     * @param manager
     * @return
     */
    public static <T> Map<Rectangle, List<Operation>> includeTextAreas(
            Map<Rectangle, List<Operation>> areas, PDFPageManager<T> manager) throws Exception {
        return areas;
    }

    /**
     * Include text areas that are located farther than the threshold, but
     * are small enough that it is not likely, they will live on their own.
     * @param <T>
     * @param areas
     * @param manager
     * @return
     * @throws Exception
     */
    public static <T> Map<Rectangle, List<Operation>> includeLooseTextAreas(
            Map<Rectangle, List<Operation>> areas, PDFPageManager<T> manager) throws Exception {
        // if there is a text part in a bigger radius than searched before and the region is small enough,
        // include it in the plot

        return areas;
    }

    /**
     * Include areas satisfying some criterias 
     * @param <T>
     * @param areas
     * @param manager
     * @return
     * @throws Exception
     */
    public static <T> Map<Rectangle, List<Operation>> includeLooseAreas(
            Map<Rectangle, List<Operation>> areas, PDFPageManager<T> manager) throws Exception {

        return areas;
    }

    /**
     * If any text operation is intersecting with the plot area, include it inside !
     * We assume that areas are not overlapping !
     * @param areas
     * @param manager
     * @return
     */
    public static Map<Rectangle, Pair<List<Operation>, Integer>> includeTextParts(
            Map<Rectangle, Pair<List<Operation>, Integer>> areas, PDFPageManager<?> manager) throws Exception {

        // each region will be represented by one of CSOperation instances
        // constituting it

        ExtractorParameters params = ExtractorParameters.getExtractorParameters();

        int hPlotTextMargin = (int) (params.getHorizontalPlotTextMargin()
                * manager.getPageBoundary().getWidth());
        int vPlotTextMargin = (int) (params.getVerticalPlotTextMargin()
                * manager.getPageBoundary().getHeight());


        HashMap<Operation, Rectangle> areaIdentifiers =
                new HashMap<Operation, Rectangle>();

        // we keep only one cluster per area

        PageLayout layout = (PageLayout) manager.getPageFeature(PageLayout.featureName);
        Map<Integer, SpatialClusterManager<Operation>> clusterManagers =
                new HashMap<Integer, SpatialClusterManager<Operation>>();

        for (int areaNum = 0; areaNum < layout.areas.size(); ++areaNum) {
            clusterManagers.put(areaNum, new SpatialClusterManager<Operation>(
                    ExtractorGeometryTools.extendRectangle(
                    manager.getPageBoundary(), 2, 2), 1, 1));

        }

        // now taking every graphical are and electing a representant operation for it


        for (Rectangle rec : areas.keySet()) {
            Operation representant = areas.get(rec).first.get(0);
            Integer pageArea = areas.get(rec).second;

            areaIdentifiers.put(representant, rec);

            clusterManagers.get(pageArea).addRectangle(
                    ExtractorGeometryTools.extendRectangle(rec, hPlotTextMargin, vPlotTextMargin),
                    representant);
        }

        // now processing all the text operations

        for (Operation textOp : manager.getTextOperations()) {

            if (textOp instanceof TextOperation) {
                // determining, which columns is thgaze operation intersecting

                HashSet<Integer> intersecting = layout.getIntersectingAreas(((TextOperation) textOp).getBoundary());

                Rectangle boundary = ExtractorGeometryTools.extendRectangle(
                        ((TextOperation) textOp).getBoundary(),
                        hPlotTextMargin, vPlotTextMargin);
                for (Integer intArea : intersecting) { // considering for every intersecting area
                    clusterManagers.get(intArea).addRectangle(
                            boundary, textOp);
                }
            }
        }
        Map<Rectangle, Pair<List<Operation>, Integer>> result =
                new HashMap<Rectangle, Pair<List<Operation>, Integer>>();

        for (Integer areaNum = 0; areaNum < layout.areas.size(); ++areaNum) {


            Map<Rectangle, List<Operation>> newBoundaries = clusterManagers.get(areaNum).getFinalBoundaries();


            for (Rectangle rec : newBoundaries.keySet()) {
                // find old rectangle corresponding to this new one
                Rectangle oldRect = null;
                List<Operation> toAdd = new LinkedList<Operation>();
                for (Operation op : newBoundaries.get(rec)) {
                    if (areaIdentifiers.containsKey(op)) {
                        oldRect = areaIdentifiers.get(op);
                    } else {
                        toAdd.add(op);
                    }
                }

                if (oldRect != null) {
                    // we have a cluster containing a plot !
                    toAdd.addAll(areas.get(oldRect).first);
                    result.put(ExtractorGeometryTools.shrinkRectangle(rec,
                            hPlotTextMargin, vPlotTextMargin), new Pair<List<Operation>, Integer>(toAdd, areaNum));
                }
            }

        }

        return result;
    }
}
