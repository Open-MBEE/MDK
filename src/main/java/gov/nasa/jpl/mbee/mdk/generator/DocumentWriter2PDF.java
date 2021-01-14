package gov.nasa.jpl.mbee.mdk.generator;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.GUILog;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBBook;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBSerializeVisitor;
import gov.nasa.jpl.mbee.mdk.model.DocBookOutputVisitor;
import gov.nasa.jpl.mbee.mdk.model.Document;
import gov.nasa.jpl.mbee.mdk.util.Utils;

import java.io.*;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;

/**
 * runs the generation as a runnable to not stall magicdraw main thread, also
 * allow user to cancel
 * 
 * Document -> PDF
 *
 * @author dlam
 */
public class DocumentWriter2PDF implements RunnableWithProgress {

    private File docbook;
    private File docbookXslFo;
    private File outputFile;
    private static FopFactory fopFactory = FopFactory.newInstance();

    public DocumentWriter2PDF(/*Document dge, File realfile, boolean genNewImage, File dir*/ File docbook, File docbookXslFo, File outputFile) {
    	this.docbook = docbook;
    	this.docbookXslFo = docbookXslFo;
    	this.outputFile = outputFile;
    }

    @Override
    public void run(ProgressStatus arg0) {
        GUILog gl = Application.getInstance().getGUILog();
        arg0.setIndeterminate(true);
        StreamSource docbookSrc = new StreamSource(docbook);

        // create a user agent (used to tweak rendering settings on a per-run basis.
        // we are just using defaults for now though.
        FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
        
        OutputStream pdfOut;
		try {
			pdfOut = new java.io.FileOutputStream(new File(outputFile.getPath()));
		
	        try {
	        	            // Construct a FOP that makes PDFs (other options are postscript, rtf, etc.)
	            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, pdfOut);
	
				// Setup XSLT
				Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(docbookXslFo));
	
	            // Pipe the FO events to FOP
	            Result foResults = new SAXResult(fop.getDefaultHandler());
	
	            //transform Docbook -> XSL-FO -> PDF
	            transformer.transform(docbookSrc, foResults);
	            pdfOut.close();
	            gl.log("Generation Finished");
	        }
	        catch (Exception ex) {
	        	Utils.printException(ex);
	            
	        } finally {
	            pdfOut.close();
	            
	        } 
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

}
