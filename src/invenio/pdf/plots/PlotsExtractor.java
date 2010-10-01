package invenio.pdf.plots;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.BooleanControl;

import de.intarsys.cwt.awt.environment.CwtAwtGraphicsContext;
import de.intarsys.cwt.environment.IGraphicsContext;
import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.CSOperation;
import de.intarsys.pdf.cos.COSDictionary;
import de.intarsys.pdf.cos.COSDocument;
import de.intarsys.pdf.cos.COSDocumentElement;
import de.intarsys.pdf.cos.COSName;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.cos.COSStream;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDDocument;
import de.intarsys.pdf.pd.PDForm;
import de.intarsys.pdf.pd.PDImage;
import de.intarsys.pdf.pd.PDObject;
import de.intarsys.pdf.pd.PDPage;
import de.intarsys.pdf.pd.PDPageNode;
import de.intarsys.pdf.pd.PDPageTree;
import de.intarsys.pdf.pd.PDResources;
import de.intarsys.pdf.pd.PDXObject;
import de.intarsys.pdf.platform.cwt.rendering.CSPlatformRenderer;
import de.intarsys.tools.locator.FileLocator;

public class PlotsExtractor {
	/**
	 * @param args
	 */
	public static int SCALE = 2;

	/* Useful debugging methods */

	private static void printCOSOutputLine(int depth, String key, String value,
			PrintStream outputFile) {
		outputFile.print(depth);
		if (depth < 100)
			outputFile.print(" ");
		if (depth < 10)
			outputFile.print(" ");
		for (int i = 0; i < depth; i++) {
			outputFile.print("  ");
		}
		outputFile.print(key);
		outputFile.print("->");
		outputFile.println(value);
	}
	private static Boolean isFilteredCOSEntry(COSName name){
		/**
		 * Indicated if the COS entry described by a given name is filtered out of the results set
		 */
		String s = name.toString();
		if (name.toString().equals("/Font")){
			return true;
		}
		return false;
	}
	
	protected static void printCOSStructure(COSObject root, String printKey,
			int depth, PrintStream file, PrintStream fullTreeFile,
			Map<Integer, Boolean> visited) {

		// determining if COSObject is one of possible obejcts

		visited.put(root.hashCode(), true);

		COSStream cosStream = root.asStream();
		// COSName cosName = root.asName();
		// COSArray cosArray = root.asArray();
		COSDictionary cosDictionary = root.asDictionary();
		// COSBoolean cosBoolean = root.asBoolean();
		// COSFixed cosFixed = root.asFixed();
		// COSInteger cosInteger = root.asInteger();
		// COSNull cosNull = root.asNull();
		// COSNumber cosNumber = root.asNumber();
		// COSString cosString = root.asString();

		if (depth > 140) {
			file.println(" Too deep ! probably some loops");
			return;
		}

		if (cosDictionary != null || cosStream != null) {
			if (cosStream != null) {
				cosDictionary = cosStream.getDict(); // get teh dictionary
														// associated with the
														// stream
				file.println("---------------------------------------------------");
				file.println("stream     | metadata : "
						+ cosStream.getDict().toString());
				file.println("---------------------------------------------------");
				file.println(cosStream.toString());
				printCOSOutputLine(depth, printKey, "stream", fullTreeFile);
			} else {
				printCOSOutputLine(depth, printKey, "dictionary", fullTreeFile);
			}

			@SuppressWarnings("unchecked")
			Set<COSName> keys = cosDictionary.keySet();
			Iterator<COSName> nIter = keys.iterator();
			while (nIter.hasNext()) {
				COSName key = nIter.next();
				if (isFilteredCOSEntry(key)){
					printCOSOutputLine(depth + 1, key.toString(), "entry has been filtered out of the result", fullTreeFile);
				} else if (visited.containsKey(cosDictionary.get(key).hashCode())) {
					printCOSOutputLine(depth + 1, key.toString(), cosDictionary
							.get(key).toString() + " - already visited object",
							fullTreeFile);

				} else {
					COSObject obj = cosDictionary.get(key);
					printCOSStructure(obj, key.toString(), depth + 1, file,
							fullTreeFile, visited);
				}
			}
		} else {
			printCOSOutputLine(depth, printKey, root.toString(), fullTreeFile);
			Iterator<COSDocumentElement> iter = root.basicIterator();
			while (iter.hasNext()) {
				COSObject child = iter.next().dereference();
				printCOSStructure(child, "", depth + 1, file, fullTreeFile,
						visited);
			}
			;
		}
	};

	protected static void printPDStructure(PDObject root, PrintStream output) {

		class PDStructurePrinter implements IPDStructureTraverser {
			private PrintStream output;

			@Override
			public void visitNode(PDObject node, Stack<PDObject> nestingStack) {
				for (int i = 0; i < nestingStack.size(); i++) {
					this.output.print("*");
				}
				this.output.println(node.toString());
			}

			public PDStructurePrinter(PrintStream output) {
				this.output = output;
			}
		}
		;

		PDStructurePrinter nodePrinter = new PDStructurePrinter(output);
		traversePDStructure(root, nodePrinter);
		/**
		 * Printing the internal PD tree of the document (The tree on a more
		 * abstract level)
		 */

	}

	// //// The end of helper debugging methods

	private interface IPDStructureTraverser {
		void visitNode(PDObject node, Stack<PDObject> nestingStack);
	};

	private static void traversePDStructure(PDObject root,
			IPDStructureTraverser traverser) {
		/**
		 * Traversing the PD structure of the document and performing a defined
		 * operation on each node a traditional DFS-like traverse
		 */
		class Pair {
			public PDObject first;
			public PDObject second;

			public Pair(PDObject f, PDObject s) {
				this.first = f;
				this.second = s;
			}
		}
		;

		HashMap<Integer, PDObject> traversedObjects = new HashMap<Integer, PDObject>();

		Stack<Pair> toTraverse = new Stack<Pair>(); // stack of nodes that are
													// waiting to be processed
		Stack<PDObject> parentsStack = new Stack<PDObject>(); // stack of parent
																// nodes of a
																// currently
																// visited node
		toTraverse.push(new Pair(null, root));

		while (!toTraverse.empty()) {
			Pair currentPair = toTraverse.pop();
			PDObject currentNode = currentPair.second;
			PDObject currentParent = currentPair.first;
			if (!traversedObjects.containsKey(currentNode.hashCode())) {
				traversedObjects.put(currentNode.hashCode(), currentNode);
				while (!(parentsStack.empty() && currentParent == null)
						&& !(parentsStack.peek() == currentParent)) {
					parentsStack.pop();
				}

				parentsStack.push(currentNode);

				traverser.visitNode(currentNode, parentsStack);

				List<PDObject> children = getPDNodeChildren(currentNode);

				for (PDObject child : children) {
					toTraverse.push(new Pair(currentNode, child));
				}
			}
		}
	}

	private static List<PDObject> getPDNodeChildren(PDObject currentNode) {
		/**
		 * Retrieves all the children of a given node in the PD structure
		 */
		List<PDObject> results = new ArrayList<PDObject>();

		if (currentNode instanceof PDPageNode) {
			/* We might have some sub-elements as this is a Page node */
			PDPageNode pnode = (PDPageNode) currentNode;
			PDPageNode currentChild = pnode.getFirstNode();

			while (currentChild != null) {
				if (currentChild != pnode) {
					results.add(currentChild);
				}
				currentChild = currentChild.getNextNode();
			}
		}

		if (currentNode instanceof PDPage) {
			/* In this case we want to consider resources */
			PDPage page = (PDPage) currentNode;
			if (page.getResources() == null) {
				// file.println("Empty resources for a page !");
			} else {
				results.add(page.getResources());
			}
		}

		if (currentNode instanceof PDResources) {
			/** the attached resources are also PDObjects */

			PDResources resources = (PDResources) currentNode;
			List<PDObject> children = resources.getGenericChildren();
			System.out.println("Printing the dictionary children");

		}
		return results;
	}

	private static String writeImageToFile(BufferedImage image, String filename)
			throws IOException {
		/**
		 * Save a given BufferedImage instance into a file
		 * 
		 * @param image
		 *            The object describing the image to be saved
		 * @param filename
		 *            Name of the file, where results should be saved. If this
		 *            parameter designs a directory, a new file with the unique
		 *            name is created
		 * @return The name of the file, where image has been saved
		 */
		ImageWriter writer = null;
		Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("png");
		if (iter.hasNext()) {
			writer = iter.next();
		}

		File outputFile = new File(filename);
		if (outputFile.isDirectory()) {
			outputFile = File.createTempFile("plot", ".png", outputFile);
		}

		ImageOutputStream ios = null;

		try {
			ios = ImageIO.createImageOutputStream(outputFile);
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), null);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (ios != null) {
				try {
					ios.flush();
				} catch (IOException e) {
				}
				try {
					ios.close();
				} catch (IOException e) {
				}
			}
			writer.reset();
		}
		return outputFile.getPath();
	}

	private static void findPlotNodes(PDObject rootNode,
			List<PlotDescription> plotDescs) throws IOException {
		/**
		 * Goes through the PD structure of the PDF document spotting relevant
		 * nodes
		 * 
		 * @param obj
		 *            PD structure object to start with
		 * @param plotDescs
		 *            A list of results - to be updated
		 */
		class PlotFinder implements IPDStructureTraverser {
			private List<PlotDescription> foundPlots;

			@Override
			public void visitNode(PDObject node, Stack<PDObject> nestingStack) {
				if (node instanceof PDResources) {
					// we expect to find results only inside resource
					// dictionaries

					PDResources resources = (PDResources) node;
					COSDictionary cosRes = (COSDictionary) resources
							.cosGetObject();
					// cosObject is useful in order to determine names of the
					// external objects

					COSName xobjectsName = COSName.constant("XObject");
					COSObject tmpobj = cosRes.get(xobjectsName);
					if (!tmpobj.isNull()) {
						COSDictionary objects = (COSDictionary) tmpobj;
						@SuppressWarnings("unchecked")
						Set<COSName> keys = objects.keySet();
						String[] names = new String[keys.size()];
						COSName[] cosNames = new COSName[keys.size()];

						int currentInd = 0;
						for (COSName name : keys) {
							names[currentInd] = name.toString();
							cosNames[currentInd] = name;
							currentInd++;
						}

						// now we are ready to retrieve resources associated
						// with given names

						for (COSName name : cosNames) {
							PDXObject xobject = resources
									.getXObjectResource(name);
							if (xobject instanceof PDForm
									|| xobject instanceof PDImage) {
								PlotDescription desc = new PlotDescription();
								desc.setXObject(xobject);
								this.foundPlots.add(desc);
							}
						}

					}

				}
			}

			public PlotFinder(List<PlotDescription> pDesc) {
				this.foundPlots = pDesc;
			}
		};

		PlotFinder finder = new PlotFinder(plotDescs);
		traversePDStructure(rootNode, finder);
	};

	private static BufferedImage renderForm(PDForm form) {
		Rectangle2D rect = form.getBoundingBox().toNormalizedRectangle();
		BufferedImage image = null;
		IGraphicsContext graphics = null;

		try {
			image = new BufferedImage((int) rect.getWidth() * SCALE,
					(int) rect.getHeight() * SCALE, BufferedImage.TYPE_INT_RGB);

			Graphics2D g2 = (Graphics2D) image.getGraphics();
			//ExtractorGraphics2D eg2 = new ExtractorGraphics2D(g2, null);
			graphics = new CwtAwtGraphicsContext(g2);
			// setup user space
			AffineTransform imgTransform = graphics.getTransform();
			imgTransform.scale(SCALE, -SCALE);
			imgTransform.translate(-rect.getMinX(), -rect.getMaxY());
			graphics.setTransform(imgTransform);
			graphics.setBackgroundColor(Color.WHITE);
			graphics.fill(rect);
			CSContent content = form.getContentStream();
			// CSOperation[] operations = content.getOperations();
			if (content != null) {
				CSPlatformRenderer renderer = new CSPlatformRenderer(null,
						graphics);
				renderer.process(content, form.getResources());
			}
			return image;
		} finally {
			if (graphics != null) {
				graphics.dispose();
			}
		}
	}

	private static void renderPlots(List<PlotDescription> plotDescs) {
		/**
		 * Renders all plot descriptors. Note: This operation is performed on a
		 * more global level rather than in the descriptor class because
		 * rendering depends on the global document context ( like the rotation
		 * relative to the original page coordonates).
		 * 
		 * @param plotDescs
		 *            a list of prefilled descriptors. All the descriptors
		 *            having an attached xobjects will be renderes and the
		 *            resulting image will be included in the plot descriptor.
		 */

		for (PlotDescription desc : plotDescs) {
			BufferedImage image = renderForm((PDForm) desc.getXObject());
			desc.setImage(image);
		}
	}

	public static List<PlotDescription> extractPlots(PDDocument doc)
			throws IOException {
		/**
		 * Function extracting all the plots from a given PDF document
		 * 
		 * @param doc
		 *            an instance of the PDDocument class, describing the source
		 *            to extract from
		 * @return List of plot descriptors
		 */
		List<PlotDescription> descriptions = new LinkedList<PlotDescription>();
		PDPageTree pages = doc.getPageTree();
		findPlotNodes(pages, descriptions);
		return descriptions;
	}

	public static void savePlots(List<PlotDescription> descriptions,
			String directoryName) throws IOException {
		/**
		 * Saves the plot descriptions into a given directory
		 * 
		 * @param descriptions
		 *            A list of plot descriptions. Might be a result of the
		 *            extraction
		 * @param directory
		 *            Directory where the results should be stored
		 */
		
		System.out.println("Saving plots from a document");
		File resultsDir = new File(directoryName);
		if (!resultsDir.exists()) {
			resultsDir.mkdir();
		}

		for (PlotDescription desc : descriptions) {
			writeImageToFile(desc.getImage(), directoryName);
		}
	}

	public static void saveMetadata(List<PlotDescription> descriptions,
			String metadataFile) {
		/**
		 * Saves the meta-data about extracted plots into a file
		 * 
		 * @param descriptions
		 *            Descriptions of plots detected in the PDF document
		 * @param metadataFile
		 *            The name of the file where results should be saved
		 */

	}

	/** debug code -> rendering the entire page */
	
	public static BufferedImage renderPage(PDPage page) {
		Rectangle2D rect = page.getCropBox().toNormalizedRectangle();
		BufferedImage image = null;
		IGraphicsContext graphics = null;
		try {
			
			image = new BufferedImage((int) rect.getWidth() * SCALE, (int) rect
					.getHeight()
					* SCALE, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = (Graphics2D) image.getGraphics();
			
			// Our wrapper around the 2D device allowing to extract informations about the device
			ExtractorOperationsManager opManager = new ExtractorOperationsManager();
			
			ExtractorGraphics2D g2proxy = new ExtractorGraphics2D(g2, opManager);
			
			// now we use our wrapper in order to construct standard mechanisms
			graphics = new ExtractorGraphicsContext(g2proxy);
			
			// setup user space
			AffineTransform imgTransform = graphics.getTransform();
			imgTransform.scale(SCALE, -SCALE);
			imgTransform.translate(-rect.getMinX(), -rect.getMaxY());
			graphics.setTransform(imgTransform);
			graphics.setBackgroundColor(Color.WHITE);
			graphics.fill(rect);
			CSContent content = page.getContentStream();
			CSOperation[] operations = content.getOperations();
			
			if (content != null) {
				
				//CSPlatformRenderer renderer = new CSPlatformRenderer(null,
				//		graphics);
				
				// we inject our own implementation of the renderer -> before performing an operation, it is 
				// marked as currently performed. In such a way, out graphics device will be able to detect the operation
				// and assign its attributes
				
				ExtractorCSInterpreter renderer = new ExtractorCSInterpreter(null, 
						graphics, opManager);
				
				renderer.process(content, page.getResources());
			}
			return image;
		} finally {
			if (graphics != null) {
				graphics.dispose();
			}
		}
	}
	
	public static void renderDocumentPages(PDDocument doc, String filename){
		ImageWriter writer = null;
		Iterator iter = ImageIO.getImageWritersByFormatName("png");
		if (iter.hasNext()) {
			writer = (ImageWriter) iter.next();
		}
		//
		PDPageTree pages = doc.getPageTree();		
		
		FileOutputStream outputStream = null;
		FileOutputStream outputStreamTree = null;
		FileOutputStream outputPdStreamTree = null;
		
		PrintStream file;
		PrintStream fullTreeFile;
		PrintStream fullPdTreeFile;
		
		try {
			outputStream = new FileOutputStream("c:\\pdf\\analysis_output.txt");
			outputStreamTree = new FileOutputStream("c:\\pdf\\analysis_output_tree.txt");
			outputPdStreamTree = new FileOutputStream("c:\\pdf\\analysis_output_pd_tree.txt");
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		file = new PrintStream(outputStream);
		fullTreeFile = new PrintStream(outputStreamTree);
		fullPdTreeFile = new PrintStream(outputPdStreamTree);
		
		file.close();
		fullTreeFile.close();
		fullPdTreeFile.close();
		PDPage page = pages.getFirstPage();
		//PDPage page = pages.getPageAt(6);
		//iterateNodes(page.getFirstNode(), 1);
		int i = 0;
		while (page != null) {
			BufferedImage image = renderPage(page);
			FileOutputStream os = null;
			File outputFile = new File(filename + "." + i + ".png");
			ImageOutputStream ios = null;
			try {
				ios = ImageIO.createImageOutputStream(outputFile);
				writer.setOutput(ios);
				writer.write(null, new IIOImage(image, null, null), null);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (ios != null) {
					try {
						ios.flush();
					} catch (IOException e) {
					}
					try {
						ios.close();
					} catch (IOException e) {
					}
				}
				writer.reset();
			}
			System.out.print(".");
			i++;
			if (i % 10 == 0) {
				System.out.println();
			}
			page = page.getNextPage();
		}
		//
		writer.dispose();
	};
	
	public static void processDocument(String fileName, String outputDirectory)
			throws IOException, COSLoadException {
		/**
		 * Perform a complete processing for one document
		 * 
		 * @param fileName
		 *            The name of the pdf file to extract plots from
		 * @param outputDirectory
		 *            The name of the directory, where the results should be
		 *            saved
		 */
		FileLocator locator = new FileLocator(fileName);
		PDDocument doc = PDDocument.createFromLocator(locator);

		File cosLogFile = new File(fileName + ".cos");
		PrintStream cosStream = new PrintStream(cosLogFile);

		File logFile = new File(fileName + ".log");
		PrintStream logStream = new PrintStream(logFile);

		// first we analyse the COS structure of the document
		printCOSStructure(doc.getCatalog().cosGetObject(), "Catalog", 1,
				logStream, cosStream, new HashMap<Integer, Boolean>());

		File pdLogFile = new File(fileName + ".pd");
		PrintStream pdStream = new PrintStream(pdLogFile);

		printPDStructure(doc.getPageTree(), pdStream);
		pdStream.close();
		logStream.close();
		cosStream.close();

		List<PlotDescription> plots = extractPlots(doc);
		renderPlots(plots);
		savePlots(plots, outputDirectory);
		
		renderDocumentPages(doc, fileName);

	}

	public static void main(String[] args) throws IOException, COSLoadException {
		String inputFileName = args[0];
		File input = new File(inputFileName);
		if (input.isDirectory()) {
			File[] files = input.listFiles();
			for (File file : files) {
				if (file.getPath().toLowerCase().endsWith(".pdf")) {
					processDocument(file.getPath(), file.getPath()
							+ ".extracted");
				}
			}
		} else {
			processDocument(inputFileName, inputFileName + ".extracted");
		}
	}

}