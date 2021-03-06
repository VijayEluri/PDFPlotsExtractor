/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.pdf.core;

import de.intarsys.pdf.content.CSOperation;
import de.intarsys.pdf.pd.PDPage;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Call managing one PDF document page and giving access to PDF operations
 * constituting it.
 *
 * @author piotr
 */
public class PDFPageManager<InternalPageType> {

    private List<Operation> operations;
    private Set<Operation> textOperations; // we need to be able to quicly acces the information if the opretation is graphical
    private Set<Operation> graphicalOperations;
    private Set<Operation> transformationOperations;
    private Rectangle pageBoundary;
    WeakReference<BufferedImage> renderedPage;
    private Map<String, IPDFPageFeature> pageFeatures;
    private int pageNumber;
    private String pageText;
    private InternalPageType internalPage; // internal representation of the page
    private PDFDocumentManager documentManager;
    private String rawImageFileName = "";
    // private LinkedList<PDFObject> pdfObjects;
    private final HashMap<CSOperation, Operation> operationsMap;
    private final HashMap<CSOperation, AffineTransform> effectiveTransforms;
    
    // at some point we might need a mapping operation -> index
    public PDFPageManager() {
        this.operations = new ArrayList<Operation>();
        this.textOperations = new HashSet<Operation>();
        this.graphicalOperations = new HashSet<Operation>();
        this.transformationOperations = new HashSet<Operation>();
        this.pageFeatures = new HashMap<String, IPDFPageFeature>();
        this.internalPage = null;
        this.renderedPage = null;
        this.operationsMap = new HashMap<CSOperation, Operation>();
        this.effectiveTransforms = new HashMap<CSOperation, AffineTransform>();
    }

    public void addTextOperation(TextOperation newOp) {
        this.addOperation(newOp);
        this.textOperations.add(newOp);
    }

    public void addOperation(Operation newOp) {
        this.operations.add(newOp);
        this.operationsMap.put(newOp.getOriginalOperation(), newOp);
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
     * Returns the higher level operation by the provided low level operation
     *
     * @param op
     * @return
     */
    public Operation getHigherLevelOperation(CSOperation op) {
        return this.operationsMap.get(op);
    }

    /**
     * Returns a set of all the graphical operations
     *
     * @return
     */
    public Set<Operation> getGraphicalOperations() {
        return this.graphicalOperations;
    }

    /**
     * Returns the rectangle bounding the page inside its coordinates system
     *
     * @return
     */
    public Rectangle getPageBoundary() {
        return this.pageBoundary;
    }

    /**
     * Sets the rectangle that defines page boundary in its coordinates system
     *
     * @param bd
     */
    public void setPageBoundary(Rectangle bd) {
        this.pageBoundary = bd;
    }

    /**
     * Returns a set of all the text-related operations
     *
     * @return
     */
    public Set<Operation> getTextOperations() {
        return this.textOperations;
    }

    /**
     * Returns teh rendered version of the page
     *
     * @return
     */
    public BufferedImage getRenderedPage() {
        BufferedImage result = null;
        if (this.renderedPage != null) {
            result = this.renderedPage.get();
        }

        if (this.renderedPage == null || this.renderedPage.get() == null) {
            result = PDFCommonTools.renderPDFPage((PDPage) this.internalPage);
            this.renderedPage = new WeakReference<BufferedImage>(result);
        }

        return result;
    }

    /**
     * Sets the image being a rendered representation of the page
     *
     * @param rp
     */
    public void setRenderedPage(BufferedImage rp) {

        this.renderedPage = new WeakReference<BufferedImage>(rp);
    }

    /**
     * Sets the page number inside the document. The page number is not the one
     * that is visible (sometimes numbering varies), but rather an offset
     * (starting from 0) from the beginning of the PDF file
     *
     * @param num
     */
    public void setPageNumber(int num) {
        this.pageNumber = num;
    }

    /**
     * Sets the text representation of the page
     *
     * @param t
     */
    public void setPageText(String t) {
        this.pageText = t;
    }

    /**
     * Returns the text representation of the page
     *
     * @return
     */
    public String getPageText() {
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

    public void setInternalPage(InternalPageType page) {
        this.internalPage = page;
    }

    public InternalPageType getInternalPage() {
        return this.internalPage;
    }

    public Set<Operation> getTransOperations() {
        return this.transformationOperations;
    }

    public PDFDocumentManager getDocumentManager() {
        return this.documentManager;
    }

    public void setDocumentManager(PDFDocumentManager manager) {
        this.documentManager = manager;
    }

    /**
     * sets the file name of the image of raw, unannotated page
     *
     * @param n the file name
     */
    public void setRawFileName(String n) {
        this.rawImageFileName = n;
    }

    public String getRawFileName() {
        return this.rawImageFileName;
    }

    /*
     public void setPDFObjects(LinkedList<PDFObject> objects) {
     this.pdfObjects = objects;
     }

     public LinkedList<PDFObject> getPDFObjects(){
     return this.pdfObjects;
     }
     * 
     */
    public void setEffectiveTransform(CSOperation op, AffineTransform at){
        this.effectiveTransforms.put(op, at);
    }
    
    public AffineTransform getEffectiveTransform(CSOperation op){
        return this.effectiveTransforms.get(op);
    }
}
