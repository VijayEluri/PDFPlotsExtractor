/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.pdf.core.documentProcessing;

import de.intarsys.cwt.awt.environment.CwtAwtGraphicsContext;
import de.intarsys.cwt.environment.IGraphicsContext;
import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.text.CSTextExtractor;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDDocument;
import de.intarsys.pdf.pd.PDPage;
import de.intarsys.pdf.pd.PDPageTree;
import de.intarsys.pdf.platform.cwt.rendering.CSPlatformRenderer;
import de.intarsys.pdf.tools.kernel.PDFGeometryTools;
import de.intarsys.tools.locator.FileLocator;
import invenio.pdf.core.ExtractorParameters;
import invenio.pdf.core.PDFDocumentManager;
import invenio.pdf.core.PDFPageManager;
import invenio.pdf.features.CSSelectiveRenderer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * The purpose of this class is to provide interface to a lower level PDF
 * library (in this implementation it is jPod, but could be easily replaced by
 * rewriting the functionality of this class).
 *
 * The only method visible to the external world reads the PDF document whose
 * URI has been specified as an argument, parses it and constructs an instance
 * of PDFPageManager describing the document.
 *
 * The purpose of this transformation is extracting informations useful from the
 * PDF extractor point of view rather than those interesting from the rendering
 * point of view, like native current coordinate-system transformation matrix.
 *
 * @author piotr
 */
public class PDFDocumentTools {

    private static Log log = LogFactory.getLog(PDFDocumentTools.class);  
    /**
     * A method reading the PDF file and returning a corresponding
     * PDFDocumentManager
     *
     * @param fileName path to the input file
     * @return PDFDocumentManager instance
     * @throws IOException
     */
    public static PDFDocumentManager readPDFDocument(File inputFile)
            throws IOException {

        FileLocator locator = new FileLocator(inputFile.getPath());
        PDDocument doc;
        try {
            doc = PDDocument.createFromLocator(locator);
        } catch (COSLoadException ex) {
            throw new IOException("Wrong PDF file");
        }

        PDFDocumentManager documentManager = getOperationsFromDocument(doc, inputFile.getPath());
        //doc.close();
        documentManager.setPDDocument(doc);

        return documentManager;
    }

    private static PDFPageManager<PDPage> getOperationsFromPage(PDPage page) {
        log.info("Starting retrieving operations from a page");
        Rectangle2D rect = page.getCropBox().toNormalizedRectangle();
        BufferedImage image = null;
        IGraphicsContext graphics = null;
        ExtractorParameters parameters = ExtractorParameters.getExtractorParameters();

        try {
            Rectangle pageBoundary = new Rectangle(0, 0,
                    (int) rect.getWidth() * parameters.getPageScale(),
                    (int) rect.getHeight() * parameters.getPageScale());

            image = new BufferedImage((int) pageBoundary.getWidth(),
                    (int) pageBoundary.getHeight(), BufferedImage.TYPE_INT_RGB);

            Graphics2D g2 = (Graphics2D) image.createGraphics();

            //Our wrapper around the 2D device allowing to extract informations
            // about the device
            PDFPageOperationsManager opManager = new PDFPageOperationsManager(pageBoundary);
            ExtractorGraphics2D g2proxy = new ExtractorGraphics2D(g2, opManager, image);

            // now we use our wrapper in order to construct standard mechanisms
            graphics = new CwtAwtGraphicsContext(g2proxy);

            // setup user space
            AffineTransform imgTransform = graphics.getTransform();
            imgTransform.scale(parameters.getPageScale(), -parameters.getPageScale());
            imgTransform.translate(-rect.getMinX(), -rect.getMaxY());

            graphics.setTransform(imgTransform);
            graphics.setBackgroundColor(Color.WHITE);
            graphics.fill(rect);
            CSContent content = page.getContentStream();

            PDFPageManager<PDPage> result = new PDFPageManager<PDPage>();
            result.setInternalPage(page);

            // now the text extraction part
            CSTextExtractor textExtractor = new CSTextExtractor();
            AffineTransform pageTx = new AffineTransform();
            PDFGeometryTools.adjustTransform(pageTx, page);
            textExtractor.setDeviceTransform(pageTx);

            if (content != null) {
                // we inject our own implementation of the renderer -> before performing an operation, it is
                // marked as currently performed. In such a way, out graphics device will be able to detect the operation
                // and assign its attributes

                ExtractorCSInterpreter renderer = new ExtractorCSInterpreter(null,
                        graphics, opManager);

                ExtractorCSTextInterpreter textInterpreter =
                        new ExtractorCSTextInterpreter(null, textExtractor, opManager);

                renderer.process(content, page.getResources());
                textInterpreter.process(content, page.getResources());

                opManager.setPageText(textExtractor.getContent());

                result = opManager.transformToPDFPageManager();
                result.setRenderedPage(image);
                result.setInternalPage(page);
                //result.setPDFObjects(renderer.getObjects());
            }
            System.gc();

            return result;

        } finally {
            if (graphics != null) {
                graphics.dispose();
            }
            System.gc();
        }
    }

    private static PDFDocumentManager getOperationsFromDocument(PDDocument doc,
            String fileName) throws IOException {
        PDFDocumentManager documentManager = new PDFDocumentManager();
        documentManager.setSourceFileName(fileName);

        PDPageTree pages = doc.getPageTree();
        PDPage page = pages.getFirstPage();
        log.debug("Reading operations from pages");
        int pageNum = 0;
        while (page != null) {
            PDFPageManager currentPageManager = getOperationsFromPage(page);
            currentPageManager.setPageNumber(pageNum);
            documentManager.addPage(currentPageManager);
            currentPageManager.setDocumentManager(documentManager);
            page = page.getNextPage();
            pageNum++;
            log.debug(pageNum);
        }
        log.debug("...finished");
        return documentManager;
    }

    /**
     * Renders a page omitting all the graphical and textual operations except
     * given ones
     */
    public static void renderToCanvas(PDFPageManager pageManager, Graphics2D canvas, double scale) {
        // preparing a stucture that will allow us to quickly identify
        // if a given operation should be rendered or not

        PDFPageManager<PDPage> mgr = (PDFPageManager<PDPage>) pageManager;
        PDPage page = mgr.getInternalPage();

        /// now an almost regular rendering ... using our selective renderer

        Rectangle2D rect = page.getCropBox().toNormalizedRectangle();

        IGraphicsContext graphics = null;
        try {
            graphics = new CwtAwtGraphicsContext(canvas);
            // setup user space
            AffineTransform imgTransform = graphics.getTransform();

            imgTransform.scale(scale, -scale);
            imgTransform.translate(-rect.getMinX(), -rect.getMaxY());
            graphics.setTransform(imgTransform);
            graphics.setBackgroundColor(Color.WHITE);
            graphics.fill(rect);
            CSContent content = page.getContentStream();
            if (content != null) {
                CSPlatformRenderer renderer = new CSPlatformRenderer(null, graphics);
                renderer.process(content, page.getResources());
            }

        } finally {
            if (graphics != null) {
                graphics.dispose();
            }
        }
    }

    public static void selectivelyRenderToCanvas(PDFPageManager pageManager, Graphics2D canvas, double scale, Rectangle2D clippingRec) {
        // preparing a stucture that will allow us to quickly identify
        // if a given operation should be rendered or not

        PDFPageManager<PDPage> mgr = (PDFPageManager<PDPage>) pageManager;
        PDPage page = mgr.getInternalPage();

        /// now an almost regular rendering ... using our selective renderer

        Rectangle2D rect = page.getCropBox().toNormalizedRectangle();

        IGraphicsContext graphics = null;
        try {
            graphics = new CwtAwtGraphicsContext(canvas);
            // setup user space
            AffineTransform imgTransform = graphics.getTransform();

            imgTransform.scale(scale, -scale);
            imgTransform.translate(-rect.getMinX(), -rect.getMaxY());
            graphics.setTransform(imgTransform);
            graphics.setBackgroundColor(Color.WHITE);
            graphics.fill(rect);

            CSSelectiveRenderer renderer = new CSSelectiveRenderer(null, graphics);
            renderer.process(mgr, true, clippingRec);

        } finally {
            if (graphics != null) {
                graphics.dispose();
            }
        }
    }
}
