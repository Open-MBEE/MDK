package gov.nasa.jpl.mbee.mdk.mms.actions;

import com.nomagic.ui.ProgressStatusRunner;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import gov.nasa.jpl.mbee.mdk.generator.DocumentWriter;
import gov.nasa.jpl.mbee.mdk.model.Document;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Action to generate a DocBook xml file from MDK Document Model.
 *
 * @author dlam, mwilson
 */
public class GenerateDocBookAction extends GeneratePdfFromDocBookAction {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_ID = GenerateDocBookAction.class.getSimpleName();

    public GenerateDocBookAction(Element view) {
        super(DEFAULT_ID, "DocBook", view);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File docbookFile = askForDocBookFile("Specify a DocBook xml file to save...", "Save");
        if (docbookFile != null) { //cancelled
            generate(docbookFile);
        }
    }

    //MDK Document model -> Docbook XML File
    protected void generate(File outputDocbookFile) {
        Document doc = prepToDocBook();
        if (doc != null) {
            ProgressStatusRunner.runWithProgressStatus(new DocumentWriter(doc, outputDocbookFile, doc.getGenNewImage()), "DocGen", true, 0);
        }
    }
 
}

