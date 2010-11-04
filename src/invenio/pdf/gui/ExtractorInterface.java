/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invenio.pdf.gui;

import de.intarsys.pdf.parser.COSLoadException;
import invenio.pdf.core.FeatureNotPresentException;
import invenio.pdf.core.PDFDocumentManager;
import invenio.pdf.core.PDFPageManager;
import invenio.pdf.core.documentProcessing.PDFDocumentPreprocessor;
import invenio.pdf.features.GraphicalAreasProvider;
import invenio.pdf.features.Plot;
import invenio.pdf.features.Plots;
import invenio.pdf.features.PlotsProvider;
import invenio.pdf.features.TextAreasProvider;
import java.io.IOException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

public class ExtractorInterface {
    // graphic controls

    private Shell shell;
    private Display display;
    // interface elements
    private Label openFileLabel;
    private Button openFileButton;
    private TabFolder pdfPagesTabFolder;
    // variables medling the data
    private String openFileName;

    private void init_interface() {
        /**
         * Creating the basic interface
         */
        this.display = new Display();
        this.shell = new Shell(display);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        shell.setLayout(layout);
        this.openFileLabel = new Label(shell, SWT.NONE);

        this.openFileButton = new Button(shell, SWT.NONE);
        openFileButton.setText("Open PDF File...");

        // Now the grid allowing us to view PDF pages

        GridData tabGData = new GridData();
        tabGData.verticalAlignment = GridData.FILL;
        tabGData.horizontalAlignment = GridData.FILL;
        tabGData.horizontalSpan = 2;
        tabGData.grabExcessHorizontalSpace = true;
        tabGData.grabExcessVerticalSpace = true;

        this.pdfPagesTabFolder = new TabFolder(shell, SWT.NONE);
        this.pdfPagesTabFolder.setLayoutData(tabGData);

        //     TabItem tabItem2 = new TabItem(this.pdfPagesTabFolder, SWT.NONE);

        // prefilling with data

        this.updateOpenFileLabel();
    }

    private void updateOpenFileLabel() {
        this.openFileLabel.setText("The file currently open in the editor: " + this.openFileName);
    }

    private TabItem createPdfPage(String title, PDFPageManager opManager, java.util.List<Plot> plots) throws FeatureNotPresentException, Exception {
        TabItem pageTab = new TabItem(this.pdfPagesTabFolder, SWT.NONE);
        PdfPageComposite content = new PdfPageComposite(this.pdfPagesTabFolder, opManager, plots);
        pageTab.setControl(content);
        pageTab.setText(title);
        return pageTab;
    }

    protected void openDocument(String filename) throws IOException, COSLoadException, FeatureNotPresentException, Exception {
        PDFDocumentManager manager = PDFDocumentPreprocessor.readPDFDocument(filename);

        this.openFileName = filename;
        this.updateOpenFileLabel();
        Plots plots = (Plots) manager.getDocumentFeature(Plots.featureName);
        for (int page = 0; page < manager.getPagesNumber(); page++) {
            TabItem tabPage = createPdfPage("Page " + (page + 1), manager.getPage(page), plots.plots.get(page));
        }
    }

    public ExtractorInterface() {
        this.openFileName = "";
        this.init_interface();
    }

    public void run() {
        /** 
         * run the application
         */
        this.shell.open();
        while (!this.shell.isDisposed()) {
            if (!this.display.readAndDispatch()) {
                this.display.sleep();
            }
        }
        this.display.dispose();
    }

    public static void main(String[] args) throws IOException, COSLoadException, FeatureNotPresentException, Exception {
        // registering feature providers

        PDFPageManager.registerFeatureProvider(new GraphicalAreasProvider());
        PDFPageManager.registerFeatureProvider(new TextAreasProvider());
        PDFDocumentManager.registerFeatureProvider(new PlotsProvider());

        ExtractorInterface exInterface = new ExtractorInterface();

        //exInterface.openDocument("c:\\pdf\\tests\\proper_raster_image_one_page.pdf");
        //exInterface.openDocument("c:\\pdf\\1007.0043.pdf");
        //exInterface.openDocument("c:\\pdf\\tests\\modified7_1007.0043.pdf");
        exInterface.openDocument("/home/piotr/pdf/1007.0043.pdf");
        //exInterface.openDocument("c:\\pdf\\tests\\problematic_page.pdf");

        //exInterface.openDocument("c:\\pdf\\tests\\two_plots_one_page.pdf");
        //exInterface.openDocument("c:\\pdf\\tests\\no_plots.pdf");
        //exInterface.openDocument("c:\\pdf\\tests\\some_math.pdf");

        //exInterface.openDocument("c:\\pdf\\tests\\overlaping_one_page.pdf");

        //exInterface.openDocument("c:\\pdf\\tibor_1.pdf");
        //exInterface.openDocument("c:\\pdf\\1007.0043.pdf");

        exInterface.run();
    }
}