/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.pdf.features;

import invenio.common.ExtractorGeometryTools;
import invenio.common.IntervalTree;
import invenio.common.Pair;
import invenio.pdf.core.DisplayedOperation;
import invenio.pdf.core.ExtractorParameters;
import invenio.pdf.core.FeatureNotPresentException;
import invenio.pdf.core.IPDFDocumentFeatureProvider;
import invenio.pdf.core.Operation;
import invenio.pdf.core.PDFCommonTools;
import invenio.pdf.core.PDFDocumentManager;
import invenio.pdf.core.PDFPageManager;
import java.awt.Rectangle;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
/**
 *
 * @author piotr
 */
public class FiguresProvider implements IPDFDocumentFeatureProvider {
    private static Log log = LogFactory.getLog(FiguresProvider.class);  

    private static String getPlotIdFromCaption(String caption) {
        String candidate = "figure";
        String prepared = caption.toLowerCase().trim().replace(" ", "");

        Pattern p = Pattern.compile("(figure|fig\\.|fig|plot|image|img.|img|table|tab.|tab)([0-9IVX]*)");
        Matcher m = p.matcher(prepared);

        if (m.lookingAt()) {
            candidate = m.group(0);
            candidate = candidate.replace(":", "");
        }
        
        return FigureCandidate.getUniqueIdentifier(candidate);
    }

    @Override
    public Figures calculateFeature(PDFDocumentManager docManager) throws FeatureNotPresentException, Exception {
        // gathering all the plot descriptors from all the pages and generaing one collection
        Figures result = new Figures();
        for (int pageNum = 0; pageNum < docManager.getPagesNumber(); ++pageNum) {
            result.figures.add(getFiguresFromPage(docManager.getPage(pageNum)));
        }

        HashMap<Integer, LinkedList<FigureCaption>> captions = getAllCaptions(docManager);

        /**
         * At this moment we do not support captions appearing on a different
         * page than the figure itself, but such situations happen in rare
         * cases.
         */
        FiguresProvider.matchPlotsWithCaptions(docManager, result, captions);

        // Now we are left with some unmatched captions.... we should further investigate this

        /// Debugging code ... we show all captions found inside of the document
        log.debug("Matched captions : \n\n");
        for (int pageNum : captions.keySet()) {
            log.debug("Page " + pageNum + "\n");
            for (FigureCaption caption : captions.get(pageNum)) {
                if (caption.alreadyMatched) {
                    log.debug("caption: " + caption.text + "\n");
                }

            }
        }
        log.debug("\n****************************************************************************\n\nUnmatched captions:\n\n");
        for (int pageNum : captions.keySet()) {
            log.debug("Page " + pageNum + "\n");

            for (FigureCaption caption : captions.get(pageNum)) {
                if (!caption.alreadyMatched) {
                    log.debug("Caption " + caption.text + "\n");
                }
            }
        }
        return result;
    }

    @Override
    public String getProvidedFeatureName() {
        return Figures.featureName;
    }

    /**
     * Class used durign matching captions with figure candidates it
     * encapsulates one of 3 tyeops of objects: - unassigned textual or
     * graphical cluster - figure candidate (rejected or not) - caption cluster
     */
    private static class PageRegion {

        Rectangle boundary;
        DisplayedOperation unassignedOperation = null;
        boolean isCaption = false;
        FigureCandidate figureCandidate = null;
    }

    private static abstract class CaptionMatcherGeneric {

        HashMap<Rectangle, DisplayedOperation> accumulator = new HashMap<Rectangle, DisplayedOperation>();
        Rectangle accumulatorBoundary = null;
        HashMap<Rectangle, FigureCandidate> figuresAccumulator = new HashMap<Rectangle, FigureCandidate>();

        public abstract int getNextNumber(int num, TreeSet<Integer> yCoordinates);

        public abstract int updateReferenceY(FigureCandidate figure, int referenceY);

        public abstract boolean stopByAcceptanceMargin(int referenceY, int curY, double toleranceMargin);

        public void process(FigureCaption caption, double toleranceMargin,
                TreeSet<Integer> yCoordinates, IntervalTree<PageRegion> spatialMgrY) {
            int curY = this.getInitialY(caption);
            int referenceY = curY;

            boolean stop = false;

            LinkedList<DisplayedOperation> tmpAccumulator = new LinkedList<DisplayedOperation>();
            LinkedList<FigureCandidate> tmpFiguresAccumulator = new LinkedList<FigureCandidate>();


            while (!stop && !this.stopByAcceptanceMargin(referenceY, curY, toleranceMargin) && !this.stopByLastOperation(yCoordinates, curY)) {
                //while (!stop && Math.abs(referenceY - curY) < toleranceMargin && yCoordinates.first() != curY) {

                int newY = this.getNextNumber(curY, yCoordinates);
                boolean ignoreTmpAcc = false;

                LinkedList<PageRegion> intersectingRegions = new LinkedList<PageRegion>();
                Set<PageRegion> intersectingIntervals = spatialMgrY.getIntersectingIntervals(newY - 1, newY + 1).keySet();
                for (PageRegion region : intersectingIntervals) {
                    // check if they intersect in X
                    if (!(region.boundary.x > caption.boundary.x + caption.boundary.width
                            || region.boundary.x + region.boundary.width < caption.boundary.x)) {
                        // caption -> STOP
                        if (region.isCaption) {
                            stop = true;
                            ignoreTmpAcc = true;
                            break;
                        }
                        // unassigned -> add to accumulator 
                        if (region.unassignedOperation != null) {
                            tmpAccumulator.add(region.unassignedOperation);
                        }

                        // figure candidate -> make it really a candidate, possibly combine different parts

                        if (region.figureCandidate != null) {
                            // add to accumulator, rest reference line
                            if (!"".equals(region.figureCandidate.getCaption().text)) {
                                stop = true;
                                ignoreTmpAcc = true;
                                break;
                            } else {
                                tmpFiguresAccumulator.add(region.figureCandidate);
                            }

                        }
                    }
                    /// now merging accumulator into the figure and possibly making the figure candidate visible again

                }

                if (!ignoreTmpAcc && !tmpFiguresAccumulator.isEmpty()) {
                    // include in global accumulator ... only if we did ntot block explicitly and there are some cached figures
                    // we mark all plots from the accumulator as approved
                    for (DisplayedOperation op : tmpAccumulator) {


                        if (accumulatorBoundary == null) {
                            accumulatorBoundary = op.getBoundary();
                        } else {
                            accumulatorBoundary = accumulatorBoundary.union(op.getBoundary());
                        }
                        accumulator.put(op.getBoundary(), op);

                    }

                    for (FigureCandidate figure : tmpFiguresAccumulator) {
                        figure.isApproved = true;
                        figuresAccumulator.put(figure.getBoundary(), figure);
                        referenceY = this.updateReferenceY(figure, referenceY);
                    }

                    tmpAccumulator = new LinkedList<DisplayedOperation>();
                    tmpFiguresAccumulator = new LinkedList<FigureCandidate>();
                }
                curY = newY;
            }

        }

        public abstract int getInitialY(FigureCaption caption);

        public abstract boolean stopByLastOperation(TreeSet<Integer> yCoordinates, int curY);
    }

    private static class CaptionMatcherUp extends CaptionMatcherGeneric {

        @Override
        public int getNextNumber(int curY, TreeSet<Integer> yCoordinates) {
            return yCoordinates.lower(curY);
        }

        @Override
        public int updateReferenceY(FigureCandidate figure, int referenceY) {
            return Math.min(referenceY, figure.getBoundary().y);
        }

        @Override
        public boolean stopByAcceptanceMargin(int referenceY, int curY, double toleranceMargin) {
            return referenceY - curY > toleranceMargin;
        }

        @Override
        public int getInitialY(FigureCaption caption) {
            return caption.boundary.y;
        }

        @Override
        public boolean stopByLastOperation(TreeSet<Integer> yCoordinates, int curY) {
            return yCoordinates.first() == curY;
        }
    }

    private static class CaptionMatcherDown extends CaptionMatcherGeneric {

        @Override
        public int getNextNumber(int num, TreeSet<Integer> yCoordinates) {
            return yCoordinates.higher(num);
        }

        @Override
        public int updateReferenceY(FigureCandidate figure, int referenceY) {
            return Math.max(referenceY, figure.getBoundary().y + figure.getBoundary().height);
        }

        @Override
        public boolean stopByAcceptanceMargin(int referenceY, int curY, double toleranceMargin) {
            return curY - referenceY > toleranceMargin;
        }

        @Override
        public int getInitialY(FigureCaption caption) {
            return caption.boundary.y + caption.boundary.height;
        }

        @Override
        public boolean stopByLastOperation(TreeSet<Integer> yCoordinates, int curY) {
            return yCoordinates.last() == curY;
        }
    }

    private static boolean intersectsCaptionOrFigureCandidate(Rectangle rec, List<FigureCandidate> figureCandidates, List<FigureCaption> figureCaptions) {
        for (FigureCandidate plot : figureCandidates) {
            if (rec.intersects(plot.getBoundary())) {
                return true;
            }
        }
        for (FigureCaption caption : figureCaptions) {
            if (rec.intersects(caption.boundary)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches detected captions with figure candidates, possibly reverting some
     * unused candidates into figures and including parts of figures which were
     * not considered figures before.
     *
     * @param docManager
     * @param plots
     * @param captions
     * @throws FeatureNotPresentException
     * @throws Exception
     */
    private static void matchPlotsWithCaptions(
            PDFDocumentManager docManager,
            Figures plots,
            HashMap<Integer, LinkedList<FigureCaption>> captions)
            throws FeatureNotPresentException, Exception {
        
        for (int pageNum = 0; pageNum < docManager.getPagesNumber(); ++pageNum) {
            /**
             * Preparing field for further processing - building interval trees
             * and so on ...
             */
            PDFPageManager pageManager = docManager.getPage(pageNum);

            GraphicalAreas graphicalAreas =
                    (GraphicalAreas) pageManager.getPageFeature(GraphicalAreas.featureName);

            TextAreas textAreas =
                    (TextAreas) pageManager.getPageFeature(TextAreas.featureName);

            List<FigureCandidate> figureCandidates = plots.getFigureCandidatesByPage(pageNum);


            IntervalTree<PageRegion> spatialMgrY = new IntervalTree<PageRegion>(
                    pageManager.getPageBoundary().y - 2,
                    pageManager.getPageBoundary().y + pageManager.getPageBoundary().height + 2);


            TreeSet<Integer> yCoordinates = new TreeSet<Integer>();

            ////// we process different types of operations and build the spatial cluster together with collection of y coordinates

            // captions
            for (FigureCaption caption : captions.get(pageNum)) {
                // first consider the area above the caption ... search for first element above
                PageRegion region = new PageRegion();
                region.boundary = caption.boundary;
                region.isCaption = true;
                yCoordinates.add(caption.boundary.y);
                yCoordinates.add(caption.boundary.y + caption.boundary.height);
                //spatialMgrX.addInterval(caption.boundary.x, caption.boundary.x + caption.boundary.width, region);
                spatialMgrY.addInterval(caption.boundary.y, caption.boundary.y + caption.boundary.height, region);
            }

            /// text and gfraphical regions


            for (Object op : pageManager.getOperations()) {
                if (op instanceof DisplayedOperation) {
                    DisplayedOperation dop = (DisplayedOperation) op;
                    PageRegion region = new PageRegion();
                    /// if not intersecting any of plot candidates or captions
                    Rectangle rec = dop.getBoundary();
                    if (!intersectsCaptionOrFigureCandidate(dop.getBoundary(), figureCandidates, captions.get(pageNum))) {
                        region.boundary = rec;
                        region.unassignedOperation = dop;
                        yCoordinates.add(rec.y);
                        yCoordinates.add(rec.y + rec.height);
                        //spatialMgrX.addInterval(rec.x, rec.x + rec.width, region);
                        spatialMgrY.addInterval(rec.y, rec.y + rec.height, region);
                    }
                }

            }


            /// processing figure candidates
            for (FigureCandidate figureCandidate : figureCandidates) {
                PageRegion region = new PageRegion();
                Rectangle rec = figureCandidate.getBoundary();
                region.boundary = rec;
                region.figureCandidate = figureCandidate;
                yCoordinates.add(rec.y);
                yCoordinates.add(rec.y + rec.height);
                //spatialMgrX.addInterval(rec.x, rec.x + rec.width, region);
                spatialMgrY.addInterval(rec.y, rec.y + rec.height, region);
            }

            /**
             *
             * END OF PREPARATION OF DATA STRUCTURES ---- REAL CODE
             *
             *
             * Now considering all the captions from the page in the order of
             * increasing Y
             */
            ExtractorParameters parameters = ExtractorParameters.getExtractorParameters();
            double toleranceMargin = parameters.getMaximalInclusionHeight() * pageManager.getPageBoundary().height;

            for (FigureCaption caption : captions.get(pageNum)) {
                // first consider the area above the caption ... search for first element above

                /// accumulator for small portions of page that have not been taken into account earlier


                // we will be moving the reference line until something stops us
                //......filll the stupid gap
                CaptionMatcherGeneric matcher;

                matcher = new CaptionMatcherUp();
                matcher.process(caption, toleranceMargin, yCoordinates, spatialMgrY);

                if (matcher.figuresAccumulator.isEmpty()) {
                    matcher = new CaptionMatcherDown();
                    matcher.process(caption, toleranceMargin, yCoordinates, spatialMgrY);
                }


                if (!matcher.figuresAccumulator.isEmpty()) {
                    // WARNING: (in the case of possible refactoring) ... this condition mighht look like else staement of previous if, but it IS NOT

                    /*We have to assign at least one figure candidate. Otherwise we search in opposite direction*/
                    FigureCandidate selectedFigure = null;

                    log.debug("Merging resulting figures");

                    if (matcher.figuresAccumulator.size() > 1) {
                        // there are many figure candidates - we create super-figure which will contain all the sub-figures and additional operations
                        selectedFigure = new FigureCandidate();
                        selectedFigure.setId(getPlotIdFromCaption(caption.text.toLowerCase()));
                        selectedFigure.setPageManager(pageManager);
                        Rectangle boundary = null;
                        for (FigureCandidate figureCandidate : matcher.figuresAccumulator.values()) {
                            figureCandidate.isToplevelPlot = false;
                            selectedFigure.addOperations(figureCandidate.getOperations());
                            if (boundary == null) {
                                boundary = figureCandidate.getBoundary();
                            } else {
                                boundary = boundary.union(figureCandidate.getBoundary());
                            }
                        }

                        selectedFigure.setBoundary(boundary);
                        plots.figures.get(pageNum).add(selectedFigure);

                    } else {
                        // there is only one figure candidate - we simply add operation to it                     
                        selectedFigure = matcher.figuresAccumulator.values().iterator().next();
                    }


                    for (Operation op : matcher.accumulator.values()) {
                        selectedFigure.addOperation(op);

                    }

                    if (matcher.accumulatorBoundary != null) {
                        Rectangle newBoundary = selectedFigure.getBoundary().union(matcher.accumulatorBoundary);
                        selectedFigure.setBoundary(newBoundary);
                    }
                    // now assigning caption to the elected figure

                    selectedFigure.setCaption(caption);
                    caption.alreadyMatched = true;
                }
            }

        }
    }

    /**
     * Retrieve all captions appearing in the document, aggregated by page
     * number, layout element number and the rectangle
     *
     * @param docManager the document manager describing currently processed PDF
     * @return
     */
    public static HashMap<Integer, LinkedList<FigureCaption>> getAllCaptions(PDFDocumentManager docManager) throws FeatureNotPresentException, Exception {
        HashMap<Integer, LinkedList<FigureCaption>> captions =
                new HashMap<Integer, LinkedList<FigureCaption>>();

        for (int pageNum = 0; pageNum < docManager.getPagesNumber(); ++pageNum) {
            PDFPageManager pageManager = docManager.getPage(pageNum);

            captions.put(pageNum, new LinkedList<FigureCaption>());

            TextAreas textAreas =
                    (TextAreas) pageManager.getPageFeature(TextAreas.featureName);

            for (Rectangle textRegion : textAreas.areas.keySet()) {
                String textContent = textAreas.areas.get(textRegion).first;
                FigureCaption caption = toFigureCaption(textContent);

                if (caption != null) {
                    caption.boundary = textRegion;
                    captions.get(pageNum).add(caption);
                }
            }

            // now we sort caption within every area... by y coordinate

            java.util.Collections.sort(captions.get(pageNum), new Comparator<FigureCaption>() {
                @Override
                public int compare(FigureCaption o1, FigureCaption o2) {
                    return o1.boundary.y - o2.boundary.y;
                }
            });
        }


        return captions;
    }

    /**
     * Finds all the plots present in the PDF page. Plots are extracted together
     * with captions but without references because captions appear on the same
     * page and textual references have to be found globally in the document.
     *
     * @param manager
     * @return List of plot descriptors
     */
    public static List<FigureCandidate> getFiguresFromPage(PDFPageManager manager) throws FeatureNotPresentException, Exception {

        // first we generate algorithm parameters depending on the page parameters
        //TODO: extend this

        int[] margins = PDFCommonTools.calculateGraphicsMargins(manager);


        /**
         * ***********
         * Treating graphics operations - clustering them, filtering and
         * including appropriate text operations ************
         */
        GraphicalAreas graphicalAreas =
                (GraphicalAreas) manager.getPageFeature(GraphicalAreas.featureName);

        if (graphicalAreas == null) {
            throw new FeatureNotPresentException(GraphicalAreas.featureName);
        }

        Map<Rectangle, Pair<List<Operation>, Integer>> areas;


        areas = graphicalAreas.areas;
        areas = ExtractorGeometryTools.shrinkRectangleMap(areas, margins[0], margins[1]);
        areas = FigureHeuristics.removeIncorrectGraphicalRegions(areas);
        areas = FigureHeuristics.includeTextParts(areas, manager);

        // at this moment we should know unusd areas

        List<FigureCandidate> plots = FiguresProvider.areasToPlots(areas, manager);

        FigureHeuristics.removeFalsePlots(plots, manager); // removals from this stage can be reverted !

        // we are done with plot images -> creating plot structures for every
        // selected region

        return plots;
    }

    private static List<FigureCandidate> areasToPlots(
            Map<Rectangle, Pair<List<Operation>, Integer>> areas,
            PDFPageManager manager) {
        // transforms a collection of areas to an instance of Plots
        List<FigureCandidate> plots = new LinkedList<FigureCandidate>();

        for (Rectangle area : areas.keySet()) {
            FigureCandidate plot = new FigureCandidate();
            plot.setBoundary(area);
            plot.addOperations(areas.get(area).first);
            plot.setPageNumber(manager.getPageNumber());


            /* We do not assign caption at this stage !
             Pair<String, Rectangle> caption = getPlotCaption(plot, manager);        
             plot.setCaption(caption.first);
             plot.setCaptionBoundary(caption.second);
             */

            plot.setPageManager(manager);
            plot.setId(getPlotIdFromCaption(plot.getCaption().text));
            plots.add(plot);
        }

        return plots;
    }

    private static FigureCaption toFigureCaption(String candidate) {
        String prepared = candidate.toLowerCase().trim();
        log.debug("Processing a potential caption : " + candidate);

        
        Pattern p = Pattern.compile("(figure|fig\\.|fig|plot|image|img.|img|table|tab.|tab)([^a-z]*([A-Z]|:|-|—|.))");
        Matcher m = p.matcher(prepared);

        if (m.lookingAt()) {
            FigureCaption caption = new FigureCaption();
            caption.text = candidate;
            caption.figureIdentifier = m.group(0); // the entire word
            caption.isTable = m.group(1).startsWith("tab");
            caption.alreadyMatched = false;
            return caption;
        } else {
            return null;
        }
    }
}
