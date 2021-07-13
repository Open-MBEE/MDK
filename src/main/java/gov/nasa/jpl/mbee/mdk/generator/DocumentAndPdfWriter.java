package gov.nasa.jpl.mbee.mdk.generator;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;
import gov.nasa.jpl.mbee.mdk.MDKPlugin;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBBook;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBSerializeVisitor;
import gov.nasa.jpl.mbee.mdk.model.DocBookOutputVisitor;
import gov.nasa.jpl.mbee.mdk.model.Document;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.fop.apps.*;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;


/**
 * runs the generation as a runnable to not stall magicdraw main thread, also
 * allow user to cancel
 * <p>
 * MD Model -> Document(xml) -> PDF
 *
 * @author DavidWillard, Miyako Wilson
 */
public class DocumentAndPdfWriter implements RunnableWithProgress {

	private final Document dge;
	private final boolean genNewImage;
	private final File docbookXslFo;
	private final File outputFile;
	private File docbook;

	public DocumentAndPdfWriter(File docbook, File docbookXslFo, File outputFile) {
		this(null, false, docbook, docbookXslFo, outputFile);
	}

	

	public DocumentAndPdfWriter(Document dge, boolean genNewImage, File docbook, File docbookXslFo, File outputFile) {
		this.dge = dge;
		this.genNewImage = genNewImage;
		this.docbook = docbook;
		this.docbookXslFo = docbookXslFo;
		this.outputFile = outputFile;
	}

	//create xml in temp directory and remove when the application ended.
	//no message to Notification window
	//used by "DocGen->Generate->PDF" action
	protected boolean mdModel2DocumentAsTemp(ProgressStatus status) {
		try {
			docbook = File.createTempFile(docbook.getName(), ".xml");
			return DocumentWriter.mdModel2Document(status, dge, genNewImage, docbook, false);
			
		} catch (IOException e) {
			return false;
		}
	}
    //DocGen -> Generate-> PDF
    protected void document2Pdf()  {
    	
    	ClassLoader localClassLoader = Thread.currentThread().getContextClassLoader();
    	try {
    		FopFactory fopFactory = FopFactory.newInstance();//fopConfigFile);
    	
    		//make currentloader to be mainloader (batik, fop, xmlgraphics jar need to be loaded at the same level so images to be embedded in PDF).
    		URL[] urls = new URL[1];
    		//urls[0] = (new File(ApplicationEnvironment.getInstallRoot() + File.separator + "plugins" + File.separator + "gov.nasa.jpl.mbee.mdk")).toURI().toURL();
			urls[0] = MDKPlugin.getInstance().getDescriptor().getPluginDirectory().toURI().toURL();
    		URLClassLoader mainClassLoader = new URLClassLoader(urls, Application.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(mainClassLoader);
    		
        	StreamSource docbookSrc = new StreamSource(docbook);
	
	        // create a user agent (used to tweak rendering settings on a per-run basis.
	        // we are just using defaults for now though.
	        FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
	        
	        OutputStream pdfOut;
			try {
				pdfOut = new java.io.FileOutputStream(outputFile);
				try {
					// Construct a FOP that makes PDFs (other options are postscript, rtf, etc.)
					Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, pdfOut);
					// Setup XSLT
					Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(docbookXslFo));
					// Pipe the FO events to FOP
					Result res = new SAXResult(fop.getDefaultHandler());
					//transform Docbook -> XSL-FO -> PDF
					transformer.transform(docbookSrc, res);


					FormattingResults foResults = fop.getResults();
					// Result processing
					@SuppressWarnings("unchecked")
					List<PageSequenceResults> pageSequences = foResults.getPageSequences();
					for (PageSequenceResults pageSequenceResults : pageSequences) {
						Application.getInstance().getGUILog().log("PageSequence "
								+ (String.valueOf(pageSequenceResults.getID()).length() > 0
								? pageSequenceResults.getID() : "<no id>")
								+ " generated " + pageSequenceResults.getPageCount() + " pages.");
					}
					Application.getInstance().getGUILog().log("Generated " + foResults.getPageCount() + " pages in total.");
					Application.getInstance().getGUILog().log("A PDF file is created as " + outputFile.getAbsolutePath());
				} catch (FOPException | TransformerException e) {
					e.printStackTrace();
					Application.getInstance().getGUILog().log("[ERROR] Failed to generate a PDF file. " + ExceptionUtils.getStackTrace(e));
				} finally {
					pdfOut.close();
					//Delete file on exit for docbook xml file
					docbook.deleteOnExit();
				}
			} catch (Exception e) { //FileNotFoundException and IOException for pdfOut.close()
				e.printStackTrace();
				Application.getInstance().getGUILog().log("[ERROR] Failed to generate a PDF file. " + ExceptionUtils.getStackTrace(e));
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
			Application.getInstance().getGUILog().log("[ERROR] Failed to generate a PDF file." + ExceptionUtils.getStackTrace(e));
		} finally {
			Thread.currentThread().setContextClassLoader(localClassLoader);
		}
	}

	@Override
	public void run(ProgressStatus status) {
		status.setIndeterminate(true);
		status.setDescription("Generating a PDF file...");
		if (mdModel2DocumentAsTemp(status)) 
			document2Pdf();
		else 
			Application.getInstance().getGUILog().log("[ERROR] Failed to generate a PDF file.");
	}
}
