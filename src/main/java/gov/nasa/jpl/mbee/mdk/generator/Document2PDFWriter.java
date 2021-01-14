package gov.nasa.jpl.mbee.mdk.generator;

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
 * MD Model -> Document -> PDF
 * @author dlam
 */
public class Document2PDFWriter implements RunnableWithProgress {

	private Document dge;
	private boolean genNewImage;
    private File docbook;
    private File docbookXslFo;
    private File outputFile;
    private static FopFactory fopFactory = FopFactory.newInstance();

    public Document2PDFWriter(Document dge, boolean genNewImage, File docbook, File docbookXslFo, File outputFile) {
    	this.dge = dge;
    	this.genNewImage = genNewImage;
    	this.docbook = docbook;
    	this.docbookXslFo = docbookXslFo;
    	this.outputFile = outputFile;
    }
    private void mdModel2Document(ProgressStatus arg0) throws IOException {

    	//MD Model -> Document
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(docbook));
            
            //SessionManager.getInstance().createSession(Application.getInstance().getProject(), DocBookOutputVisitor.class.getSimpleName());
            DocBookOutputVisitor visitor = new DocBookOutputVisitor(false, docbook.getParentFile().getAbsolutePath());
            dge.accept(visitor);
            //SessionManager.getInstance().closeSession(Application.getInstance().getProject());
            DBBook book = visitor.getBook();
            if (book != null) {
                // List<DocumentElement> books = dge.getDocumentElement();
                DBSerializeVisitor v = new DBSerializeVisitor(genNewImage, docbook.getParentFile(), arg0);
                book.accept(v);
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
                // writer.write("<!DOCTYPE book [\n<!ENTITY % sgml.features \"IGNORE\">\n<!ENTITY % xml.features \"INCLUDE\">\n<!ENTITY % dbcent PUBLIC \"-//OASIS//ENTITIES DocBook Character Entities\nV4.4//EN\" \"dbcentx.mod\">\n%dbcent;\n]>");
                writer.write(v.getOut());
            }
            writer.flush();
            writer.close();
            Utils.guilog("Docbook is created as " + docbook.getAbsolutePath()); 
        } catch (IOException ex) {
           //Utils.printException(ex);
           throw ex;
        }

    }

    private void document2Pdf() throws Exception {
    
    	//Document -> PDF
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
	            
	            Utils.log("PDF is created as " + outputFile.getAbsolutePath());
	        }
	        catch (Exception ex) {
	        	throw ex;
	            
	        } finally {
	            pdfOut.close();
	            
	        } 
		} catch (Exception e) {
			throw e;
		}
    }
    @Override
    public void run(ProgressStatus arg0) {
    	arg0.setIndeterminate(true);
    	arg0.setDescription("Generating docbook...");
    	try {
			mdModel2Document(arg0);
		} catch (IOException e1) {
			Utils.log("[ERROR] An error creating the docbooke file " + e1.getMessage());
		}
    	arg0.setDescription("Converting to pdf...");
    	try {
			document2Pdf();
		} catch (Exception e) {
			Utils.log("[ERROR] An error creating the pdf file " + e.getMessage());
		}
    }

}
