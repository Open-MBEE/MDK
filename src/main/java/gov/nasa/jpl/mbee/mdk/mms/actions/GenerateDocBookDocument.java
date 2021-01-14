package gov.nasa.jpl.mbee.mdk.mms.actions;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import gov.nasa.jpl.mbee.mdk.util.Utils;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.filechooser.FileNameExtensionFilter;


/**
 * Action to Generate DocBook from MD Model
 * 
 */

public class GenerateDocBookDocument extends GeneratePDFDocument {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_ID = GenerateDocBookDocument.class.getSimpleName();
    private File docBookDefaultDir = null;
    
    public GenerateDocBookDocument(Element view) {
        super(DEFAULT_ID, "DocBook", view);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            File savefile = fileSelect("Select a docbook(xml) to be saved ...", docBookDefaultDir, "Save", new FileNameExtensionFilter("XML", "xml"));
            if (savefile == null)	return; //cancelled
            docBookDefaultDir = savefile.getParentFile();
            //rename to .xml if not
        	if ( !savefile.getName().endsWith(".xml")) 
            	savefile = new File( savefile + ".xml");
            generateDocBook(savefile);
        
        } catch (Exception ex) {
            Utils.printException(ex);
        }
    }
 
}
