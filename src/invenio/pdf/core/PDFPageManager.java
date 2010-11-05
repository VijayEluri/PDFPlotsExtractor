/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.pdf.core;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author piotr
 */
public class PDFPageManager {

    private List<Operation> operations;
    private Set<Operation> textOperations; // we need to be able to quicly acces the information if the opretation is graphical
    private Set<Operation> graphicalOperations;
    private Set<Operation> transformationOperations;
    private Rectangle pageBoundary;
    private BufferedImage renderedPage;
    private Map<String, IPDFPageFeature> pageFeatures;
    private int pageNumber;
    private String pageText;

    // at some point we might need a mapping operation -> index
    public PDFPageManager() {
        this.operations = new ArrayList<Operation>();
        this.textOperations = new HashSet<Operation>();
        this.graphicalOperations = new HashSet<Operation>();
        this.transformationOperations = new HashSet<Operation>();
        this.pageFeatures = new HashMap<String, IPDFPageFeature>();
    }

    public void addTextOperation(TextOperation newOp) {
        this.addOperation(newOp);
        this.textOperations.add(newOp);
    }

    public void addOperation(Operation newOp) {
        this.operations.add(newOp);
    }

    public List<Operation> getOperations() {
        return this.operations;
    }

    public void addGraphicalOperation(GraphicalOperation newOp) {
        this.addOperation(newOp);
        this.graphicalOperations.add(newOp);
    }

    public void addTransformationOperation(TransformationOperation newOp) {
        this.addOperation(newOp);
        this.transformationOperations.add(newOp);
    }

    /**
     * Returns a set of all teh graphical operations
     * 
     * @return
     */
    public Set<Operation> getGraphicalOperations() {
        return this.graphicalOperations;
    }

    /**
     * Returns the rectangle bounding the page inside its coordinates system
     * @return
     */
    public Rectangle getPageBoundary() {
        return this.pageBoundary;
    }

    /**
     * Sets the rectangle that defines page boundary in its coordinates system
     * @param bd
     */
    public void setPageBoundary(Rectangle bd) {
        this.pageBoundary = bd;
    }

    /**
     * Returns a set of all the text-related operations
     * @return
     */
    public Set<Operation> getTextOperations() {
        return this.textOperations;
    }

    /**
     * Returns teh rendered version of the page
     * @return
     */
    public BufferedImage getRenderedPage() {
        return this.renderedPage;
    }

    /**
     * Sets the image being a rendered representation of the page
     * @param rp
     */
    public void setRenderedPage(BufferedImage rp) {
        this.renderedPage = rp;
    }

    /**
     * Sets the page number inside the document.
     * The page number is not the one that is visible (sometimes numbering varies),
     * but rather an offset (starting from 0) from the beginning of the PDF file
     * @param num
     */
    public void setPageNumber(int num) {
        this.pageNumber = num;
    }

    /**
     * Sets the text representation of the page
     * @param t
     */
    public void setPageText(String t){
        this.pageText = t;
    }

    /**
     * Returns the text representation of the page
     * @return
     */
    public String getPageText(){
        return this.pageText;
    }
    public int getPageNumber() {
        return this.pageNumber;
    }
    /////// Features maangement
    private static HashMap<String, IPDFPageFeatureProvider> featureProviders =
            new HashMap<String, IPDFPageFeatureProvider>();

    public static void registerFeatureProvider(IPDFPageFeatureProvider provider) {
        PDFPageManager.featureProviders.put(provider.getProvidedFeatureName(), provider);
    }

    public IPDFPageFeature getPageFeature(String featureName) throws FeatureNotPresentException, Exception {
        if (this.pageFeatures.containsKey(featureName)) {
            // the feature is already precalculated and we can jsut return it
            return this.pageFeatures.get(featureName);
        } else {
            if (featureProviders.containsKey(featureName)) {
                this.pageFeatures.put(featureName,
                        featureProviders.get(featureName).calculateFeature(this));
                return this.pageFeatures.get(featureName);
            }
        }
        return null;
    }
}
