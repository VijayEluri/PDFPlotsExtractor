/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.pdf.features;

import invenio.common.ExtractorGeometryTools;
import invenio.common.Pair;
import invenio.common.SpatialClusterManager;
import invenio.pdf.core.FeatureNotPresentException;
import invenio.pdf.core.IPDFPageFeature;
import invenio.pdf.core.IPDFPageFeatureProvider;
import invenio.pdf.core.Operation;
import invenio.pdf.core.PDFCommonTools;
import invenio.pdf.core.PDFPageManager;
import invenio.pdf.core.TextOperation;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author piotr
 */
public class TextAreasProvider implements IPDFPageFeatureProvider {
    private static Log log = LogFactory.getLog(TextAreasProvider.class);  

    /** calculate all the text regions inside the page
     *
     * text regions are distinguished by their margins. The vertical margin
     * is much narrower than the horizontal one
     *
     * @param pageManager
     * @return
     * @throws FeatureNotPresentException
     */
    @Override
    public <T> TextAreas calculateFeature(PDFPageManager<T> pageManager) throws FeatureNotPresentException, Exception {
        TextAreas result = new TextAreas();
        PageLayout pageLayout = (PageLayout) pageManager.getPageFeature(PageLayout.featureName);

        int[] margins = PDFCommonTools.calculateTextMargins(pageManager);

        Rectangle spBd = ExtractorGeometryTools.extendRectangle(
                pageManager.getPageBoundary(),
                margins[0] * 2, margins[1] * 2);

        HashMap<Integer, SpatialClusterManager<Operation>> clusterManagers = new HashMap<Integer, SpatialClusterManager<Operation>>();

        for (int i = 0; i < pageLayout.areas.size(); ++i) {
            clusterManagers.put(i, new SpatialClusterManager<Operation>(spBd, margins[0], margins[1]));
        }

        // System.out.println("SpatialClusterManager<Integer> clustersManager = new SpatialClusterManager<Integer>(new Rectangle(" + spBd.x + ", " + spBd.y + ", " + spBd.width + ", " + spBd.height + "), " + margins[0] + ", " + margins[1] + ");");
        //int tmpNum = 0;

        for (Operation op : pageManager.getTextOperations()) {
            TextOperation textOp = (TextOperation) op;
            Rectangle bd = textOp.getBoundary();
            //  System.out.println("clustersManager.addRectangle(new Rectangle(" + bd.x + ", " + bd.y + ", " + bd.width + ", " + bd.height + "), " + tmpNum + ");");
            // tmpNum++;
            System.out.flush();
            int areaNum = pageLayout.getSingleBestIntersectingArea(textOp.getBoundary());
            if (areaNum >= 0) {
                clusterManagers.get(areaNum).addRectangle(
                        ExtractorGeometryTools.cropRectangle(
                        textOp.getBoundary(), pageManager.getPageBoundary()),
                        textOp);
            }
        }

        Map<Rectangle, List<Operation>> tmpResults = new HashMap<Rectangle, List<Operation>>();
        for (int i = 0; i < pageLayout.areas.size(); ++i) {
            tmpResults.putAll(clusterManagers.get(i).getFinalBoundaries());
        }

        for (Rectangle bd : tmpResults.keySet()) {
            List<Operation> operations = tmpResults.get(bd);
            result.areas.put(ExtractorGeometryTools.shrinkRectangle(bd, margins[0], margins[1]),
                    new Pair<String, List<Operation>>(getTextAreaString(operations), operations));

            log.debug("Text area found: " + getTextAreaString(operations));
        }
        return result;
    }

    @Override
    public String getProvidedFeatureName() {
        return TextAreas.featureName;
    }

    /**
     * Return an uniform string from a list of text operations
     * @param operations
     * @return string representation of a block
     */
    private String getTextAreaString(List<Operation> operations) {
        StringBuilder sb = new StringBuilder();
        // we want to return in the order identical as in page string

        ArrayList<TextOperation> tmpOperations = new ArrayList<TextOperation>();

        for (Operation op : operations) {
            TextOperation to = (TextOperation) op;
            tmpOperations.add(to);
        }

        Collections.sort(tmpOperations, new Comparator<TextOperation>() {

            @Override
            public int compare(TextOperation o1, TextOperation o2) {
                return o1.getTextBeginning() - o2.getTextBeginning();
            }
        });

        for (TextOperation to : tmpOperations) {
            sb.append(to.getText());
        }

        return sb.toString();
    }
}
