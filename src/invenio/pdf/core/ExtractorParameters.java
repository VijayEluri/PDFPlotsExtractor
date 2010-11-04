package invenio.pdf.core;

import java.util.Properties;

/**
 * Handling parameters of the plots extractor - they are stored in an external
 * configuration file and this class is responsible of providing access to them.
 *
 * Properties object follow the singleton pattern
 * @author piotr
 */
public class ExtractorParameters extends Properties {

    private static ExtractorParameters properties = null;

    /**
     * Getting an instance of the project properties
     * 
     * @return
     */
    public static ExtractorParameters getExtractorParameters() {
        if (properties == null) {
            // TODO : read this information from some configuration XML file
            properties = new ExtractorParameters();
        }
        return properties;
    }

    /* return the maximal */
    public double getMinimalAspectRatio() {
        return 0.1;
    }

    /**
     * Returns the maximal aspect ratio which is a double value
     * @return
     */
    public double getMaximalAspectRatio() {
        return 1 / getMinimalAspectRatio();
    }

    /**
     * Returns a fraction of the page height that will be considered a margin of
     * a graphical operation
     * @return
     */
    public double getVerticalGraphicalMargin() {
        return 0.03;
    }

    /**
     * Returns a fraction of the page height that will be considered
     * a horizontal margin of graphical operations
     * @return
     */
    public double getHorizontalGraphicalMargin() {
        return 0.15;
    }

    /**
     * Returns a fraction of the page height that will be considered a vertical
     * margin for all text operations
     * 
     * @return
     */
    public double getVerticalTextMargin() {
        return 0.0065;
    }

    /**
     * Returns a fraction of the page width that will be considered the horizontal
     * margin of all text operations
     * @return
     */
    public double getHorizontalTextMargin() {
        return 0.05; // this value is much bigger that the vertical margin
        // as it is unlikely that the text block will be lying next
        // to another text block (they are rather located below each other)
    }

    /**
     * Return the coefficient by which the page will be scaled
     * @return
     */
    public int getPageScale() {
        return 1;
    }
}