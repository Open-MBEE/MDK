package gov.nasa.jpl.mbee.mdk.generator;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.task.ProgressStatus;
import com.nomagic.task.RunnableWithProgress;

import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBBook;
import gov.nasa.jpl.mbee.mdk.docgen.docbook.DBSerializeVisitor;
import gov.nasa.jpl.mbee.mdk.model.DocBookOutputVisitor;
import gov.nasa.jpl.mbee.mdk.model.Document;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.lang.exception.ExceptionUtils;


/**
 * runs the generation as a runnable to not stall magicdraw main thread, also
 * allow user to cancel
 * <p>
 * MD Model -> Document
 *
 * @author dlam, mwilson
 */
public class DocumentWriter implements RunnableWithProgress {

	private final Document dge;
	private final boolean genNewImage;
	private File docbook;
	
    public DocumentWriter(Document dge, File outputfile, boolean genNewImage) {
        this.dge = dge;
        this.genNewImage = genNewImage;
        this.docbook = outputfile;
    }

    @Override
    public void run(ProgressStatus status) {
        status.setIndeterminate(true);
        status.setDescription("Generating a DocBook XML file...");
        mdModel2Document(status,this.dge, this.genNewImage, this.docbook, true);
    }
    public static boolean mdModel2Document(ProgressStatus status, Document dge, boolean genNewImage, File docbook, boolean messageout){

		//MD Model -> Document
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(docbook));

			SessionManager.getInstance().createSession(Application.getInstance().getProject(), DocBookOutputVisitor.class.getSimpleName());
			DocBookOutputVisitor visitor = new DocBookOutputVisitor(false, docbook.getParentFile().getAbsolutePath());
			dge.accept(visitor);
			SessionManager.getInstance().closeSession(Application.getInstance().getProject());
			DBBook book = visitor.getBook();
			if (book != null) {
				DBSerializeVisitor v = new DBSerializeVisitor(genNewImage, docbook.getParentFile(), status);
				book.accept(v);
				writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
				writer.write(v.getOut());
			}
			writer.flush();
			writer.close();
			if (messageout)
				Application.getInstance().getGUILog().log("A DocBook xml file is created as " + docbook.getAbsolutePath());
			return true;
		} catch (IOException e) {
			if (messageout)
				Application.getInstance().getGUILog().log("[ERROR] Failed to generate a DocBook XML file. " + ExceptionUtils.getStackTrace(e));
			return false;
		}

	}
}
