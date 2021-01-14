package gov.nasa.jpl.mbee.mdk.mms.actions;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import gov.nasa.jpl.mbee.mdk.util.Utils;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.filechooser.FileNameExtensionFilter;


/**
 * Action to PDF from DocBook
 * 
 */

public class GeneratePDFFromDocBook extends GeneratePDFDocument {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_ID = GeneratePDFFromDocBook.class.getSimpleName();
    private File docBookDefaultDir = null;
    
    public GeneratePDFFromDocBook(Element view) {
        super(DEFAULT_ID, "DocBook -> PDF", view);
    }
    @Override
    public void actionPerformed(ActionEvent e) {
    	 try {
    		 
			if( xslDefaultFile.exists() == false) {
				Utils.showPopupMessage("\"Document Modeling Plugin\" is required.  Please install it and try again.");
				return;
			}
    		 
			File docbookFile = fileSelect("Select a docbook(xml) ...", docBookDefaultDir, "Select", new FileNameExtensionFilter("XML", "xml"));
			if ( docbookFile == null) return; //cancelled
			docBookDefaultDir = docbookFile.getParentFile();
			xslDefaultFile = fileSelect("Select a style sheet(xsl) ...", xslDefaultFile, "Select", new FileNameExtensionFilter("XSL", "xsl"));
			File outputPdfFile = fileSelect("Select an output pdf to be saved ...", pdfDefaultDir, "Save", new FileNameExtensionFilter("PDF", "pdf"));
			if (outputPdfFile == null) return; //cancelled
			pdfDefaultDir = outputPdfFile.getParentFile();
			
			//not end with .pdf and then append .pdf
			if ( !outputPdfFile.getName().endsWith(".pdf"))
				outputPdfFile = new File(outputPdfFile + ".pdf");
			
			super.generatePDF(docbookFile, xslDefaultFile, outputPdfFile);
     
         } catch (Exception ex) {
             Utils.printException(ex);
         }
    }

   
   
}
