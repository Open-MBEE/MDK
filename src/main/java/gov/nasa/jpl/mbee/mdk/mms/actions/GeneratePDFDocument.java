package gov.nasa.jpl.mbee.mdk.mms.actions;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.plugins.PluginDescriptor;
import com.nomagic.magicdraw.plugins.PluginUtils;
import com.nomagic.ui.ProgressStatusRunner;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.docgen.ViewViewpointValidator;
import gov.nasa.jpl.mbee.mdk.generator.Document2PDFWriter;
import gov.nasa.jpl.mbee.mdk.generator.DocumentGenerator;
import gov.nasa.jpl.mbee.mdk.generator.DocumentWriter;
import gov.nasa.jpl.mbee.mdk.generator.DocumentWriter2PDF;
import gov.nasa.jpl.mbee.mdk.generator.PostProcessor;
import gov.nasa.jpl.mbee.mdk.model.Document;
import gov.nasa.jpl.mbee.mdk.util.Utils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.apache.fop.apps.FOPException;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Action to Generate DocBook from MD Model, then Convert to PDF
 * 
 * default xsl is stored in plugin directory
 */
public class GeneratePDFDocument extends MDAction {

	protected Element view;
    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_ID = GeneratePDFDocument.class.getSimpleName();
    //if "Document Modeling" plugin (no cost) is installed
    protected static String xslDefaultPathInMDKPlugin = "com.nomagic.magicdraw.modelbasedreport" + File.separator + "xsl" + File.separator + "docbook-xsl-1.78.1" + File.separator + "fo" + File.separator + "docbook.xsl";
    protected static File xslDefaultFile = null;
    protected static File pdfDefaultDir = null;
    Document doc;
    boolean genNewImage;
    
    
    public GeneratePDFDocument(String id, String name, Element view)
    {
    	super(id, name, null, null);
        this.view = view;
    }

    public GeneratePDFDocument(Element view) {
        this(DEFAULT_ID, "PDF", view);
        if (xslDefaultFile == null) {
	        //Get our plugin directory
	        Optional<PluginDescriptor> pd = PluginUtils.getPlugins().stream().map(Plugin::getDescriptor)
	        		.filter(descriptor -> descriptor.getName().equals("Model Development Kit"))
	        		.findFirst();
	        if ( pd.isPresent() ) {	
	           // the XSL FO file
	        	xslDefaultFile = new File(pd.get().getPluginDirectory().getParent() , xslDefaultPathInMDKPlugin);
	        }
        }
    }
    protected File fileSelect(String title, File defaultFile, String approveButtonText, FileNameExtensionFilter filter) {
        JFileChooser choose = new JFileChooser();
        if (defaultFile != null)
        	if ( defaultFile.isDirectory())
        		choose.setCurrentDirectory(defaultFile);
        	else
        		choose.setSelectedFile(defaultFile);
        
        choose.setDialogTitle(title);
        
        if ( filter != null)
        	choose.addChoosableFileFilter(filter);
        int retval = choose.showDialog(null, approveButtonText);
        if (retval == JFileChooser.APPROVE_OPTION) {
            return choose.getSelectedFile();
        }
        return null;
    }
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
        	
        	 if( xslDefaultFile.exists() == false) {
        		Utils.showPopupMessage("\"Document Modeling Plugin\" is required.  Please install it and try again.");
 	        	return;
        	 }
        	//1. pick stylesheet (xsl)
        	xslDefaultFile = fileSelect("Select a style sheet(xsl) ...", xslDefaultFile, "Select", new FileNameExtensionFilter("Style sheet", "xsl"));
        	if ( xslDefaultFile == null) return;
        	while ( !xslDefaultFile.getName().endsWith(".xsl")) {
        		Utils.log("[ERROR] Pleas pick a .xsl file.");
        		xslDefaultFile = fileSelect("Select a style sheet(xsl) ...", xslDefaultFile, "Select", new FileNameExtensionFilter("Style sheet", "xsl"));
        	}
        	//2. pick outputfil (pdf) file
        	File pdfFile = fileSelect("Select an output pdf to be saved ...", pdfDefaultDir, "Save", new FileNameExtensionFilter("PDF", "pdf"));
        	if ( pdfFile == null) return;
        	pdfDefaultDir = pdfFile.getParentFile();
        	//if a output file is not *.pdf then add *.pdf.
        	if ( !pdfFile.getName().endsWith(".pdf"))
        		pdfFile = new File(pdfFile + ".pdf");
        	//assign docgen3 file name based on the pdf file
        	File docbookfile = new File(pdfFile + ".xml");
        	generateDocBookThenPDF(docbookfile, xslDefaultFile, pdfFile);
        	
        } catch (Exception ex) {
            Utils.printException(ex);
        }
    }
    private void prepToDocBook()
    {
    	Project project = Application.getInstance().getProject();
        ViewViewpointValidator dv = new ViewViewpointValidator(Collections.singleton(view), project, true);
        dv.run();
        if (dv.isFailed()) {
            Utils.log("[ERROR] View validation failed for " + Converters.getElementToHumanNameConverter().apply(view) + ". Aborting generation.");
            Utils.displayValidationWindow(project, dv.getValidationSuite(), dv.getValidationSuite().getName());
            return;
        }
        DocumentGenerator dg = new DocumentGenerator(view, dv, null);
        doc = dg.parseDocument();
        boolean genNewImage = doc.getGenNewImage();
        (new PostProcessor()).process(doc);
    }
    private void generateDocBookThenPDF(File docbook, File docbookXslFo, File outputFile) {
    	prepToDocBook();
    	ProgressStatusRunner.runWithProgressStatus(new Document2PDFWriter(doc, genNewImage, docbook, docbookXslFo, outputFile), "Generating DocGen3 then to PDF...", true, 0);
    }
    protected void generateDocBook(File savefile) {
    	prepToDocBook();
        ProgressStatusRunner.runWithProgressStatus(new DocumentWriter(doc, savefile, genNewImage), "Generating DocGen 3 Document...", true, 0);
    }

    protected void generatePDF(File docbook, File docbookXslFo, File outputFile) throws FOPException, TransformerFactoryConfigurationError, TransformerException, IOException {
    	ProgressStatusRunner.runWithProgressStatus(new DocumentWriter2PDF(docbook, docbookXslFo, outputFile), "Converting to PDF ...", true, 0);
    }
    
}
